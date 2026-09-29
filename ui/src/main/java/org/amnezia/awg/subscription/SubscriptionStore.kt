/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.subscription

import android.util.Log
import org.amnezia.awg.Application
import org.amnezia.awg.model.Subscription
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Persists the set of [Subscription]s in the application's preferences `DataStore`.
 *
 * The whole list is stored as a single JSON document under one key. Mutations are performed
 * inside a single `edit` transaction so that a read-modify-write cycle can never interleave with
 * another writer and lose an update.
 */
object SubscriptionStore {
    private val SUBSCRIPTIONS = stringPreferencesKey("subscriptions")

    val subscriptions: Flow<List<Subscription>>
        get() = Application.getPreferencesDataStore().data.map { decode(it[SUBSCRIPTIONS]) }

    suspend fun current(): List<Subscription> = subscriptions.first()

    suspend fun get(id: String): Subscription? = current().firstOrNull { it.id == id }

    /** Inserts [subscription], or replaces the existing entry with the same [Subscription.id]. */
    suspend fun upsert(subscription: Subscription) = mutate { list ->
        val index = list.indexOfFirst { it.id == subscription.id }
        if (index >= 0) list[index] = subscription else list.add(subscription)
    }

    suspend fun remove(id: String) = mutate { list ->
        list.removeAll { it.id == id }
    }

    /**
     * Applies [transform] to the subscription with the given [id], atomically. If no such
     * subscription exists the block is not run and no write is performed.
     */
    suspend fun update(id: String, transform: (Subscription) -> Subscription) = mutate { list ->
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) list[index] = transform(list[index])
    }

    private suspend inline fun mutate(crossinline block: (MutableList<Subscription>) -> Unit) {
        Application.getPreferencesDataStore().edit { prefs ->
            val list = decode(prefs[SUBSCRIPTIONS]).toMutableList()
            block(list)
            if (list.isEmpty()) prefs.remove(SUBSCRIPTIONS) else prefs[SUBSCRIPTIONS] = encode(list)
        }
    }

    private fun encode(subscriptions: List<Subscription>): String {
        val array = JSONArray()
        for (subscription in subscriptions) {
            array.put(
                JSONObject()
                    .put(KEY_ID, subscription.id)
                    .put(KEY_NAME, subscription.name)
                    .put(KEY_URL, subscription.url)
                    .put(KEY_LAST_SYNCED, subscription.lastSynced)
                    .put(KEY_MANAGED, JSONArray(subscription.managedTunnels.toList()))
                    .put(KEY_ENABLED, subscription.enabled)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<Subscription> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<Subscription>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString(KEY_ID).takeIf { it.isNotEmpty() } ?: continue
                val name = item.optString(KEY_NAME)
                val url = item.optString(KEY_URL)
                if (url.isEmpty()) continue
                val managedArray = item.optJSONArray(KEY_MANAGED)
                val managed = LinkedHashSet<String>()
                if (managedArray != null) {
                    for (j in 0 until managedArray.length()) {
                        managedArray.optString(j).takeIf { it.isNotEmpty() }?.let { managed.add(it) }
                    }
                }
                result.add(
                    Subscription(
                        id = id,
                        name = name.ifEmpty { url },
                        url = url,
                        lastSynced = item.optLong(KEY_LAST_SYNCED, 0L),
                        managedTunnels = managed,
                        enabled = item.optBoolean(KEY_ENABLED, true)
                    )
                )
            }
            result
        } catch (e: JSONException) {
            // A corrupt store must not brick the app; start over from an empty list.
            Log.e(TAG, "Failed to decode stored subscriptions", e)
            emptyList()
        }
    }

    private const val TAG = "AmneziaWG/SubscriptionStore"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_URL = "url"
    private const val KEY_LAST_SYNCED = "lastSynced"
    private const val KEY_MANAGED = "managedTunnels"
    private const val KEY_ENABLED = "enabled"
}
