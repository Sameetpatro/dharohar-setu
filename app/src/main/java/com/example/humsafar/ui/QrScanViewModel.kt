// app/src/main/java/com/example/humsafar/ui/QrScanViewModel.kt
// REWRITTEN — wired to FastAPI /sites/scan/{qr_value} and /trips/start

package com.example.humsafar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.humsafar.data.TripManager
import com.example.humsafar.models.QrScanResult
import com.example.humsafar.network.HumsafarClient
import com.example.humsafar.network.SiteDetail
import com.example.humsafar.utils.QrCodeParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class QrScanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<QrUiState>(QrUiState.Scanning)
    val uiState: StateFlow<QrUiState> = _uiState.asStateFlow()

    private val isProcessing = AtomicBoolean(false)
    private var lastScannedQr: String = ""

    // ── QR detected by camera ─────────────────────────────────────────────
    fun onQrDetected(rawQr: String) {
        if (!isProcessing.compareAndSet(false, true)) return
        val normalized = QrCodeParser.extractNodeIdentifier(rawQr)
        if (normalized == null) {
            _uiState.value = QrUiState.Error("Invalid QR code format")
            isProcessing.set(false)
            return
        }
        val qrValue = normalized
        lastScannedQr = qrValue

        viewModelScope.launch {
            _uiState.value = QrUiState.Validating

            try {
                val resp = HumsafarClient.api.scanQr(qrValue)
                if (!resp.isSuccessful || resp.body() == null) {
                    _uiState.value = QrUiState.Error("QR not recognised (${resp.code()})")
                    isProcessing.set(false)
                    return@launch
                }

                val result = resp.body()!!

                if (!result.isValid) {
                    _uiState.value = QrUiState.Error("Invalid QR code — not a Humsafar node")
                    isProcessing.set(false)
                    return@launch
                }

                // Fetch the full site so we have name + node list
                val siteResp = HumsafarClient.api.getSiteDetail(result.siteId!!)
                val site = if (siteResp.isSuccessful) siteResp.body() else null

                // Register the scan so it can complete an in-flight bonus challenge,
                // regardless of whether a trip is active.
                result.nodeId?.let { com.example.humsafar.data.ActiveSiteManager.onNodeScanned(it) }

                when {
                    // ── KING NODE ──────────────────────────────────────────
                    result.isKingNode -> {
                        if (TripManager.isActive()) {
                            // Already on a trip — just update current node and proceed
                            TripManager.updateCurrentNode(result.nodeId!!, result.nodeName ?: "Entry")
                            _uiState.value = QrUiState.Success(result, site)
                        } else {
                            // Start a new trip
                            startTrip(result, site)
                        }
                    }

                    // ── NORMAL NODE — trip active ──────────────────────────
                    TripManager.isActive() -> {
                        TripManager.updateCurrentNode(result.nodeId!!, result.nodeName ?: "")
                        _uiState.value = QrUiState.Success(result, site)
                    }

                    // ── NORMAL NODE — no trip yet → show popup ────────────
                    else -> {
                        _uiState.value = QrUiState.AskStartTrip(result, site)
                        isProcessing.set(false)
                    }
                }

            } catch (e: Exception) {
                _uiState.value = QrUiState.Error("Network error: ${e.message}")
                isProcessing.set(false)
            }
        }
    }

    // ── User tapped "Start Trip" in the popup ─────────────────────────────
    fun confirmStartTripFromNormalNode(result: QrScanResult, site: SiteDetail?) {
        viewModelScope.launch {
            // Backend now accepts any node QR for /trips/start, so we go through
            // the same flow as a king node — this guarantees a real server-side
            // trip_id, which is required for /trips/end and /reviews/submit.
            //
            // IMPORTANT: do NOT set state to `Validating` here. `Validating` shows
            // the camera preview, which would re-mount the AndroidView and call
            // bindToLifecycle() a second time on the same LifecycleOwner →
            // CameraX throws "No supported surface combination is found".
            // Use a dedicated `StartingTrip` state that hides the camera.
            _uiState.value = QrUiState.StartingTrip
            startTrip(result, site)
        }
    }

    // ── User dismissed the popup — don't start trip, still show node ──────
    fun dismissStartTrip(result: QrScanResult, site: SiteDetail?) {
        isProcessing.set(false)
        _uiState.value = QrUiState.Success(result, site)
    }

    fun resetScanner() {
        _uiState.value = QrUiState.Scanning
        isProcessing.set(false)
        lastScannedQr = ""
    }

    // ── Private helper: start trip via backend ────────────────────────────
    private suspend fun startTrip(result: QrScanResult, site: SiteDetail?) {
        try {
            // Make sure the firebase user has been mirrored to our backend
            // before calling /trips/start, otherwise it 404s with "User not found".
            val firebaseUser = com.example.humsafar.auth.AuthManager.currentUser.value
            if (firebaseUser != null) {
                com.example.humsafar.data.UserRepository.syncFirebaseUserSuspend(firebaseUser)
            }

            val trip = TripManager.current()
            val entryLat = trip.lastLat.takeIf { it != 0.0 }
            val entryLng = trip.lastLng.takeIf { it != 0.0 }

            val tripResp = HumsafarClient.api.startTrip(
                firebaseUid = TripManager.USER_ID,
                qrValue     = lastScannedQr,
                entryLat    = entryLat,
                entryLng    = entryLng
            )
            if (tripResp.isSuccessful && tripResp.body() != null) {
                val tripBody = tripResp.body()!!
                TripManager.activateTrip(
                    tripId   = tripBody.tripId,
                    siteId   = result.siteId ?: 0,
                    siteName = site?.name ?: "",
                    nodeId   = result.nodeId ?: 0,
                    nodeName = result.nodeName ?: "Entry"
                )
                _uiState.value = QrUiState.Success(result, site)
            } else {
                _uiState.value = QrUiState.Error("Failed to start trip (${tripResp.code()})")
                isProcessing.set(false)
            }
        } catch (e: Exception) {
            _uiState.value = QrUiState.Error("Network error: ${e.message}")
            isProcessing.set(false)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

sealed class QrUiState {
    data object Scanning         : QrUiState()
    data object Validating       : QrUiState()
    data object FetchingLocation : QrUiState()
    /** Tour-start handshake with /trips/start. Does NOT show the camera (camera
     *  is unbound at this point) so the AndroidView never remounts. */
    data object StartingTrip     : QrUiState()

    /** Normal node scanned but no active trip — ask user */
    data class AskStartTrip(
        val scanResult: QrScanResult,    // ← CHANGED: use network.QrScanResult
        val site:       SiteDetail?      // ← CHANGED: use network.SiteDetail
    ) : QrUiState()

    data class ShowingNodes(val nodes: List<com.example.humsafar.network.Node>) : QrUiState()

    data class Success(
        val scanResult: QrScanResult,    // ← CHANGED: use network.QrScanResult
        val site:       SiteDetail?      // ← CHANGED: use network.SiteDetail
    ) : QrUiState()

    data class Error(val message: String) : QrUiState()
}