package org.amnezia.awg

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.amnezia.awg.activity.MainActivity
import org.amnezia.awg.backend.Statistics
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.util.QuantityFormatter
import org.amnezia.awg.util.applicationScope

class ConnectionStatusService : Service() {
    private var isUpdateActive = true

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        val channel = NotificationChannelCompat.Builder(CONNECTION_STATUS_NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT)
            .setName(getString(R.string.notification_channel_name))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        showDisconnectingNotification()
        isUpdateActive = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()

        applicationScope.launch {
            while (isUpdateActive) {
                updateConnectionStatus()
                delay(1000)
            }
        }

        return START_STICKY
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createActionIntent(): PendingIntent {
        val intent = Intent(this, DisconnectTunnelsReceiver::class.java).apply {
            action = "org.amnezia.awg.action.SET_ALL_TUNNELS_DOWN"
            putExtra(NotificationCompat.EXTRA_NOTIFICATION_ID, FOREGROUND_NOTIFICATION_ID)
        }
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createOneTunnelNotification(tunnel: Tunnel, config: Config, statistics: Statistics): Notification {
        var contextText: String
        if (config.peers.size == 1) {
            val peerStats = statistics.peer(config.peers.first().publicKey)
            val rxBytes = QuantityFormatter.formatBytes(peerStats?.rxBytes() ?: 0)
            val txBytes = QuantityFormatter.formatBytes(peerStats?.txBytes() ?: 0)
            contextText = getString(R.string.notification_text_rx_tx, rxBytes, txBytes)
        } else {
            contextText = getString(R.string.notification_text_peers_count, config.peers.size)
        }
        val builder = NotificationCompat.Builder(this, CONNECTION_STATUS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(getString(R.string.notification_title_connected_to, tunnel.name))
            .setContentText(contextText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
            .addAction(0, getString(R.string.notification_action_disconnect), createActionIntent())
        return builder.build()
    }

    private fun createMultipleTunnelNotification(tunnels: MutableList<ObservableTunnel>): Notification {
        val builder = NotificationCompat.Builder(this, CONNECTION_STATUS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(getString(R.string.notification_title_connected))
            .setContentText(getString(R.string.notification_text_tunnels_count, tunnels.size))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
            .addAction(0, getString(R.string.notification_action_disconnect), createActionIntent())
        return builder.build()
    }

    private fun createConnectingNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CONNECTION_STATUS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(getString(R.string.notification_title_connecting))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
        return builder.build()
    }

    private fun createDisconnectedNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CONNECTION_STATUS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(getString(R.string.notification_title_disconnected))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
        return builder.build()
    }

    private fun showNotification(notification: Notification) {
        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this@ConnectionStatusService,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@with
            }

            notify(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private suspend fun updateConnectionStatus() {
        val manager = Application.getTunnelManager()
        val activeTunnels = mutableListOf<ObservableTunnel>()
        manager.getTunnels().forEach {
            if (it.state == Tunnel.State.UP) {
                activeTunnels.add(it)
            }
        }

        var notification: Notification
        if (activeTunnels.size == 1) {
            val tunnel = activeTunnels.first()
            val statistics = manager.getTunnelStatistics(tunnel)
            val config = manager.getTunnelConfig(tunnel)
            notification = createOneTunnelNotification(tunnel, config, statistics)
        } else if (activeTunnels.size > 1) {
            notification = createMultipleTunnelNotification(activeTunnels)
        } else {
            notification = createDisconnectedNotification()
        }

        showNotification(notification)
    }

    private fun showDisconnectingNotification() {
        val builder = NotificationCompat.Builder(this, CONNECTION_STATUS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(getString(R.string.notification_title_disconnecting))
            .setTimeoutAfter(500)
            .setSilent(true)
            .setContentIntent(createContentIntent())
        showNotification(builder.build())
    }

    private fun startForeground() {
        try {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                createConnectingNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                Log.e(TAG, "Failed to start foreground service", e)
            }
        }
    }

    companion object {
        private const val TAG = "AmneziaWG/ConnectionStatusService"
        private const val CONNECTION_STATUS_NOTIFICATION_CHANNEL_ID: String = "connection_status"
        private const val FOREGROUND_NOTIFICATION_ID = 1
    }
}
