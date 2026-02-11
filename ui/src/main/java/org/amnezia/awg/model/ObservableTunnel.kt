/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.model

import android.util.Log
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import org.amnezia.awg.BR
import org.amnezia.awg.backend.Statistics
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.databinding.Keyed
import org.amnezia.awg.util.applicationScope
import org.amnezia.awg.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Encapsulates the volatile and nonvolatile state of an AmneziaWG tunnel.
 */
class ObservableTunnel internal constructor(
    private val manager: TunnelManager,
    private var name: String,
    config: Config?,
    state: Tunnel.State
) : BaseObservable(), Keyed<String>, Tunnel {
    override val key
        get() = name

    @Bindable
    override fun getName() = name

    suspend fun setNameAsync(name: String): String = withContext(Dispatchers.Main.immediate) {
        if (name != this@ObservableTunnel.name)
            manager.setTunnelName(this@ObservableTunnel, name)
        else
            this@ObservableTunnel.name
    }

    fun onNameChanged(name: String): String {
        this.name = name
        notifyPropertyChanged(BR.name)
        return name
    }


    @get:Bindable
    var state = state
        private set

    @get:Bindable
    var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
        private set

    override fun onStateChange(newState: Tunnel.State) {
        onStateChanged(newState)
    }

    fun onStateChanged(state: Tunnel.State): Tunnel.State {
        if (state != Tunnel.State.UP) {
            onStatisticsChanged(null)
            onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
        } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
            // When state changes to UP, set to CONNECTING until handshake confirms
            onConnectionStatusChanged(ConnectionStatus.CONNECTING)
        }
        this.state = state
        notifyPropertyChanged(BR.state)
        return state
    }

    fun onConnectionStatusChanged(status: ConnectionStatus): ConnectionStatus {
        if (status != this.connectionStatus) {
            this.connectionStatus = status
            notifyPropertyChanged(BR.connectionStatus)
            Log.d(TAG, "Connection status changed for $name: $status")
        }
        return status
    }

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    suspend fun setStateAsync(state: Tunnel.State): Tunnel.State = withContext(Dispatchers.Main.immediate) {
        if (state != this@ObservableTunnel.state)
            manager.setTunnelState(this@ObservableTunnel, state)
        else
            this@ObservableTunnel.state
    }


    @get:Bindable
    var config = config
        get() {
            if (field == null)
            // Opportunistically fetch this if we don't have a cached one, and rely on data bindings to update it eventually
                applicationScope.launch {
                    try {
                        manager.getTunnelConfig(this@ObservableTunnel)
                    } catch (e: Throwable) {
                        Log.e(TAG, Log.getStackTraceString(e))
                    }
                }
            return field
        }
        private set

    suspend fun getConfigAsync(): Config = withContext(Dispatchers.Main.immediate) {
        config ?: manager.getTunnelConfig(this@ObservableTunnel)
    }

    suspend fun setConfigAsync(config: Config): Config = withContext(Dispatchers.Main.immediate) {
        this@ObservableTunnel.config.let {
            if (config != it)
                manager.setTunnelConfig(this@ObservableTunnel, config)
            else
                it
        }
    }

    fun onConfigChanged(config: Config?): Config? {
        this.config = config
        notifyPropertyChanged(BR.config)
        return config
    }


    @get:Bindable
    var statistics: Statistics? = null
        get() {
            if (field == null || field?.isStale != false)
            // Opportunistically fetch this if we don't have a cached one, and rely on data bindings to update it eventually
                applicationScope.launch {
                    try {
                        manager.getTunnelStatistics(this@ObservableTunnel)
                    } catch (e: Throwable) {
                        Log.e(TAG, Log.getStackTraceString(e))
                    }
                }
            return field
        }
        private set

    suspend fun getStatisticsAsync(): Statistics = withContext(Dispatchers.Main.immediate) {
        statistics.let {
            if (it == null || it.isStale)
                manager.getTunnelStatistics(this@ObservableTunnel)
            else
                it
        }
    }

    fun onStatisticsChanged(statistics: Statistics?): Statistics? {
        this.statistics = statistics
        notifyPropertyChanged(BR.statistics)
        return statistics
    }


    suspend fun deleteAsync() = manager.delete(this)


    companion object {
        private const val TAG = "AmneziaWG/ObservableTunnel"
    }
}
