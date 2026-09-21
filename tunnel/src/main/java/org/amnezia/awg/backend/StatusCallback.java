package org.amnezia.awg.backend;

/**
 * Callback for status changes detected by the status polling job.
 */
public interface StatusCallback {
    /**
     * Called when connection status is determined.
     *
     * @param tunnel    The tunnel whose connection status changed.
     * @param connected true if handshake is fresh (connected), false if the tunnel lost its
     *                  connection and is reconnecting
     */
    void onStatusChanged(Tunnel tunnel, boolean connected);
}

