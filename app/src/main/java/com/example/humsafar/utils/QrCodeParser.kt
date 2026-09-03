package com.example.humsafar.utils

import com.example.humsafar.BuildConfig
import java.net.URI
import java.net.URLDecoder

object QrCodeParser {

    const val BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    /**
     * Extracts and normalizes a node identifier from either:
     * 1. A Base62 hashed ID (e.g. "Q0v", "Q0w", "Q10", counter from 100001)
     * 2. A complete HTTPS URL: "https://dharohar-setu.onrender.com/node/Q0v" or "https://humsafar.vercel.app/node/12345"
     * 3. A legacy plain text ID: "IIITS-0-KING", "QTB-0-KING", "NODE_1"
     *
     * Handles URL decoding, whitespace, query parameters, URL fragments,
     * trailing slashes, and protects against path traversal.
     *
     * @param rawInput Raw scanned barcode value or deep link URI string.
     * @return Normalized identifier (e.g. "Q0v" or "IIITS-0-KING"), or null if invalid/empty.
     */
    fun extractNodeIdentifier(rawInput: String?): String? {
        if (rawInput.isNullOrBlank()) return null

        val trimmed = rawInput.trim()

        val candidate = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.contains("/node/", ignoreCase = true) ||
            trimmed.startsWith("/node", ignoreCase = true)
        ) {
            extractFromUrl(trimmed)
        } else {
            trimmed
        }

        if (candidate.isNullOrBlank()) return null

        val cleaned = candidate.trim()

        // Security / validation: reject path traversal or malicious injection strings
        if (cleaned.contains("..") || cleaned.contains("/") || cleaned.contains("\\")) {
            return null
        }

        return cleaned
    }

    private fun extractFromUrl(urlStr: String): String? {
        return try {
            val nodeMarker = "/node/"
            val nodeIndex = urlStr.indexOf(nodeMarker, ignoreCase = true)

            val rawSegment = if (nodeIndex != -1) {
                urlStr.substring(nodeIndex + nodeMarker.length)
            } else {
                val parsed = URI.create(urlStr)
                val path = parsed.path ?: return null
                if (path.startsWith("/node", ignoreCase = true)) {
                    path.removePrefix("/node").trimStart('/')
                } else {
                    path.trimStart('/')
                }
            }

            // Strip trailing slashes, query parameters, fragments
            val cleanSegment = rawSegment
                .split("/", "?", "#")
                .firstOrNull { it.isNotBlank() } ?: return null

            URLDecoder.decode(cleanSegment, "UTF-8").trim()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if the given raw input is an HTTPS node deep link URL.
     */
    fun isNodeUrl(rawInput: String?): Boolean {
        if (rawInput.isNullOrBlank()) return false
        val trimmed = rawInput.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)) &&
                (trimmed.contains("/node/", ignoreCase = true) ||
                 trimmed.contains("/node", ignoreCase = true))
    }

    /**
     * Constructs a full public QR URL for a given node identifier.
     */
    fun buildNodeQrUrl(
        nodeIdentifier: String,
        baseUrl: String = BuildConfig.PUBLIC_NODE_URL
    ): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanId = nodeIdentifier.trim()
        return "$cleanBase/$cleanId"
    }

    /**
     * Encodes a non-negative counter value into a Base62 alphanumeric string.
     * E.g. 100001 -> "Q0v"
     */
    fun encodeBase62(num: Long): String {
        require(num >= 0) { "Base62 number must be non-negative" }
        if (num == 0L) return BASE62_ALPHABET[0].toString()
        val sb = StringBuilder()
        var n = num
        while (n > 0) {
            sb.append(BASE62_ALPHABET[(n % 62).toInt()])
            n /= 62
        }
        return sb.reverse().toString()
    }

    /**
     * Decodes a Base62 string back into its numeric counter value.
     * E.g. "Q0v" -> 100001
     * Returns null if string contains characters outside [0-9A-Za-z].
     */
    fun decodeBase62(code: String?): Long? {
        if (code.isNullOrBlank()) return null
        var result = 0L
        for (ch in code.trim()) {
            val idx = BASE62_ALPHABET.indexOf(ch)
            if (idx == -1) return null
            result = result * 62 + idx
        }
        return result
    }

    /**
     * Checks if a string is a valid Base62 alphanumeric identifier.
     */
    fun isBase62(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        return candidate.all { BASE62_ALPHABET.contains(it) }
    }
}

