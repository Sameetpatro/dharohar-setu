package com.example.humsafar.navigation

import android.content.Intent
import android.util.Log
import com.example.humsafar.utils.QrCodeParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeepLinkTarget(
    val nodeIdentifier: String,
    val timestamp: Long = System.currentTimeMillis()
)

object DeepLinkHandler {

    private const val TAG = "DeepLinkHandler"

    private val _pendingDeepLink = MutableStateFlow<DeepLinkTarget?>(null)
    val pendingDeepLink: StateFlow<DeepLinkTarget?> = _pendingDeepLink.asStateFlow()

    fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val rawUriString = uri.toString()
        Log.d(TAG, "Checking intent URI for deep link: $rawUriString")

        // Custom scheme: humsafar://node/{NODE_ID}
        val nodeIdentifier = if (uri.scheme.equals("humsafar", ignoreCase = true) &&
            uri.host.equals("node", ignoreCase = true)
        ) {
            uri.pathSegments?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        } else {
            // HTTPS App Link or standard deep link
            QrCodeParser.extractNodeIdentifier(rawUriString)
        }

        if (!nodeIdentifier.isNullOrBlank()) {
            Log.i(TAG, "Discovered deep link targeting identifier: $nodeIdentifier")
            _pendingDeepLink.value = DeepLinkTarget(nodeIdentifier)
        }
    }

    fun triggerNode(nodeIdentifier: String) {
        val normalized = QrCodeParser.extractNodeIdentifier(nodeIdentifier) ?: return
        _pendingDeepLink.value = DeepLinkTarget(normalized)
    }

    fun consume() {
        _pendingDeepLink.value = null
    }
}
