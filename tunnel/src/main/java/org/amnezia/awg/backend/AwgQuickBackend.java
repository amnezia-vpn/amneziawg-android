/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.amnezia.awg.backend.BackendException.Reason;
import org.amnezia.awg.backend.Tunnel.State;
import org.amnezia.awg.util.RootShell;
import org.amnezia.awg.util.ToolsInstaller;
import org.amnezia.awg.config.Config;
import org.amnezia.awg.crypto.Key;
import org.amnezia.awg.util.NonNullForAll;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.Nullable;

/**
 * Implementation of {@link Backend} that uses the kernel module and {@code awg-quick} to provide
 * AmneziaWG tunnels.
 */

@NonNullForAll
public final class AwgQuickBackend implements Backend {
    private static final String TAG = "AmneziaWG/AwgQuickBackend";
    private final File localTemporaryDir;
    private final RootShell rootShell;
    private final Map<Tunnel, Config> runningConfigs = new HashMap<>();
    private final ToolsInstaller toolsInstaller;
    private boolean multipleTunnels;

    public AwgQuickBackend(final Context context, final RootShell rootShell, final ToolsInstaller toolsInstaller) {
        localTemporaryDir = new File(context.getCacheDir(), "tmp");
        this.rootShell = rootShell;
        this.toolsInstaller = toolsInstaller;
    }

    public static boolean hasKernelSupport() {
        return new File("/sys/module/amneziawg").exists();
    }

    @Override
    public Set<String> getRunningTunnelNames() {
        final List<String> output = new ArrayList<>();
        // Don't throw an exception here or nothing will show up in the UI.
        try {
            toolsInstaller.ensureToolsAvailable();
            if (rootShell.run(output, "awg show interfaces") != 0 || output.isEmpty())
                return Collections.emptySet();
        } catch (final Exception e) {
            Log.w(TAG, "Unable to enumerate running tunnels", e);
            return Collections.emptySet();
        }
        // awg puts all interface names on the same line. Split them into separate elements.
        return Set.of(output.get(0).split(" "));
    }

    @Override
    public State getState(final Tunnel tunnel) {
        return getRunningTunnelNames().contains(tunnel.getName()) ? State.UP : State.DOWN;
    }

    @Override
    public Statistics getStatistics(final Tunnel tunnel) {
        final Statistics stats = new Statistics();
        final Collection<String> output = new ArrayList<>();
        try {
            if (rootShell.run(output, String.format("awg show '%s' dump", tunnel.getName())) != 0)
                return stats;
        } catch (final Exception ignored) {
            return stats;
        }
        for (final String line : output) {
            final String[] parts = line.split("\\t");
            if (parts.length != 8)
                continue;
            try {
                stats.add(Key.fromBase64(parts[0]), Long.parseLong(parts[5]), Long.parseLong(parts[6]), Long.parseLong(parts[4]) * 1000);
            } catch (final Exception ignored) {
            }
        }
        return stats;
    }

    @Override
    public String getVersion() throws Exception {
        final List<String> output = new ArrayList<>();
        if (rootShell.run(output, "cat /sys/module/amneziawg/version") != 0 || output.isEmpty())
            throw new BackendException(Reason.UNKNOWN_KERNEL_MODULE_NAME);
        return output.get(0);
    }

    public void setMultipleTunnels(final boolean on) {
        multipleTunnels = on;
    }

    @Override
    public State setState(final Tunnel tunnel, State state, @Nullable final Config config) throws Exception {
        final State originalState = getState(tunnel);
        final Config originalConfig = runningConfigs.get(tunnel);
        final Map<Tunnel, Config> runningConfigsSnapshot = new HashMap<>(runningConfigs);

        if (state == State.TOGGLE)
            state = originalState == State.UP ? State.DOWN : State.UP;
        if ((state == State.UP && originalState == State.UP && originalConfig != null && originalConfig == config) ||
                (state == State.DOWN && originalState == State.DOWN))
            return originalState;
        if (state == State.UP) {
            toolsInstaller.ensureToolsAvailable();
            if (!multipleTunnels && originalState == State.DOWN) {
                final List<Pair<Tunnel, Config>> rewind = new LinkedList<>();
                try {
                    for (final Map.Entry<Tunnel, Config> entry : runningConfigsSnapshot.entrySet()) {
                        setStateInternal(entry.getKey(), entry.getValue(), State.DOWN);
                        rewind.add(Pair.create(entry.getKey(), entry.getValue()));
                    }
                } catch (final Exception e) {
                    try {
                        for (final Pair<Tunnel, Config> entry : rewind) {
                            setStateInternal(entry.first, entry.second, State.UP);
                        }
                    } catch (final Exception ignored) {
                    }
                    throw e;
                }
            }
            if (originalState == State.UP)
                setStateInternal(tunnel, originalConfig == null ? config : originalConfig, State.DOWN);
            try {
                setStateInternal(tunnel, config, State.UP);
            } catch (final Exception e) {
                try {
                    if (originalState == State.UP && originalConfig != null) {
                        setStateInternal(tunnel, originalConfig, State.UP);
                    }
                    if (!multipleTunnels && originalState == State.DOWN) {
                        for (final Map.Entry<Tunnel, Config> entry : runningConfigsSnapshot.entrySet()) {
                            setStateInternal(entry.getKey(), entry.getValue(), State.UP);
                        }
                    }
                } catch (final Exception ignored) {
                }
                throw e;
            }
        } else if (state == State.DOWN) {
            setStateInternal(tunnel, originalConfig == null ? config : originalConfig, State.DOWN);
        }
        return state;
    }

    private void setStateInternal(final Tunnel tunnel, @Nullable final Config config, final State state) throws Exception {
        Log.i(TAG, "Bringing tunnel " + tunnel.getName() + ' ' + state);

        Objects.requireNonNull(config, "Trying to set state up with a null config");

        final File tempFile = new File(localTemporaryDir, tunnel.getName() + ".conf");
        try (final FileOutputStream stream = new FileOutputStream(tempFile, false)) {
            stream.write(config.toAwgQuickString().getBytes(StandardCharsets.UTF_8));
        }
        String command = String.format("awg-quick %s '%s'",
                state.toString().toLowerCase(Locale.ENGLISH), tempFile.getAbsolutePath());
        if (state == State.UP)
            command = "cat /sys/module/amneziawg/version && " + command;
        final int result = rootShell.run(null, command);
        // noinspection ResultOfMethodCallIgnored
        tempFile.delete();
        if (result != 0)
            throw new BackendException(Reason.AWG_QUICK_CONFIG_ERROR_CODE, result);

        if (state == State.UP)
            runningConfigs.put(tunnel, config);
        else
            runningConfigs.remove(tunnel);

        tunnel.onStateChange(state);
    }
}
