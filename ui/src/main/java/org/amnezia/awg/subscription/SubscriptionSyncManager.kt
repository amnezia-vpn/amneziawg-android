/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.subscription

import android.util.Log
import org.amnezia.awg.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.amnezia.awg.Application
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.model.Subscription
import org.amnezia.awg.util.ErrorMessages

/** Outcome of a single subscription's sync. */
data class SubscriptionResult(
    val subscriptionId: String,
    val name: String,
    val added: List<String> = emptyList(),
    val updated: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val unchanged: Int = 0,
    val error: String? = null
) {
    val totalChanges: Int get() = added.size + updated.size + removed.size
    val succeeded: Boolean get() = error == null
}

/** Aggregate outcome of a sync run across every subscription that took part. */
data class SyncSummary(val results: List<SubscriptionResult>) {
    val added: Int get() = results.sumOf { it.added.size }
    val updated: Int get() = results.sumOf { it.updated.size }
    val removed: Int get() = results.sumOf { it.removed.size }
    val unchanged: Int get() = results.sumOf { it.unchanged }
    val failures: List<SubscriptionResult> get() = results.filterNot { it.succeeded }
    val isEmpty: Boolean get() = results.isEmpty()
}

/** Observable sync state, used to drive the pull-to-refresh spinner and the dialog. */
sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Finished(val summary: SyncSummary) : SyncState
    data class Failed(val error: String) : SyncState
}

/**
 * Reconciles the tunnels owned by each [Subscription] with the configurations currently being
 * advertised by its URL.
 *
 * The engine only ever touches tunnels listed in a subscription's `managedTunnels`. A tunnel the
 * user created by hand is never created, modified or deleted on a subscription's behalf, even if
 * a subscription advertises a configuration of the same name.
 */
object SubscriptionSyncManager {
    private const val TAG = "AmneziaWG/SubscriptionSyncManager"

    /** Minimum interval between automatic syncs. */
    const val AUTO_SYNC_COOLDOWN_MS = 15 * 60 * 1000L

    private val mutex = Mutex()
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /**
     * Syncs every enabled subscription, ignoring the cooldown.
     * @return a summary, never throwing for per-subscription failures.
     */
    suspend fun syncAll(): SyncSummary = syncInternal()

    /**
     * Syncs a single subscription, ignoring the cooldown. Returns null if it no longer exists.
     */
    suspend fun syncOne(subscriptionId: String): SyncSummary? {
        val subscription = SubscriptionStore.get(subscriptionId) ?: return null
        return syncInternal(only = listOf(subscription))
    }

    /**
     * Syncs every enabled subscription whose last successful sync is older than
     * [AUTO_SYNC_COOLDOWN_MS]. Does nothing if a sync is already in flight.
     *
     * @return the summary of the run, or null if the cooldown meant no work was due.
     */
    suspend fun syncIfDue(): SyncSummary? {
        val now = System.currentTimeMillis()
        val due = SubscriptionStore.current()
            .filter { it.enabled }
            .filter { now - it.lastSynced >= AUTO_SYNC_COOLDOWN_MS }
        if (due.isEmpty()) return null
        return syncInternal(only = due)
    }

    private suspend fun syncInternal(only: List<Subscription>? = null): SyncSummary {
        if (mutex.isLocked) {
            Log.d(TAG, "Sync already in progress; skipping request")
            return SyncSummary(emptyList())
        }
        return mutex.withLock {
            _state.value = SyncState.Syncing
            try {
                val subscriptions = only ?: SubscriptionStore.current().filter { it.enabled }
                if (subscriptions.isEmpty()) {
                    val summary = SyncSummary(emptyList())
                    _state.value = SyncState.Finished(summary)
                    return@withLock summary
                }
                val results = ArrayList<SubscriptionResult>(subscriptions.size)
                for (subscription in subscriptions) {
                    results.add(syncSubscription(subscription))
                }
                val summary = SyncSummary(results)
                _state.value = SyncState.Finished(summary)
                summary
            } catch (e: Throwable) {
                Log.e(TAG, "Subscription sync failed", e)
                val message = ErrorMessages[e]
                _state.value = SyncState.Failed(message)
                throw e
            }
        }
    }

    private suspend fun syncSubscription(subscription: Subscription): SubscriptionResult {
        return try {
            withContext(Dispatchers.IO) {
                val fetched = SubscriptionFetcher.fetch(subscription.url)
                apply(subscription, fetched)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Sync of '${subscription.name}' failed", e)
            SubscriptionResult(
                subscriptionId = subscription.id,
                name = subscription.name,
                error = ErrorMessages[e]
            )
        }
    }

    /**
     * Applies [fetched] to the local tunnel list. Must run on [Dispatchers.IO]; the TunnelManager
     * internally re-dispatches to the main thread where required.
     */
    private suspend fun apply(subscription: Subscription, fetched: List<FetchedTunnel>): SubscriptionResult {
        val tunnelManager = Application.getTunnelManager()
        val tunnels = tunnelManager.getTunnels()

        // Tunnels this subscription owns. Anything else is off limits.
        val owned = tunnels.filter { it.name in subscription.managedTunnels }
        val ownedByName = owned.associateBy { it.name }

        // Names held by tunnels we must not touch, so a subscription never overwrites a tunnel the
        // user created by hand. Names this subscription already owns are deliberately absent: those
        // are the ones we want to match against and update. Names claimed during the run below are
        // added as we go, so two profiles in one payload that sanitise alike get distinct tunnels.
        val taken = tunnels.filter { it.name !in subscription.managedTunnels }.mapTo(HashSet()) { it.name }

        val added = ArrayList<String>()
        val updated = ArrayList<String>()
        var unchanged = 0
        val newManaged = LinkedHashSet<String>()
        val errors = ArrayList<Throwable>()

        for (entry in fetched) {
            // `taken` grows as names are claimed, so two profiles in the same payload that
            // sanitise to the same name get distinct tunnels instead of colliding.
            val name = uniqueName(sanitizeName(entry.name), taken)
            val existing = ownedByName[name]
            if (existing == null) {
                // Either brand new, or previously owned but the user renamed/deleted it. The
                // foreign-name check guarantees we never clobber a tunnel outside our control.
                try {
                    tunnelManager.create(name, entry.config)
                    taken.add(name)
                    added.add(name)
                    newManaged.add(name)
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not create tunnel $name", e)
                    errors.add(e)
                }
                continue
            }
            taken.add(name)
            newManaged.add(name)
            try {
                // Compared as canonical serialisations, not with Config.equals and not as raw text.
                // Config.equals is unusable here because KeyPair has no value equality, so two
                // separately parsed copies of the same config are never equal. Comparing
                // toString() is stable across reformatting and key reordering by the provider,
                // while still detecting a genuine content change.
                val current = existing.getConfigAsync()
                // Compared via toAwgQuickString(), the full serialisation that is also what gets
                // written to the .conf file. Two other options are traps here: Config.equals is
                // always false for two separately parsed copies because KeyPair has no value
                // equality, and Config.toString() is only a summary -- "(Config <interface>
                // (N peers))" -- which cannot see a changed endpoint or key at all.
                if (current.toAwgQuickString() == entry.config.toAwgQuickString()) {
                    unchanged++
                } else {
                    tunnelManager.setTunnelConfig(existing, entry.config)
                    updated.add(name)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not update tunnel $name", e)
                errors.add(e)
            }
        }

        // Remove tunnels this subscription owns that the provider no longer lists. A failed fetch
        // never reaches here, so a transient network error cannot delete the user's tunnels.
        val removed = ArrayList<String>()
        for (tunnel in owned) {
            if (tunnel.name in newManaged) continue
            try {
                tunnelManager.delete(tunnel)
                removed.add(tunnel.name)
            } catch (e: Throwable) {
                Log.w(TAG, "Could not delete tunnel ${tunnel.name}", e)
                errors.add(e)
                // Still own it; try again next time.
                newManaged.add(tunnel.name)
            }
        }

        SubscriptionStore.update(subscription.id) {
            it.copy(managedTunnels = newManaged, lastSynced = System.currentTimeMillis())
        }

        val error = when {
            errors.isEmpty() -> null
            errors.size == 1 -> ErrorMessages[errors[0]]
            else -> "${errors.size} profiles could not be applied"
        }
        Log.d(
            TAG,
            "Sync '${subscription.name}': ${added.size} added, ${updated.size} updated, " +
                "${removed.size} removed, $unchanged unchanged" +
                if (errors.isEmpty()) "" else ", ${errors.size} failed"
        )
        return SubscriptionResult(
            subscriptionId = subscription.id,
            name = subscription.name,
            added = added,
            updated = updated,
            removed = removed,
            unchanged = unchanged,
            error = error
        )
    }

    /**
     * Strips a provider-supplied name down to something the tunnel manager accepts: at most
     * [Tunnel.NAME_MAX_LENGTH] characters drawn from the characters it allows.
     */
    fun sanitizeName(raw: String): String {
        val cleaned = raw.trim()
            .map { if (Tunnel.NAME_PATTERN.matcher(it.toString()).matches()) it else '-' }
            .joinToString("")
            .trim('-')
            .take(Tunnel.NAME_MAX_LENGTH)
        if (cleaned.isEmpty()) return "subscription"
        return cleaned
    }

    /**
     * Returns [candidate], or the first `candidate-N` variant that is not already taken by a
     * tunnel outside this subscription's control.
     */
    private fun uniqueName(candidate: String, taken: Set<String>): String {
        if (candidate !in taken) return candidate
        for (suffix in 2..99) {
            val tail = "-$suffix"
            val trimmed = candidate.take((Tunnel.NAME_MAX_LENGTH - tail.length).coerceAtLeast(1))
            val variant = trimmed + tail
            if (variant !in taken) return variant
        }
        return candidate
    }

    /**
     * Removes a subscription and permanently deletes every tunnel it owned, so that nothing is
     * left stranded in the default space. The subscription record is dropped first, which means a
     * failure to delete a tunnel can never cause the sync engine to recreate it later.
     */
    suspend fun remove(subscriptionId: String) {
        val removed = SubscriptionStore.get(subscriptionId)
        SubscriptionStore.remove(subscriptionId)
        val subscription = removed ?: return
        val tunnelManager = Application.getTunnelManager()
        val doomed = tunnelManager.getTunnels().filter { it.name in subscription.managedTunnels }
        for (tunnel in doomed) {
            try {
                tunnelManager.delete(tunnel)
            } catch (e: Throwable) {
                Log.w(TAG, "Could not delete tunnel ${tunnel.name} while removing ${subscription.name}", e)
            }
        }
    }

    /** Adds a subscription; the first successful sync claims ownership of its tunnels. */
    suspend fun add(name: String, url: String) {
        SubscriptionStore.upsert(Subscription(name = name.trim(), url = url.trim()))
    }
}

/**
 * Renders a sync outcome as a human-readable message, or null when there is nothing worth
 * reporting (no subscriptions were involved).
 */
fun SyncSummary.toMessage(context: android.content.Context): String? {
    if (isEmpty) return null
    val failures = failures
    return when {
        failures.size == results.size ->
            context.getString(
                R.string.sync_failed,
                failures.joinToString("; ") { "${it.name}: ${it.error}" }
            )
        failures.isNotEmpty() ->
            context.getString(
                R.string.sync_partial_failure,
                failures.joinToString("; ") { "${it.name}: ${it.error}" }
            )
        added + updated + removed == 0 ->
            context.getString(R.string.sync_nothing_to_do)
        else ->
            context.getString(R.string.sync_summary, added, updated, removed)
    }
}
