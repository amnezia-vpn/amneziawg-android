/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.model

import java.util.UUID

/**
 * A remote source of AmneziaWG configurations that is periodically fetched and kept in sync
 * with the local tunnel list.
 *
 * Only the tunnels named in [managedTunnels] are ever created, modified or removed by the sync
 * engine. Tunnels the user created by any other means are invisible to a subscription and are
 * never touched by it.
 */
data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    /** Friendly name shown in the UI, e.g. "Provider A". */
    val name: String,
    /** HTTP(S) URL the configurations are fetched from. */
    val url: String,
    /** Wall-clock time of the last successful sync, or 0 if it has never succeeded. */
    val lastSynced: Long = 0L,
    /** Names of the tunnels this subscription is responsible for. */
    val managedTunnels: Set<String> = emptySet(),
    val enabled: Boolean = true
)
