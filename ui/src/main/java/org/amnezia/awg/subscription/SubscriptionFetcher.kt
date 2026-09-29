/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.subscription

import android.util.Base64
import android.util.Log
import org.amnezia.awg.config.Config
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONException

/** A single configuration as advertised by a subscription, before it is turned into a tunnel. */
data class FetchedTunnel(val name: String, val config: Config)

/** Raised when a subscription cannot be fetched or its payload cannot be understood. */
class SubscriptionFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Downloads subscription payloads and turns them into [Config] objects.
 *
 * Four payload shapes are recognised, in this order:
 *  1. a ZIP archive containing `.conf` files (the format the file importer also accepts),
 *  2. a JSON array of `{"name": ..., "config": ...}` objects,
 *  3. one or more plain AmneziaWG/WireGuard configurations, and
 *  4. any of the above Base64-encoded, which is what most providers actually serve.
 */
object SubscriptionFetcher {
    private const val MAX_REDIRECTS = 5
    private const val MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * Fetches [url] and returns every configuration it advertises.
     *
     * @throws SubscriptionFetchException on any network or parsing failure.
     */
    fun fetch(url: String): List<FetchedTunnel> {
        val normalized = url.trim()
        if (normalized.isEmpty()) throw SubscriptionFetchException("Subscription URL is empty")
        val uri = try {
            URI(normalized)
        } catch (e: Exception) {
            throw SubscriptionFetchException("Malformed subscription URL", e)
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw SubscriptionFetchException("Subscription URL must be http or https")
        }

        val body = download(normalized)
        val tunnels = parse(body)
        if (tunnels.isEmpty()) throw SubscriptionFetchException("Subscription contained no configurations")
        return tunnels
    }

    private fun download(url: String): ByteArray {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val connection = (URI(current).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept", "*/*")
                // Subscription providers use this to serve a device-specific config set.
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw SubscriptionFetchException("Redirect without a Location header (HTTP $status)")
                    current = URI(current).resolve(location).toString()
                    return@repeat
                }
                if (status !in 200..299) {
                    throw SubscriptionFetchException("Server returned HTTP $status")
                }
                val declaredLength = connection.contentLength
                if (declaredLength > MAX_PAYLOAD_BYTES) {
                    throw SubscriptionFetchException("Subscription payload is too large")
                }
                return connection.inputStream.use { readLimited(it) }
            } catch (e: SubscriptionFetchException) {
                throw e
            } catch (e: Exception) {
                throw SubscriptionFetchException("Could not reach the subscription server", e)
            } finally {
                connection.disconnect()
            }
        }
        throw SubscriptionFetchException("Too many redirects")
    }

    private fun readLimited(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_PAYLOAD_BYTES) throw SubscriptionFetchException("Subscription payload is too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun parse(body: ByteArray): List<FetchedTunnel> {
        // 1. ZIP archive.
        if (body.size > 3 && body[0] == 'P'.code.toByte() && body[1] == 'K'.code.toByte()) {
            return parseZip(body)
        }

        val text = String(body, StandardCharsets.UTF_8).trim()

        // 2. JSON array of named configs.
        if (looksLikeJsonArray(text)) {
            return parseJson(text)
        }

        // 3. One or more plain configurations. Checked after JSON because a raw config document
        //    also begins with '[', and Android's JSONArray would happily parse "[Interface]" as
        //    an array holding the unquoted string "Interface", silently yielding zero configs.
        if (text.contains("[Interface]")) {
            return parseConf(text, fallbackName = null)
        }

        // 4. Base64-encoded payload; unwrap and re-dispatch.
        val decoded = try {
            Base64.decode(text, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw SubscriptionFetchException("Unrecognised subscription format", e)
        }
        if (decoded.isEmpty()) throw SubscriptionFetchException("Subscription payload was empty")
        if (decoded.size > 3 && decoded[0] == 'P'.code.toByte() && decoded[1] == 'K'.code.toByte()) {
            return parseZip(decoded)
        }
        val decodedText = String(decoded, StandardCharsets.UTF_8).trim()
        if (looksLikeJsonArray(decodedText)) return parseJson(decodedText)
        if (decodedText.contains("[Interface]")) return parseConf(decodedText, fallbackName = null)
        throw SubscriptionFetchException("Unrecognised subscription format")
    }

    /**
     * Distinguishes a JSON array of configurations from a raw config document.
     *
     * Both begin with '[', so a bare `startsWith("[")` test is not enough: every plain config
     * starts with "[Interface]". A configuration array holds objects (or quoted strings), so the
     * first meaningful character after the opening bracket decides it.
     */
    private fun looksLikeJsonArray(text: String): Boolean {
        var i = 0
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '[') return false
        i++
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length) return false
        return text[i] == '{' || text[i] == '"'
    }

    private fun parseZip(body: ByteArray): List<FetchedTunnel> {
        val result = ArrayList<FetchedTunnel>()
        ZipInputStream(ByteArrayInputStream(body)).use { zip ->
            val reader = BufferedReader(zip.reader(StandardCharsets.UTF_8))
            var entry: ZipEntry? = null
            while (true) {
                entry = zip.nextEntry ?: break
                val rawName = entry.name
                if (rawName.endsWith("/")) continue
                val baseName = rawName.substringAfterLast('/')
                if (!baseName.endsWith(".conf", ignoreCase = true)) continue
                val stem = baseName.substring(0, baseName.length - ".conf".length)
                try {
                    result.addAll(parseConf(reader.readText(), fallbackName = stem))
                } catch (e: Throwable) {
                    Log.w(TAG, "Skipping unreadable zip entry $rawName", e)
                }
            }
        }
        return result
    }

    private fun parseJson(text: String): List<FetchedTunnel> {
        val array = try {
            JSONArray(text)
        } catch (e: JSONException) {
            throw SubscriptionFetchException("Subscription is not valid JSON", e)
        }
        val result = ArrayList<FetchedTunnel>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val configText = item.optString("config").takeIf { it.isNotBlank() } ?: continue
            val name = item.optString("name").takeIf { it.isNotBlank() }
            try {
                result.addAll(parseConf(configText, fallbackName = name))
            } catch (e: Throwable) {
                Log.w(TAG, "Skipping unreadable JSON entry at index $i", e)
            }
        }
        return result
    }

    /**
     * Parses [text], which may hold a single configuration or several concatenated ones.
     *
     * A raw configuration document carries no name of its own, so any produced tunnel is named
     * positionally; see [positionalName].
     */
    private fun parseConf(text: String, fallbackName: String?): List<FetchedTunnel> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Config.parse deliberately combines *every* [Interface] section in a document into a
        // single interface and hangs all [Peer] sections off it. A document holding three
        // configurations would therefore parse "successfully" into one tunnel, with each key
        // overwritten by the last section that mentioned it. That is silent corruption rather
        // than a multi-peer config, so the document is split on [Interface] boundaries first
        // whenever it contains more than one.
        val chunks = splitConcatenated(trimmed)
        if (chunks.size > 1) {
            val result = ArrayList<FetchedTunnel>(chunks.size)
            for (chunk in chunks) {
                try {
                    result.add(FetchedTunnel(name = fallbackName ?: positionalName(result.size), config = parseSingle(chunk)))
                } catch (e: Throwable) {
                    Log.w(TAG, "Skipping unparseable configuration block", e)
                }
            }
            return result
        }

        // Exactly one [Interface]: a single configuration, which may legitimately have peers.
        try {
            return listOf(FetchedTunnel(name = fallbackName ?: positionalName(0), config = parseSingle(trimmed)))
        } catch (e: Throwable) {
            throw SubscriptionFetchException("Could not parse configuration", e)
        }
    }

    private fun parseSingle(text: String): Config = Config.parse(
        ByteArrayInputStream(text.trim().toByteArray(StandardCharsets.UTF_8))
    )

    /**
     * Splits a document that failed to parse as a whole into blocks, each of which begins at an
     * `[Interface]` header. Returns a single-element list if there is only one such header, in
     * which case the original failure is the meaningful one.
     */
    private fun splitConcatenated(text: String): List<String> {
        val lines = text.lines()
        val starts = lines.indices.filter { lines[it].trim().equals("[Interface]", ignoreCase = true) }
        if (starts.size <= 1) return listOf(text)
        return starts.mapIndexed { index, start ->
            val end = if (index + 1 < starts.size) starts[index + 1] else lines.size
            lines.subList(start, end).joinToString("\n").trim()
        }
    }

    /**
     * Names a configuration document.
     *
     * AmneziaWG's config format has no name attribute — `[Peer]` accepts only AllowedIPs,
     * Endpoint, PersistentKeepalive, PreSharedKey and PublicKey, and any other attribute is a hard
     * parse error. A `.conf` file's name is its filename, so a name can only come from the
     * transport that supplied the config: the JSON `name` field or the ZIP entry stem. A raw config
     * document therefore has no name of its own and is given a positional one.
     */
    private fun positionalName(index: Int): String =
        if (index <= 0) DEFAULT_NAME else "$DEFAULT_NAME-${index + 1}"

    private const val TAG = "AmneziaWG/SubscriptionFetcher"
    private const val USER_AGENT = "AmneziaWG-Android"
    private const val DEFAULT_NAME = "subscription"
}
