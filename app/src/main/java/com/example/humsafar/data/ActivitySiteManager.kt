// app/src/main/java/com/example/humsafar/data/ActiveSiteManager.kt
//
// THIS IS THE SINGLE SOURCE OF TRUTH FOR THE CURRENT SITE.
//
// How it works:
//   1. GPS fires → LocationBasedSiteDetector queries GET /sites/nearby
//   2. Backend runs haversine SQL, returns inside_geofence=true for matching site
//   3. We store that site's DB id here as `activeSiteId`
//   4. Every screen (Chatbot, Voice, NodeDetail, QR scan) reads from here
//   5. Nothing is hardcoded — the id comes directly from your PostgreSQL primary key
//
// Every API call in the app uses this pattern:
//   val siteId = ActiveSiteManager.activeSiteId ?: return  // not inside any site
//   api.getSiteDetails(siteId)      // GET /sites/{site_id}
//   api.chat(siteId, nodeId, msg)   // POST /chat  body: { site_id, node_id, message }
//   api.voiceChat(siteId, nodeId)   // POST /voice-chat  form: site_id=, node_id=
//   api.startTrip(userId, qrValue)  // POST /trips/start — site_id comes from QR scan response

package com.example.humsafar.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ActiveSiteManager"

/**
 * Snapshot of the site the user is physically inside right now.
 * All fields come from the DB — nothing hardcoded.
 */
data class ActiveSite(
    val id:                    Int,      // heritage_sites.id — PRIMARY KEY
    val name:                  String,   // heritage_sites.name
    val latitude:              Double,   // heritage_sites.latitude
    val longitude:             Double,   // heritage_sites.longitude
    val geofenceRadiusMeters:  Int,      // heritage_sites.geofence_radius_meters
    val distanceMeters:        Double    // calculated by backend haversine
)

object ActiveSiteManager {

    // The site the user is INSIDE right now. Null = outside all geofences.
    private val _activeSite = MutableStateFlow<ActiveSite?>(null)
    val activeSite: StateFlow<ActiveSite?> = _activeSite.asStateFlow()

    // Convenience — most screens just need the Int id
    val activeSiteId: Int? get() = _activeSite.value?.id
    val activeSiteName: String get() = _activeSite.value?.name ?: ""

    // Optional: the node the user is at after scanning a QR code.
    // Reset to null whenever site changes or user exits geofence.
    private val _activeNodeId = MutableStateFlow<Int?>(null)
    val activeNodeId: StateFlow<Int?> = _activeNodeId.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Called by LocationBasedSiteDetector when backend confirms we're inside
    // ─────────────────────────────────────────────────────────────────────────

    fun onEnterSite(site: ActiveSite) {
        val previous = _activeSite.value
        if (previous?.id == site.id) {
            // Same site, just update distance
            _activeSite.value = site
            return
        }
        Log.i(TAG, "ENTERED site_id=${site.id} name='${site.name}'")
        _activeSite.value  = site
        _activeNodeId.value = null   // clear node from previous site
        BonusGameManager.onSiteEntered(site.id)
    }

    fun onExitAllSites() {
        val previous = _activeSite.value ?: return
        Log.i(TAG, "EXITED site_id=${previous.id} name='${previous.name}'")
        _activeSite.value   = null
        _activeNodeId.value = null
        // If the user was on a trip at this site, leaving the geofence triggers
        // the end-of-trip quiz.
        QuizTrigger.onSiteExit(previous.id)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Called by QrScanScreen when user scans a node QR code
    // The QR scan response from backend gives us: { site_id, node_id, ... }
    // We set the node here so voice/chat automatically uses node-level context
    // ─────────────────────────────────────────────────────────────────────────

    fun onNodeScanned(nodeId: Int, siteId: Int? = null, siteName: String? = null) {
        Log.i(TAG, "Node scanned: node_id=$nodeId for site_id=${siteId ?: activeSiteId}")
        _activeNodeId.value = nodeId
        if (siteId != null && (_activeSite.value == null || _activeSite.value?.id != siteId)) {
            _activeSite.value = ActiveSite(
                id = siteId,
                name = siteName ?: "Heritage Site",
                latitude = 0.0,
                longitude = 0.0,
                geofenceRadiusMeters = 500,
                distanceMeters = 0.0
            )
        }
        // A scan may complete an in-flight bonus "Bingo" challenge.
        BonusGameManager.onNodeScanned(nodeId)
    }

    fun clearNode() {
        _activeNodeId.value = null
    }
}