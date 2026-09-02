package com.example.humsafar.utils

import com.example.humsafar.BuildConfig
import java.net.URI
import java.net.URLDecoder

object QrCodeParser {

    /**
     * Extracts and normalizes a node identifier from either:
     * 1. A complete HTTPS URL: "https://humsafar.vercel.app/node/12345" or "https://humsafar.vercel.app/node/NODE_1"
     * 2. An old-style plain text ID: "12345", "NODE_1", or "IIITS-0-KING"
     *
     * Handles URL decoding, whitespace, query parameters, URL fragments,
     * trailing slashes, and protects against path traversal.
     *
     * @param rawInput Raw scanned barcode value or deep link URI string.
     * @return Normalized identifier (e.g. "12345" or "NODE_1"), or null if invalid/empty.
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
}
