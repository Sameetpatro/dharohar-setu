// app/src/main/java/com/example/humsafar/network/ApiModels.kt
// UPDATED: Node.images = node_images rows; SiteDetail.introVideoUrl from heritage_sites

package com.example.humsafar.network

import com.google.gson.annotations.SerializedName

// ── /sites/nearby ─────────────────────────────────────────────────────────────

data class NearbySite(
    @SerializedName("id")              val id: Int = 0,
    @SerializedName("name")            val name: String = "",
    @SerializedName("latitude")        val latitude: Double = 0.0,
    @SerializedName("longitude")       val longitude: Double = 0.0,
    @SerializedName("distance_meters") val distanceMeters: Double = 0.0,
    @SerializedName("inside_geofence") val insideGeofence: Boolean = false
)

// ── /sites/{site_id} ──────────────────────────────────────────────────────────

data class SiteImage(
    @SerializedName("id")            val id: Int           = 0,
    @SerializedName("image_url")     val imageUrl: String  = "",
    @SerializedName("display_order") val displayOrder: Int = 0
)

/** Row from node_images table */
data class NodeImage(
    @SerializedName("id")            val id: Int           = 0,
    @SerializedName("image_url")     val imageUrl: String  = "",
    @SerializedName("display_order") val displayOrder: Int = 0
)

data class Node(
    @SerializedName("id")             val id: Int                  = 0,
    @SerializedName("name")           val name: String             = "",
    @SerializedName("latitude")       val latitude: Double         = 0.0,
    @SerializedName("longitude")      val longitude: Double        = 0.0,
    @SerializedName("sequence_order") val sequenceOrder: Int       = 0,
    @SerializedName("is_king")        val isKing: Boolean          = false,
    @SerializedName("description")    val description: String?     = null,
    @SerializedName("video_url")      val videoUrl: String?        = null,
    @SerializedName("image_url")      val imageUrl: String?        = null,
    @SerializedName("images")         val images: List<NodeImage>  = emptyList()
)

data class SiteDetail(
    @SerializedName("id")                      val id: Int                   = 0,
    @SerializedName("name")                    val name: String              = "",
    @SerializedName("latitude")                val latitude: Double          = 0.0,
    @SerializedName("longitude")               val longitude: Double         = 0.0,
    @SerializedName("geofence_radius_meters")  val geofenceRadiusMeters: Int = 0,
    @SerializedName("summary")                 val summary: String?          = null,
    @SerializedName("history")                 val history: String?          = null,
    @SerializedName("fun_facts")               val funFacts: String?         = null,
    @SerializedName("helpline_number")         val helplineNumber: String?   = null,
    @SerializedName("static_map_url")          val staticMapUrl: String?     = null,
    @SerializedName("intro_video_url")         val introVideoUrl: String?    = null,
    @SerializedName("rating")                  val rating: Double            = 0.0,
    @SerializedName("upvotes")                 val upvotes: Int              = 0,
    @SerializedName("images")                  val images: List<SiteImage>   = emptyList(),
    @SerializedName("nodes")                   val nodes: List<Node>         = emptyList()
)

// ── /sites/scan/{qr_value} ────────────────────────────────────────────────────

data class QrScanResult(
    @SerializedName("status")         val status: String  = "",
    @SerializedName("site_id")        val siteId: Int?    = null,
    @SerializedName("node_id")        val nodeId: Int?    = null,
    @SerializedName("sequence_order") val sequenceOrder: Int? = null,
    @SerializedName("node_name")      val nodeName: String?   = null
) {
    val isValid:   Boolean get() = status == "valid"
    val isKingNode: Boolean get() = sequenceOrder == 0
}

// ── Chat ──────────────────────────────────────────────────────────────────────

data class ChatMessage(
    @SerializedName("role")    val role: String    = "",
    @SerializedName("content") val content: String = ""
)

data class ChatRequest(
    @SerializedName("site_id")  val siteId: Int? = null,
    @SerializedName("node_id")  val nodeId: Int? = null,
    @SerializedName("qr_value") val qrValue: String? = null,
    @SerializedName("message") val message: String,
    @SerializedName("history") val history: List<ChatMessage> = emptyList()
)

data class ChatResponse(
    @SerializedName("reply") val reply: String = ""
)

// ── Trip ──────────────────────────────────────────────────────────────────────

data class TripStartResponse(
    @SerializedName("message") val message: String = "",
    @SerializedName("trip_id") val tripId: Int     = 0
)

data class TripEndResponse(
    @SerializedName("message") val message: String = ""
)