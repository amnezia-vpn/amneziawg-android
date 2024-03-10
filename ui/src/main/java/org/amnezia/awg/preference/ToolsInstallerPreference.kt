/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.util.ToolsInstaller
import org.amnezia.awg.util.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Preference implementing a button that asynchronously runs `ToolsInstaller` and displays the
 * result as the preference summary.
 */
class ToolsInstallerPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var state = State.INITIAL
    override fun getSummary() = context.getString(state.messageResourceId)

    override fun getTitle() = context.getString(R.string.tools_installer_title)

    override fun onAttached() {
        super.onAttached()
        lifecycleScope.launch {
            try {
                val state = withContext(Dispatchers.IO) { Application.getToolsInstaller().areInstalled() }
                when {
                    state == ToolsInstaller.ERROR -> setState(State.INITIAL)
                    state and ToolsInstaller.YES == ToolsInstaller.YES -> setState(State.ALREADY)
                    state and (ToolsInstaller.MAGISK or ToolsInstaller.NO) == ToolsInstaller.MAGISK or ToolsInstaller.NO -> setState(State.INITIAL_MAGISK)
                    state and (ToolsInstaller.SYSTEM or ToolsInstaller.NO) == ToolsInstaller.SYSTEM or ToolsInstaller.NO -> setState(State.INITIAL_SYSTEM)
                    else -> setState(State.INITIAL)
                }
            } catch (_: Throwable) {
                setState(State.INITIAL)
            }
        }
    }

    override fun onClick() {
        setState(State.WORKING)
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { Application.getToolsInstaller().install() }
                when {
                    result and (ToolsInstaller.YES or ToolsInstaller.MAGISK) == ToolsInstaller.YES or ToolsInstaller.MAGISK -> setState(State.SUCCESS_MAGISK)
                    result and (ToolsInstaller.YES or ToolsInstaller.SYSTEM) == ToolsInstaller.YES or ToolsInstaller.SYSTEM -> setState(State.SUCCESS_SYSTEM)
                    else -> setState(State.FAILURE)
                }
            } catch (_: Throwable) {
                setState(State.FAILURE)
            }
        }
    }

    private fun setState(state: State) {
        if (this.state == state) return
        this.state = state
        if (isEnabled != state.shouldEnableView) isEnabled = state.shouldEnableView
        notifyChanged()
    }

    private enum class State(val messageResourceId: Int, val shouldEnableView: Boolean) {
        INITIAL(R.string.tools_installer_initial, true),
        ALREADY(R.string.tools_installer_already, false),
        FAILURE(R.string.tools_installer_failure, true),
        WORKING(R.string.tools_installer_working, false),
        INITIAL_SYSTEM(R.string.tools_installer_initial_system, true),
        SUCCESS_SYSTEM(R.string.tools_installer_success_system, false),
        INITIAL_MAGISK(R.string.tools_installer_initial_magisk, true),
        SUCCESS_MAGISK(R.string.tools_installer_success_magisk, false);
    }
}
