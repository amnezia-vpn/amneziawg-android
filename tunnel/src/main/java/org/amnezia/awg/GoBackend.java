package org.amnezia.awg;

import androidx.annotation.Nullable;

public class GoBackend {
    @Nullable
    public static native String awgGetConfig(int handle);

    public static native int awgGetSocketV4(int handle);

    public static native int awgGetSocketV6(int handle);

    public static native void awgTurnOff(int handle);

    public static native int awgTurnOn(String ifName, int tunFd, String settings);

    public static native String awgVersion();

    /**
     * Installs the Strict Split Tunneling filter, or removes it when null. Returns 0 on
     * success, -1 if the filter could not be registered.
     */
    public static native int awgSetUidFilter(@Nullable UidFilter filter);

    /**
     * Decides whether a new outbound flow read from the tun device may enter the tunnel.
     * Called from native code, once per new flow, on threads the JVM did not start,
     * several at a time: implementations must be thread-safe.
     */
    public interface UidFilter {
        boolean allow(String network, String srcIp, int srcPort, String dstIp, int dstPort);
    }
}
