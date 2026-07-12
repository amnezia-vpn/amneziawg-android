/*
 * Copyright © 2024 AmneziaWG Contributors. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.amnezia.awg.R

class DomainListDialogFragment : DialogFragment() {

    private val domains = mutableListOf<String>()
    private var domainsContainer: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        domains.addAll(arguments?.getStringArrayList(KEY_DOMAINS) ?: emptyList())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireActivity()
        val scrollView = android.widget.ScrollView(ctx)
        domainsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        scrollView.addView(domainsContainer)

        // Populate existing domains
        for (domain in domains) addDomainRow(domain)

        // Input row for adding new domain
        val inputLayout = TextInputLayout(ctx).apply {
            hint = getString(R.string.domain_hint)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (8 * resources.displayMetrics.density).toInt()
            layoutParams = lp
        }
        val inputEdit = TextInputEditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = "example.com"
        }
        inputLayout.addView(inputEdit)
        domainsContainer?.addView(inputLayout)

        inputEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addDomainFromInput(inputEdit)
                true
            } else false
        }

        return MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.excluded_domains_title)
            .setView(scrollView)
            .setPositiveButton(R.string.save) { _, _ ->
                // Collect remaining domain rows (skip the last input row)
                val result = ArrayList<String>()
                val container = domainsContainer ?: return@setPositiveButton
                // Check if there's text in the input field and add it
                val text = inputEdit.text?.toString()?.trim() ?: ""
                if (text.isNotEmpty() && isValidDomain(text)) result.add(text)
                for (i in 0 until container.childCount - 1) {
                    val row = container.getChildAt(i) as? LinearLayout ?: continue
                    val tv = row.getChildAt(0) as? TextView ?: continue
                    result.add(tv.text.toString())
                }
                setFragmentResult(REQUEST_DOMAINS, bundleOf(KEY_DOMAINS to result.toTypedArray()))
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun addDomainFromInput(input: EditText) {
        val domain = input.text?.toString()?.trim() ?: return
        if (domain.isEmpty()) return
        if (!isValidDomain(domain)) {
            Toast.makeText(requireContext(), getString(R.string.domain_invalid, domain), Toast.LENGTH_SHORT).show()
            return
        }
        addDomainRow(domain)
        input.text?.clear()
    }

    private fun addDomainRow(domain: String) {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val vertMargin = (4 * resources.displayMetrics.density).toInt()
            lp.topMargin = vertMargin
            lp.bottomMargin = vertMargin
            layoutParams = lp
        }
        val label = TextView(ctx).apply {
            text = domain
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 14f
            val pad = (8 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val deleteBtn = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = getString(R.string.delete)
            background = null
            setOnClickListener { domainsContainer?.removeView(row) }
        }
        row.addView(label)
        row.addView(deleteBtn)
        // Insert before the last child (which is the input row)
        val container = domainsContainer ?: return
        container.addView(row, container.childCount - 1)
    }

    private fun isValidDomain(domain: String): Boolean {
        val cleaned = if (domain.startsWith("*.")) domain.substring(2) else domain
        return cleaned.isNotEmpty() &&
                cleaned.matches(Regex("^[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?)*$"))
    }

    companion object {
        const val KEY_DOMAINS = "domains"
        const val REQUEST_DOMAINS = "request_domains"

        fun newInstance(domains: ArrayList<String>): DomainListDialogFragment {
            return DomainListDialogFragment().apply {
                arguments = bundleOf(KEY_DOMAINS to domains)
            }
        }
    }
}
