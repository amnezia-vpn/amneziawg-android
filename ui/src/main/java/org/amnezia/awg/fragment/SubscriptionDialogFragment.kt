/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.app.Dialog
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.amnezia.awg.R
import org.amnezia.awg.databinding.DialogSubscriptionEditBinding
import org.amnezia.awg.databinding.SubscriptionDialogFragmentBinding
import org.amnezia.awg.databinding.SubscriptionListItemBinding
import org.amnezia.awg.model.Subscription
import org.amnezia.awg.subscription.SubscriptionStore
import org.amnezia.awg.subscription.SubscriptionSyncManager
import org.amnezia.awg.subscription.SyncState
import org.amnezia.awg.subscription.SyncSummary
import org.amnezia.awg.subscription.toMessage
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.util.ShareUtils

/**
 * Manages the user's subscriptions: adding, editing, pausing, removing and manually syncing them.
 */
class SubscriptionDialogFragment : DialogFragment() {
    private var binding: SubscriptionDialogFragmentBinding? = null
    private val adapter = SubscriptionAdapter()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = SubscriptionDialogFragmentBinding.inflate(LayoutInflater.from(requireContext()))
        this.binding = binding
        binding.subscriptionList.layoutManager = LinearLayoutManager(requireContext())
        binding.subscriptionList.adapter = adapter
        binding.subscriptionAdd.setOnClickListener { showEditDialog(null) }

        observe()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.subscription_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    private fun observe() {
        launchWhileStarted {
            SubscriptionStore.subscriptions.collectLatest { subscriptions ->
                adapter.submit(subscriptions)
                binding?.subscriptionEmpty?.visibility =
                    if (subscriptions.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        launchWhileStarted {
            SubscriptionSyncManager.state.collectLatest { state ->
                val syncing = state is SyncState.Syncing
                binding?.subscriptionProgress?.visibility = if (syncing) View.VISIBLE else View.GONE
                adapter.setSyncing(syncing)
                if (state is SyncState.Finished) reportSummary(state.summary)
            }
        }
    }

    /**
     * Runs [block] for as long as the dialog is at least STARTED. A DialogFragment has no view
     * lifecycle, so this is bound to the fragment's own lifecycle.
     */
    private fun launchWhileStarted(block: suspend CoroutineScope.() -> Unit) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { block() }
        }
    }

    private fun reportSummary(summary: SyncSummary) {
        val context = context ?: return
        val message = summary.toMessage(context) ?: return
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun showEditDialog(subscription: Subscription?) {
        val dialogBinding = DialogSubscriptionEditBinding.inflate(LayoutInflater.from(requireContext()))
        if (subscription != null) {
            dialogBinding.subscriptionNameInput.setText(subscription.name)
            dialogBinding.subscriptionUrlInput.setText(subscription.url)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (subscription == null) R.string.subscription_add else R.string.subscription_edit)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                saveSubscription(subscription, dialogBinding)
            }
            .show()
    }

    private fun saveSubscription(existing: Subscription?, dialogBinding: DialogSubscriptionEditBinding) {
        val context = context ?: return
        val name = dialogBinding.subscriptionNameInput.textAsString()
        val url = dialogBinding.subscriptionUrlInput.textAsString()
        var valid = true

        if (name.isBlank()) {
            dialogBinding.subscriptionNameLayout.error = context.getString(R.string.subscription_name_required)
            valid = false
        }
        if (!isValidUrl(url)) {
            dialogBinding.subscriptionUrlLayout.error = context.getString(R.string.subscription_url_invalid)
            valid = false
        }
        if (!valid) return

        lifecycleScope.launch {
            if (existing == null) {
                SubscriptionStore.upsert(Subscription(name = name, url = url))
                Toast.makeText(context, context.getString(R.string.subscription_saved, name), Toast.LENGTH_SHORT).show()
            } else {
                SubscriptionStore.upsert(existing.copy(name = name, url = url))
                Toast.makeText(context, context.getString(R.string.subscription_updated, name), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
        return runCatching { java.net.URI(trimmed) }.getOrNull()?.host?.isNotEmpty() == true
    }

    private fun confirmRemoval(subscription: Subscription) {
        val context = context ?: return
        val message = if (subscription.managedTunnels.isEmpty()) {
            getString(R.string.subscription_delete_none, subscription.name)
        } else {
            getString(R.string.subscription_delete_message, subscription.name, subscription.managedTunnels.size)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.subscription_delete_title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.subscription_delete) { _, _ ->
                lifecycleScope.launch {
                    SubscriptionSyncManager.remove(subscription.id)
                    // The subscription record is dropped before its tunnels are deleted, so the
                    // store flow that normally drives the list has already fired by now. Refresh
                    // explicitly once the cascade has actually finished.
                    (parentFragment as? TunnelListFragment)?.refreshTunnels()
                    Toast.makeText(context, R.string.subscription_removed, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /**
     * The share action on a subscription row: copy the link, show it as a QR code, or save it to a
     * file.
     *
     * Unlike a tunnel configuration this does not authenticate. The URL is not a private key, but it
     * is the token the app authenticates to the provider with, so every action is confirmed with a
     * warning rather than gated behind biometrics.
     */
    private fun showSubscriptionShareMenu(subscription: Subscription, anchor: View) {
        val context = context ?: return
        val activity = activity as? FragmentActivity ?: return
        val label = context.getString(R.string.subscription_share_copy_label)
        PopupMenu(context, anchor).apply {
            menuInflater.inflate(R.menu.subscription_share, menu)
            setOnMenuItemClickListener { menuItem ->
                ShareUtils.confirmSecret(this@SubscriptionDialogFragment, R.string.share_confirm_link) {
                    when (menuItem.itemId) {
                        R.id.subscription_share_copy ->
                            ShareUtils.copy(anchor, R.string.subscription_share_copy_label, subscription.url)

                        R.id.subscription_share_qr ->
                            QrCodeDialogFragment.show(
                                childFragmentManager, subscription.url, R.string.qr_code_contains_link
                            )

                        R.id.subscription_share_export ->
                            lifecycleScope.launch {
                                ShareUtils.exportFile(
                                    activity,
                                    subscriptionShareFileName(subscription),
                                    subscription.url
                                ) { result ->
                                    val message = result.fold(
                                        onSuccess = { path -> getString(R.string.share_export_success, path) },
                                        onFailure = { throwable ->
                                            getString(R.string.share_export_error, ErrorMessages[throwable])
                                        }
                                    )
                                    Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show()
                                }
                            }
                    }
                }
                true
            }
            show()
        }
    }

    /**
     * A file name for the link, derived from the subscription's name. Tunnel names are already
     * constrained to a safe character set, subscription names are not, so anything outside it is
     * replaced rather than trusted. The extension is `.txt` because the payload is a bare URL, not
     * a configuration.
     */
    private fun subscriptionShareFileName(subscription: Subscription): String {
        val safe = subscription.name.map { if (it.isLetterOrDigit() || it in SAFE_NAME_CHARS) it else '_' }
            .joinToString("")
            .take(48)
        return "${safe.ifEmpty { "subscription" }}.txt"
    }

    private fun TextInputEditText.textAsString(): String = text?.toString()?.trim().orEmpty()

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private inner class SubscriptionAdapter : RecyclerView.Adapter<SubscriptionViewHolder>() {
        private var items: List<Subscription> = emptyList()
        private var syncing = false

        fun submit(subscriptions: List<Subscription>) {
            items = subscriptions
            notifyDataSetChanged()
        }

        fun setSyncing(syncing: Boolean) {
            if (this.syncing == syncing) return
            this.syncing = syncing
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
            val itemBinding = SubscriptionListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SubscriptionViewHolder(itemBinding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
            holder.bind(items[position], syncing)
        }
    }

    private inner class SubscriptionViewHolder(
        private val itemBinding: SubscriptionListItemBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {

        fun bind(subscription: Subscription, syncing: Boolean) {
            val context = itemBinding.root.context
            itemBinding.subscriptionItemName.text = subscription.name
            itemBinding.subscriptionItemUrl.text = subscription.url

            val syncedAgo = if (subscription.lastSynced <= 0L) null else DateUtils.getRelativeTimeSpanString(
                subscription.lastSynced,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            val status = buildList {
                add(
                    if (syncedAgo != null)
                        context.getString(R.string.subscription_last_synced, syncedAgo)
                    else
                        context.getString(R.string.subscription_never_synced)
                )
                if (subscription.managedTunnels.isNotEmpty()) {
                    add(context.getString(R.string.subscription_managed_count, subscription.managedTunnels.size))
                }
                if (!subscription.enabled) add(context.getString(R.string.subscription_disabled))
            }
            itemBinding.subscriptionItemStatus.text = status.joinToString(" · ")

            itemBinding.subscriptionItemSync.isEnabled = !syncing && subscription.enabled
            itemBinding.subscriptionItemSync.setOnClickListener {
                lifecycleScope.launch { SubscriptionSyncManager.syncOne(subscription.id) }
            }
            itemBinding.subscriptionItemShare.contentDescription =
                context.getString(R.string.subscription_share_description, subscription.name)
            itemBinding.subscriptionItemShare.setOnClickListener { anchor ->
                showSubscriptionShareMenu(subscription, anchor)
            }
            itemBinding.subscriptionItemMenu.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menuInflater.inflate(R.menu.subscription_item, menu)
                    menu.findItem(R.id.subscription_menu_pause).setTitle(
                        if (subscription.enabled) R.string.subscription_pause else R.string.subscription_resume
                    )
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.subscription_menu_edit -> showEditDialog(subscription)
                            R.id.subscription_menu_pause -> lifecycleScope.launch {
                                SubscriptionStore.update(subscription.id) { it.copy(enabled = !it.enabled) }
                            }
                            R.id.subscription_menu_delete -> confirmRemoval(subscription)
                        }
                        true
                    }
                    show()
                }
            }
        }
    }

    companion object {
        const val TAG = "SubscriptionDialogFragment"
        private const val SAFE_NAME_CHARS = "_-. "
    }
}
