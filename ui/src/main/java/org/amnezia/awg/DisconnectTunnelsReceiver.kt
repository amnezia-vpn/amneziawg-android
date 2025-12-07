package org.amnezia.awg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.util.applicationScope

class DisconnectTunnelsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if ("org.amnezia.awg.action.SET_ALL_TUNNELS_DOWN" != action) return

        applicationScope.launch {
            val manager = Application.getTunnelManager()
            manager.getTunnels().forEach {
                try {
                    manager.setTunnelState(it, Tunnel.State.DOWN)
                } catch (e: Throwable) {
                    Toast.makeText(context, ErrorMessages[e], Toast.LENGTH_LONG).show()
                    Log.e(TAG, ErrorMessages[e], e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AmneziaWG/DisconnectTunnelReceiver"
    }
}
