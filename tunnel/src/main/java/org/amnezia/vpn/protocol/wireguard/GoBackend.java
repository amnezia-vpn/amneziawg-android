package org.amnezia.vpn.protocol.wireguard;

import androidx.annotation.Nullable;

public class GoBackend {
    @Nullable
    public static native String wgGetConfig(int handle);

    public static native int wgGetSocketV4(int handle);

    public static native int wgGetSocketV6(int handle);

    public static native void wgTurnOff(int handle);

    public static native int wgTurnOn(String ifName, int tunFd, String settings);

    public static native String wgVersion();
}
