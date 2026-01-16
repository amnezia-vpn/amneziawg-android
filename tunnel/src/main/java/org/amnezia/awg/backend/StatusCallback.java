package org.amnezia.awg.backend;

/**
 * Callback for status changes detected by the status polling job.
 */
public interface StatusCallback {
    /**
     * Called when connection status is determined.
     *
     * @param connected true if handshake was successful (connected), false if disconnected
     */
    void onStatusChanged(boolean connected);
}

