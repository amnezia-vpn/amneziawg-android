/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.activity.TunnelCreatorActivity
import org.amnezia.awg.databinding.ObservableKeyedRecyclerViewAdapter.RowConfigurationHandler
import org.amnezia.awg.databinding.ObservableSortedKeyedArrayList
import org.amnezia.awg.databinding.TunnelListFragmentBinding
import org.amnezia.awg.databinding.TunnelListItemBinding
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.model.Subscription
import org.amnezia.awg.model.TunnelComparator
import org.amnezia.awg.subscription.SubscriptionStore
import org.amnezia.awg.subscription.SubscriptionSyncManager
import org.amnezia.awg.subscription.toMessage
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.util.QrCodeFromFileScanner
import org.amnezia.awg.util.TunnelImporter
import org.amnezia.awg.widget.MultiselectableRelativeLayout
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A grouping of tunnels shown in the list.
 *
 * [Default] holds every tunnel the user created or imported themselves — those not claimed by any
 * subscription. [SubscriptionSpace] holds the tunnels one subscription owns. [All] is the union.
 */
sealed interface SpaceFilter {
    data object All : SpaceFilter
    data object Default : SpaceFilter
    data class SubscriptionSpace(val id: String, val name: String) : SpaceFilter

    fun owns(tunnelName: String, subscriptions: List<Subscription>): Boolean = when (this) {
        All -> true
        Default -> subscriptions.none { tunnelName in it.managedTunnels }
        is SubscriptionSpace -> tunnelName in (subscriptions.firstOrNull { it.id == id }?.managedTunnels ?: emptySet())
    }
}

/**
 * Fragment containing a list of known AmneziaWG tunnels. It allows creating and deleting tunnels.
 */
class TunnelListFragment : BaseFragment() {
    private val actionModeListener = ActionModeListener()
    private var actionMode: ActionMode? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private var binding: TunnelListFragmentBinding? = null

    /** The tunnels actually shown, filtered down to the selected space. */
    private val visibleTunnels = ObservableSortedKeyedArrayList<String, ObservableTunnel>(TunnelComparator)
    private var subscriptions: List<Subscription> = emptyList()
    private var selectedSpace: SpaceFilter = SpaceFilter.All

    /** Guards against the programmatic rebuild of the tab strip re-entering the selection listener. */
    private var updatingTabs = false

    private val tabListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            if (updatingTabs) return
            val space = tab.tag as? SpaceFilter ?: return
            if (space == selectedSpace) return
            selectedSpace = space
            refreshVisibleTunnels()
        }

        override fun onTabUnselected(tab: TabLayout.Tab) = Unit
        override fun onTabReselected(tab: TabLayout.Tab) = Unit
    }

    private val tunnelFileImportResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { data ->
        if (data == null) return@registerForActivityResult
        val activity = activity ?: return@registerForActivityResult
        val contentResolver = activity.contentResolver ?: return@registerForActivityResult
        activity.lifecycleScope.launch {
            if (QrCodeFromFileScanner.validContentType(contentResolver, data)) {
                try {
                    val qrCodeFromFileScanner = QrCodeFromFileScanner(contentResolver, QRCodeReader())
                    val result = qrCodeFromFileScanner.scan(data)
                    TunnelImporter.importTunnel(parentFragmentManager, result.text) { showSnackbar(it) }
                } catch (e: Exception) {
                    val error = ErrorMessages[e]
                    val message = Application.get().resources.getString(R.string.import_error, error)
                    Log.e(TAG, message, e)
                    showSnackbar(message)
                }
            } else {
                TunnelImporter.importTunnel(contentResolver, data) { showSnackbar(it) }
            }
        }
    }

    private val qrImportResultLauncher = registerForActivityResult(ScanContract()) { result ->
        val qrCode = result.contents
        val activity = activity
        if (qrCode != null && activity != null) {
            activity.lifecycleScope.launch { TunnelImporter.importTunnel(parentFragmentManager, qrCode) { showSnackbar(it) } }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            val checkedItems = savedInstanceState.getIntegerArrayList(CHECKED_ITEMS)
            if (checkedItems != null) {
                for (i in checkedItems) actionModeListener.setItemChecked(i, true)
            }
        }
        // The space strip is derived from the subscription list, so it has to follow it.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SubscriptionStore.subscriptions.collectLatest { updated ->
                    subscriptions = updated
                    rebuildSpaceTabs()
                    refreshVisibleTunnels()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = TunnelListFragmentBinding.inflate(inflater, container, false)
        val bottomSheet = AddTunnelsSheet()
        binding?.apply {
            createFab.setOnClickListener {
                if (childFragmentManager.findFragmentByTag("BOTTOM_SHEET") != null)
                    return@setOnClickListener
                childFragmentManager.setFragmentResultListener(AddTunnelsSheet.REQUEST_KEY_NEW_TUNNEL, viewLifecycleOwner) { _, bundle ->
                    when (bundle.getString(AddTunnelsSheet.REQUEST_METHOD)) {
                        AddTunnelsSheet.REQUEST_CREATE -> {
                            startActivity(Intent(requireActivity(), TunnelCreatorActivity::class.java))
                        }

                        AddTunnelsSheet.REQUEST_IMPORT -> {
                            tunnelFileImportResultLauncher.launch("*/*")
                        }

                        AddTunnelsSheet.REQUEST_SCAN -> {
                            qrImportResultLauncher.launch(
                                ScanOptions()
                                    .setOrientationLocked(false)
                                    .setBeepEnabled(false)
                                    .setPrompt(getString(R.string.qr_code_hint))
                            )
                        }

                        AddTunnelsSheet.REQUEST_SUBSCRIPTIONS -> {
                            SubscriptionDialogFragment().show(childFragmentManager, SubscriptionDialogFragment.TAG)
                        }
                    }
                }
                bottomSheet.showNow(childFragmentManager, "BOTTOM_SHEET")
            }
            swipeRefresh.setOnRefreshListener { syncNow() }
            // No explicit colour scheme: SwipeRefreshLayout picks up the themed colorPrimary, which
            // keeps the spinner correct across the light and dark Material 3 palettes.
            spaceTabs.addOnTabSelectedListener(tabListener)
            executePendingBindings()
        }
        backPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) { actionMode?.finish() }
        backPressedCallback?.isEnabled = false

        return binding?.root
    }

    override fun onDestroyView() {
        binding?.spaceTabs?.removeOnTabSelectedListener(tabListener)
        binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntegerArrayList(CHECKED_ITEMS, actionModeListener.getCheckedItems())
    }

    override fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?) {
        binding ?: return
        if (newTunnel != null) viewForTunnel(newTunnel)?.setSingleSelected(true)
        if (oldTunnel != null) viewForTunnel(oldTunnel)?.setSingleSelected(false)
    }

    private fun onTunnelDeletionFinished(count: Int, throwable: Throwable?) {
        val message: String
        val ctx = activity ?: Application.get()
        if (throwable == null) {
            message = ctx.resources.getQuantityString(R.plurals.delete_success, count, count)
        } else {
            val error = ErrorMessages[throwable]
            message = ctx.resources.getQuantityString(R.plurals.delete_error, count, count, error)
            Log.e(TAG, message, throwable)
        }
        showSnackbar(message)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        binding ?: return
        binding!!.fragment = this
        lifecycleScope.launch {
            binding!!.tunnels = visibleTunnels
            refreshVisibleTunnels()
        }
        binding!!.rowConfigurationHandler = object : RowConfigurationHandler<TunnelListItemBinding, ObservableTunnel> {
            override fun onConfigureRow(binding: TunnelListItemBinding, item: ObservableTunnel, position: Int) {
                binding.fragment = this@TunnelListFragment
                binding.root.setOnClickListener {
                    if (actionMode == null) {
                        selectedTunnel = item
                    } else {
                        actionModeListener.toggleItemChecked(position)
                    }
                }
                binding.root.setOnLongClickListener {
                    actionModeListener.toggleItemChecked(position)
                    true
                }
                binding.tunnelShare.contentDescription =
                    getString(R.string.tunnel_share_description, item.name)
                // A separate click listener rather than a data binding expression: the share menu is
                // built imperatively and needs the row's anchor view, which the layout cannot pass.
                binding.tunnelShare.setOnClickListener { anchor ->
                    showTunnelShareMenu(item, anchor)
                }
                if (actionMode != null)
                    (binding.root as MultiselectableRelativeLayout).setMultiSelected(actionModeListener.checkedItems.contains(position))
                else
                    (binding.root as MultiselectableRelativeLayout).setSingleSelected(selectedTunnel == item)
            }
        }
    }

    /**
     * Rebuilds the space strip as `All`, `Default`, then one tab per subscription. The selected
     * space is preserved if it still exists; if its subscription was deleted, fall back to `All`.
     */
    private fun rebuildSpaceTabs() {
        val tabs = binding?.spaceTabs ?: return
        if (selectedSpace is SpaceFilter.SubscriptionSpace &&
            subscriptions.none { it.id == (selectedSpace as SpaceFilter.SubscriptionSpace).id }
        ) {
            selectedSpace = SpaceFilter.All
        }
        val spaces = buildList {
            add(SpaceFilter.All)
            add(SpaceFilter.Default)
            subscriptions.forEach { add(SpaceFilter.SubscriptionSpace(it.id, it.name)) }
        }
        updatingTabs = true
        try {
            tabs.removeAllTabs()
            spaces.forEach { space ->
                tabs.addTab(tabs.newTab().setText(space.label()).setTag(space))
            }
            val index = spaces.indexOf(selectedSpace).coerceAtLeast(0)
            tabs.getTabAt(index)?.select()
        } finally {
            updatingTabs = false
        }
    }

    private fun SpaceFilter.label(): String = when (this) {
        SpaceFilter.All -> getString(R.string.space_all)
        SpaceFilter.Default -> getString(R.string.space_default)
        is SpaceFilter.SubscriptionSpace -> name
    }

    /**
     * Rebuilds [visibleTunnels] from the tunnel manager and the current space selection.
     *
     * The bound adapter observes [visibleTunnels], not the tunnel manager, so anything that
     * creates or deletes tunnels behind the list's back must call this. That includes the
     * subscription engine: a sync or a subscription removal changes the tunnel set without
     * touching this fragment.
     */
    fun refreshTunnels() {
        refreshVisibleTunnels()
    }

    /** Repopulates [visibleTunnels] with whichever tunnels belong to the selected space. */
    private fun refreshVisibleTunnels() {
        lifecycleScope.launch {
            val all = Application.getTunnelManager().getTunnels()
            val filtered = all.filter { selectedSpace.owns(it.name, subscriptions) }
            visibleTunnels.clear()
            visibleTunnels.addAll(filtered)
            updateEmptyState(filtered.isEmpty())
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        val placeholder = binding?.emptyPlaceholderText ?: return
        placeholder.text = when {
            !isEmpty -> ""
            selectedSpace is SpaceFilter.SubscriptionSpace ->
                getString(R.string.space_placeholder_subscription, selectedSpace.label())
            selectedSpace == SpaceFilter.Default -> getString(R.string.space_placeholder_default)
            else -> getString(R.string.tunnel_list_placeholder)
        }
    }

    /**
     * Runs a manual sync of every enabled subscription, reporting the outcome. Backs both
     * pull-to-refresh and the toolbar action.
     */
    fun syncNow() {
        val swipeRefresh = binding?.swipeRefresh ?: return
        lifecycleScope.launch {
            swipeRefresh.isRefreshing = true
            val message = try {
                SubscriptionSyncManager.syncAll().toMessage(requireContext())
                    ?: getString(R.string.sync_no_subscriptions)
            } catch (e: Throwable) {
                Log.e(TAG, "Manual subscription sync failed", e)
                getString(R.string.sync_failed, ErrorMessages[e])
            } finally {
                binding?.swipeRefresh?.isRefreshing = false
            }
            refreshVisibleTunnels()
            showSnackbar(message)
        }
    }

    private fun showSnackbar(message: CharSequence) {
        refreshVisibleTunnels()
        val binding = binding
        if (binding != null)
            Snackbar.make(binding.mainContainer, message, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.createFab)
                .show()
        else
            Toast.makeText(activity ?: Application.get(), message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Locates the row for [tunnel] in the *visible* list, since that is what the adapter is
     * showing and therefore what the adapter positions refer to.
     */
    private fun viewForTunnel(tunnel: ObservableTunnel): MultiselectableRelativeLayout? {
        val position = visibleTunnels.indexOfKey(tunnel.name)
        if (position < 0) return null
        return binding?.tunnelList
            ?.findViewHolderForAdapterPosition(position)?.itemView
            as? MultiselectableRelativeLayout
    }

    private inner class ActionModeListener : ActionMode.Callback {
        val checkedItems: MutableCollection<Int> = HashSet()
        private var resources: Resources? = null

        fun getCheckedItems(): ArrayList<Int> {
            return ArrayList(checkedItems)
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.menu_action_delete -> {
                    val activity = activity ?: return true
                    val copyCheckedItems = HashSet(checkedItems)
                    binding?.createFab?.apply {
                        visibility = View.VISIBLE
                        scaleX = 1f
                        scaleY = 1f
                    }
                    activity.lifecycleScope.launch {
                        try {
                            val tunnels = visibleTunnels.toList()
                            val tunnelsToDelete = ArrayList<ObservableTunnel>()
                            for (position in copyCheckedItems) tunnelsToDelete.add(tunnels[position])
                            val futures = tunnelsToDelete.map { async(SupervisorJob()) { it.deleteAsync() } }
                            onTunnelDeletionFinished(futures.awaitAll().size, null)
                        } catch (e: Throwable) {
                            onTunnelDeletionFinished(0, e)
                        } finally {
                            refreshVisibleTunnels()
                        }
                    }
                    checkedItems.clear()
                    mode.finish()
                    true
                }

                R.id.menu_action_select_all -> {
                    for (i in 0 until visibleTunnels.size) {
                        setItemChecked(i, true)
                    }
                    true
                }

                else -> false
            }
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            actionMode = mode
            backPressedCallback?.isEnabled = true
            if (activity != null) {
                resources = activity!!.resources
            }
            animateFab(binding?.createFab, false)
            mode.menuInflater.inflate(R.menu.tunnel_list_action_mode, menu)
            binding?.tunnelList?.adapter?.notifyDataSetChanged()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            backPressedCallback?.isEnabled = false
            resources = null
            animateFab(binding?.createFab, true)
            checkedItems.clear()
            binding?.tunnelList?.adapter?.notifyDataSetChanged()
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            updateTitle(mode)
            return false
        }

        fun setItemChecked(position: Int, checked: Boolean) {
            if (checked) {
                checkedItems.add(position)
            } else {
                checkedItems.remove(position)
            }
            val adapter = if (binding == null) null else binding!!.tunnelList.adapter
            if (actionMode == null && !checkedItems.isEmpty() && activity != null) {
                (activity as AppCompatActivity).startSupportActionMode(this)
            } else if (actionMode != null && checkedItems.isEmpty()) {
                actionMode!!.finish()
            }
            adapter?.notifyItemChanged(position)
            updateTitle(actionMode)
        }

        fun toggleItemChecked(position: Int) {
            setItemChecked(position, !checkedItems.contains(position))
        }

        private fun updateTitle(mode: ActionMode?) {
            if (mode == null) {
                return
            }
            val count = checkedItems.size
            if (count == 0) {
                mode.title = ""
            } else {
                mode.title = resources!!.getQuantityString(R.plurals.delete_title, count, count)
            }
        }

        private fun animateFab(view: View?, show: Boolean) {
            view ?: return
            val animation = AnimationUtils.loadAnimation(
                context, if (show) R.anim.scale_up else R.anim.scale_down
            )
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {
                }

                override fun onAnimationEnd(animation: Animation?) {
                    if (!show) view.visibility = View.GONE
                }

                override fun onAnimationStart(animation: Animation?) {
                    if (show) view.visibility = View.VISIBLE
                }
            })
            view.startAnimation(animation)
        }
    }

    companion object {
        private const val CHECKED_ITEMS = "CHECKED_ITEMS"
        private const val TAG = "AmneziaWG/TunnelListFragment"
    }
}
