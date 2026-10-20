// app/src/main/java/com/example/humsafar/models/ApiModels.kt
// FIXED — added description + videoUrl to SiteNode so NodeDetailScreen compiles.
// Backend NodeResponse schema and sites.py router must also be updated (see schemas.py fix).

package com.example.humsafar.models

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────────────────────────
// /sites/nearby  →  GET /sites/nearby?lat=&lng=
// ─────────────────────────────────────────────────────────────────────────────
data class NearbySite(
    @SerializedName("id")              val id: Int = 0,
    @SerializedName("name")            val name: String = "",
    @SerializedName("latitude")        val latitude: Double = 0.0,
    @SerializedName("longitude")       val longitude: Double = 0.0,
    @SerializedName("distance_meters") val distanceMeters: Double = 0.0,
    @SerializedName("inside_geofence") val insideGeofence: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
// /sites/{site_id}  →  GET /sites/{site_id}
// ─────────────────────────────────────────────────────────────────────────────
data class SiteDetail(
    @SerializedName("id")               val id: Int = 0,
    @SerializedName("name")             val name: String = "",
    @SerializedName("latitude")         val latitude: Double = 0.0,
    @SerializedName("longitude")        val longitude: Double = 0.0,
    @SerializedName("geofence_radius_meters") val geofenceRadiusMeters: Int = 300,
    @SerializedName("summary")          val summary: String? = null,
    @SerializedName("history")          val history: String? = null,
    @SerializedName("fun_facts")        val funFacts: String? = null,
    @SerializedName("helpline_number")  val helplineNumber: String? = null,
    @SerializedName("static_map_url")   val staticMapUrl: String? = null,
    @SerializedName("intro_video_url")  val introVideoUrl: String? = null,
    @SerializedName("rating")           val rating: Double = 4.5,
    @SerializedName("upvotes")          val upvotes: Int = 0,
    @SerializedName("images")           val images: List<SiteImage> = emptyList(),
    @SerializedName("nodes")            val nodes: List<SiteNode> = emptyList()
)

data class SiteImage(
    @SerializedName("id")            val id: Int = 0,
    @SerializedName("image_url")     val imageUrl: String = "",
    @SerializedName("display_order") val displayOrder: Int = 0
)

// Matches backend NodeResponse — used by NodeDetailScreen (images, isKing, etc.)
data class SiteNode(
    @SerializedName("id")              val id: Int = 0,
    @SerializedName("name")            val name: String = "",
    @SerializedName("latitude")        val latitude: Double = 0.0,
    @SerializedName("longitude")       val longitude: Double = 0.0,
    @SerializedName("sequence_order")  val sequenceOrder: Int = 0,
    @SerializedName("is_king")         val isKing: Boolean = false,
    @SerializedName("description")    val description: String? = null,
    @SerializedName("video_url")       val videoUrl: String? = null,
    @SerializedName("image_url")       val imageUrl: String? = null,
    @SerializedName("images")          val images: List<NodeImage> = emptyList()
)

data class NodeImage(
    @SerializedName("id")            val id: Int = 0,
    @SerializedName("image_url")     val imageUrl: String = "",
    @SerializedName("display_order") val displayOrder: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// /sites/scan/{qr_value}  →  GET /sites/scan/{qr_value}
// ─────────────────────────────────────────────────────────────────────────────
data class QrScanResult(
    @SerializedName("status")         val status: String = "",
    @SerializedName("site_id")        val siteId: Int? = null,
    @SerializedName("node_id")        val nodeId: Int? = null,
    @SerializedName("sequence_order") val sequenceOrder: Int? = null,
    @SerializedName("node_name")      val nodeName: String? = null
) {
    val isValid: Boolean get() = status == "valid"
    val isKingNode: Boolean get() = sequenceOrder == 0
}

// ─────────────────────────────────────────────────────────────────────────────
// /trips/start  →  POST /trips/start?user_id=&qr_value=
// ─────────────────────────────────────────────────────────────────────────────
data class TripStartResponse(
    @SerializedName("message") val message: String = "",
    @SerializedName("trip_id") val tripId: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// /trips/end  →  POST /trips/end?trip_id=
// ─────────────────────────────────────────────────────────────────────────────
data class TripEndResponse(
    @SerializedName("message") val message: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// /chat/  →  POST /chat/
// firebase_uid/trip_id/lang_code are optional; when provided the backend
// persists the user message + assistant reply to user_chat_history. Calls
// without firebase_uid still work — chat must never fail on analytics writes.
// ─────────────────────────────────────────────────────────────────────────────
data class ChatRequest(
    @SerializedName("site_id")      val siteId: Int? = null,
    @SerializedName("node_id")      val nodeId: Int? = null,
    @SerializedName("qr_value")     val qrValue: String? = null,
    @SerializedName("message")      val message: String,
    @SerializedName("history")      val history: List<ChatHistoryItem> = emptyList(),
    @SerializedName("firebase_uid") val firebaseUid: String? = null,
    @SerializedName("trip_id")      val tripId: Int? = null,
    @SerializedName("lang_code")    val langCode: String? = null
)

data class ChatHistoryItem(
    @SerializedName("role")    val role: String,
    @SerializedName("content") val content: String
)

data class ChatResponse(
    @SerializedName("reply") val reply: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Local trip state (never serialized, lives in TripManager only)
// ─────────────────────────────────────────────────────────────────────────────
data class TripSnapshot(
    val tripId:          Int     = 0,
    val siteId:          Int     = 0,
    val siteName:        String  = "",
    val currentNodeId:   Int     = 0,
    val currentNodeName: String  = "",
    val isTripActive:    Boolean = false,
    val startedAtMs:     Long    = 0L,
    val lastLat:         Double  = 0.0,
    val lastLng:         Double  = 0.0,
    val visitedNodeIds:  List<Int> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Node detail (assembled client-side from SiteDetail + QrScanResult)
// ─────────────────────────────────────────────────────────────────────────────
data class NodeDetail(
    val id:            Int,
    val name:          String,
    val siteId:        Int,
    val siteName:      String,
    val sequenceOrder: Int,
    val description:   String?,
    val videoUrl:      String?,
    val images:        List<String>,
    val isKing:        Boolean
)

// ─────────────────────────────────────────────────────────────────────────────
// /sites/{site_id}/nodes  →  GET /sites/{site_id}/nodes (exact lat/lng for map)
// ─────────────────────────────────────────────────────────────────────────────
data class NodePositionResponse(
    @SerializedName("id")              val id: Int = 0,
    @SerializedName("name")            val name: String = "",
    @SerializedName("latitude")       val latitude: Double = 0.0,
    @SerializedName("longitude")      val longitude: Double = 0.0,
    @SerializedName("sequence_order") val sequenceOrder: Int = 0,
    @SerializedName("is_king")        val isKing: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
// /reviews/submit  →  POST /reviews/submit
// Backend ReviewSubmitBody expects "firebase_uid" — NOT "user_id".
// ─────────────────────────────────────────────────────────────────────────────
data class ReviewSubmitRequest(
    @SerializedName("trip_id")         val tripId: Int,
    @SerializedName("site_id")         val siteId: Int,
    @SerializedName("firebase_uid")    val firebaseUid: String,
    @SerializedName("star_rating")     val starRating: Int,
    @SerializedName("q1")              val q1: Int,
    @SerializedName("q2")              val q2: Int,
    @SerializedName("q3")              val q3: Int,
    @SerializedName("suggestion_text") val suggestionText: String? = null
)

data class ReviewSubmitResponse(
    @SerializedName("message")    val message: String = "",
    @SerializedName("review_id")  val reviewId: Int = 0,
    @SerializedName("new_rating") val newRating: Double = 0.0
)

// ─────────────────────────────────────────────────────────────────────────────
// /reviews/sites/rate  →  POST /reviews/sites/rate
// /reviews/nodes/rate  →  POST /reviews/nodes/rate
// ─────────────────────────────────────────────────────────────────────────────
data class SiteRatingRequest(
    @SerializedName("site_id")      val siteId: Int,
    @SerializedName("firebase_uid") val firebaseUid: String,
    @SerializedName("rating")       val rating: Int    // 1–5
)

data class NodeRatingRequest(
    @SerializedName("node_id")      val nodeId: Int,
    @SerializedName("site_id")      val siteId: Int,
    @SerializedName("firebase_uid") val firebaseUid: String,
    @SerializedName("rating")       val rating: Int    // 1–5
)

data class RatingResponse(
    @SerializedName("message") val message: String = "",
    @SerializedName("new_avg") val newAvg: Double  = 0.0,
    @SerializedName("total")   val total: Int      = 0
)

data class ReviewSummary(
    @SerializedName("avg_star_rating")        val avgStarRating: Double      = 0.0,
    @SerializedName("total_ratings")          val totalRatings: Int          = 0,
    @SerializedName("avg_overall_experience") val avgOverallExperience: Double = 0.0,
    @SerializedName("avg_guide_helpfulness")  val avgGuideHelpfulness: Double  = 0.0,
    @SerializedName("avg_recommend_score")    val avgRecommendScore: Double    = 0.0,
    @SerializedName("total_reviews")          val totalReviews: Int            = 0,
    @SerializedName("recommend_pct")          val recommendPct: Double         = 0.0,
    @SerializedName("satisfaction_label")     val satisfactionLabel: String    = "No data"
)

// ─────────────────────────────────────────────────────────────────────────────
// /users/register  →  POST /users/register
// ─────────────────────────────────────────────────────────────────────────────
data class UserRegisterRequest(
    @SerializedName("firebase_uid")   val firebaseUid: String,
    @SerializedName("display_name")   val displayName: String? = null,
    @SerializedName("email")          val email:        String? = null,
    @SerializedName("phone")          val phone:        String? = null,
    @SerializedName("avatar_url")     val avatarUrl:    String? = null,
    @SerializedName("preferred_lang") val preferredLang: String = "en-IN",
    @SerializedName("is_anonymous")   val isAnonymous:  Boolean = false
)

data class UserResponse(
    @SerializedName("id")             val id: String = "",
    @SerializedName("firebase_uid")   val firebaseUid: String = "",
    @SerializedName("display_name")   val displayName: String? = null,
    @SerializedName("email")          val email: String? = null,
    @SerializedName("phone")          val phone: String? = null,
    @SerializedName("avatar_url")     val avatarUrl: String? = null,
    @SerializedName("preferred_lang") val preferredLang: String = "en-IN",
    @SerializedName("is_anonymous")   val isAnonymous: Boolean = false,
    @SerializedName("created_at")     val createdAt: String = "",
    @SerializedName("last_active_at") val lastActiveAt: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// /reviews/users/{firebase_uid}/history  →  GET visit history
// ─────────────────────────────────────────────────────────────────────────────
data class VisitHistoryItem(
    @SerializedName("id")               val id: Int = 0,
    @SerializedName("site_id")          val siteId: Int = 0,
    @SerializedName("site_name")        val siteName: String = "",
    @SerializedName("trip_id")          val tripId: Int? = null,
    @SerializedName("nodes_visited")    val nodesVisited: List<Int> = emptyList(),
    @SerializedName("total_nodes")      val totalNodes: Int = 0,
    @SerializedName("nodes_completed")  val nodesCompleted: Int = 0,
    @SerializedName("completed")        val completed: Boolean = false,
    @SerializedName("visited_at")       val visitedAt: String? = null,    // ISO-8601
    @SerializedName("ended_at")         val endedAt: String? = null,      // ISO-8601
    @SerializedName("duration_mins")    val durationMins: Int? = null,
    @SerializedName("review_submitted") val reviewSubmitted: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
// /trips/active/{firebase_uid}  →  GET active trip (for resume)
// ─────────────────────────────────────────────────────────────────────────────
data class ActiveTripResponse(
    @SerializedName("active")     val active: Boolean = false,
    @SerializedName("trip_id")    val tripId: Int? = null,
    @SerializedName("site_id")    val siteId: Int? = null,
    @SerializedName("started_at") val startedAt: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// /community/feedback  →  POST /community/feedback
// ─────────────────────────────────────────────────────────────────────────────
data class FeedbackCreateRequest(
    @SerializedName("firebase_uid") val firebaseUid: String? = null,   // null = anonymous
    @SerializedName("site_id")      val siteId: Int,
    @SerializedName("category")     val category: String = "general",   // general | accessibility | content | bug
    @SerializedName("content")      val content: String
)

data class FeedbackResponse(
    @SerializedName("id")         val id: Int = 0,
    @SerializedName("site_id")    val siteId: Int = 0,
    @SerializedName("category")   val category: String = "general",
    @SerializedName("content")    val content: String = "",
    @SerializedName("status")     val status: String = "open",
    @SerializedName("created_at") val createdAt: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// /community/comments  →  Node comments (with replies)
// ─────────────────────────────────────────────────────────────────────────────
data class NodeCommentCreateRequest(
    @SerializedName("firebase_uid")      val firebaseUid: String,
    @SerializedName("site_id")           val siteId: Int,
    @SerializedName("node_id")           val nodeId: Int,
    @SerializedName("content")           val content: String,
    @SerializedName("parent_comment_id") val parentCommentId: Int? = null
)

data class NodeCommentResponse(
    @SerializedName("id")                val id: Int = 0,
    @SerializedName("user_id")           val userId: String = "",
    @SerializedName("site_id")           val siteId: Int = 0,
    @SerializedName("node_id")           val nodeId: Int = 0,
    @SerializedName("parent_comment_id") val parentCommentId: Int? = null,
    @SerializedName("content")           val content: String = "",
    @SerializedName("is_flagged")        val isFlagged: Boolean = false,
    @SerializedName("created_at")        val createdAt: String = "",
    @SerializedName("display_name")      val displayName: String? = null,
    @SerializedName("avatar_url")        val avatarUrl: String? = null,
    @SerializedName("reply_count")       val replyCount: Int = 0,
    @SerializedName("is_own")            val isOwn: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
// /instants  →  node UGC moments (Instagram-style)
// ─────────────────────────────────────────────────────────────────────────────
data class NodeInstantCreateRequest(
    @SerializedName("firebase_uid") val firebaseUid: String,
    @SerializedName("site_id")      val siteId: Int,
    @SerializedName("node_id")       val nodeId: Int,
    @SerializedName("media_url")     val mediaUrl: String,
    @SerializedName("media_type")    val mediaType: String = "image",
    @SerializedName("caption")       val caption: String? = null
)

data class NodeInstantResponse(
    @SerializedName("id")           val id: Int = 0,
    @SerializedName("user_id")       val userId: String = "",
    @SerializedName("site_id")       val siteId: Int = 0,
    @SerializedName("node_id")       val nodeId: Int = 0,
    @SerializedName("media_url")     val mediaUrl: String = "",
    @SerializedName("media_type")    val mediaType: String = "image",
    @SerializedName("caption")       val caption: String? = null,
    @SerializedName("like_count")    val likeCount: Int = 0,
    @SerializedName("created_at")    val createdAt: String = "",
    @SerializedName("display_name")  val displayName: String? = null,
    @SerializedName("avatar_url")    val avatarUrl: String? = null,
    @SerializedName("liked_by_me")   val likedByMe: Boolean = false
)

data class NodeInstantLikeResponse(
    @SerializedName("instant_id") val instantId: Int = 0,
    @SerializedName("liked")       val liked: Boolean = false,
    @SerializedName("like_count")  val likeCount: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// /sites/{site_id}/recommendations  →  GET /sites/{site_id}/recommendations
// ─────────────────────────────────────────────────────────────────────────────
data class RecommendationResponse(
    @SerializedName("id")          val id: Int,
    @SerializedName("site_id")     val siteId: Int,
    @SerializedName("type")        val type: String,      // monument, hotel, restaurant
    @SerializedName("name")        val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("latitude")    val latitude: Double? = null,
    @SerializedName("longitude")   val longitude: Double? = null
)
// ─────────────────────────────────────────────────────────────────────────────
// /amenities/near-node  →  GET /amenities/near-node?node_id=&top_n=
// ─────────────────────────────────────────────────────────────────────────────
data class AmenityResponse(
    @SerializedName("id")               val id:             Int,
    @SerializedName("site_id")          val siteId:         Int,
    @SerializedName("node_id")          val nodeId:         Int?    = null,
    @SerializedName("type")             val type:           String,   // "washroom" | "shop"
    @SerializedName("name")             val name:           String,
    @SerializedName("description")      val description:    String? = null,
    @SerializedName("latitude")         val latitude:       Double,
    @SerializedName("longitude")        val longitude:      Double,
    @SerializedName("price_info")       val priceInfo:      String? = null,
    @SerializedName("timing")           val timing:         String? = null,
    @SerializedName("is_paid")          val isPaid:         Boolean = false,
    @SerializedName("distance_meters")  val distanceMeters: Double? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// /users/{firebase_uid}/phone  →  PATCH (set mobile number after Google sign-in)
// ─────────────────────────────────────────────────────────────────────────────
data class PhoneUpdateRequest(
    @SerializedName("phone") val phone: String
)

// ─────────────────────────────────────────────────────────────────────────────
// /stats/live · /stats/heartbeat · /stats/visit
// ─────────────────────────────────────────────────────────────────────────────
data class LiveStats(
    @SerializedName("active_users")    val activeUsers: Int    = 0,
    @SerializedName("lifetime_visits") val lifetimeVisits: Int = 0,
    @SerializedName("total_users")     val totalUsers: Int     = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// /insights/sites/{site_id}
// ─────────────────────────────────────────────────────────────────────────────
data class DailyVisit(
    @SerializedName("date")  val date: String = "",
    @SerializedName("count") val count: Int   = 0
)

data class NodePopularity(
    @SerializedName("node_id")          val nodeId: Int        = 0,
    @SerializedName("name")             val name: String       = "",
    @SerializedName("visits")           val visits: Int        = 0,
    @SerializedName("avg_rating")       val avgRating: Double  = 0.0,
    @SerializedName("rating_count")     val ratingCount: Int   = 0,
    @SerializedName("engagement_score") val engagementScore: Double = 0.0
)

data class SiteMlInsight(
    @SerializedName("model")                        val model: String = "linear_regression",
    @SerializedName("trained_on")                   val trainedOn: Int = 0,
    @SerializedName("predicted_visits_next_day")    val predictedVisitsNextDay: Int = 0,
    @SerializedName("visits_trend")                 val visitsTrend: String = "steady",
    @SerializedName("mins_per_extra_node")          val minsPerExtraNode: Double = 0.0,
    @SerializedName("predicted_full_duration_mins") val predictedFullDurationMins: Double = 0.0,
    @SerializedName("engagement_score")             val engagementScore: Double = 0.0,
    @SerializedName("insight_text")                 val insightText: String = ""
)

data class SiteInsights(
    @SerializedName("site_id")             val siteId: Int = 0,
    @SerializedName("site_name")           val siteName: String = "",
    @SerializedName("total_visits")        val totalVisits: Int = 0,
    @SerializedName("unique_visitors")     val uniqueVisitors: Int = 0,
    @SerializedName("avg_duration_mins")   val avgDurationMins: Double = 0.0,
    @SerializedName("avg_nodes_completed") val avgNodesCompleted: Double = 0.0,
    @SerializedName("completion_rate")     val completionRate: Double = 0.0,
    @SerializedName("total_interactions")  val totalInteractions: Int = 0,
    @SerializedName("avg_rating")          val avgRating: Double = 0.0,
    @SerializedName("daily_visits")        val dailyVisits: List<DailyVisit> = emptyList(),
    @SerializedName("node_popularity")     val nodePopularity: List<NodePopularity> = emptyList(),
    @SerializedName("ml")                  val ml: SiteMlInsight = SiteMlInsight()
)

// ─────────────────────────────────────────────────────────────────────────────
// /insights/nodes/{node_id}
// ─────────────────────────────────────────────────────────────────────────────
data class NodeMlInsight(
    @SerializedName("engagement_score") val engagementScore: Double = 0.0,
    @SerializedName("insight_text")     val insightText: String = ""
)

data class NodeInsights(
    @SerializedName("node_id")        val nodeId: Int = 0,
    @SerializedName("site_id")        val siteId: Int = 0,
    @SerializedName("name")           val name: String = "",
    @SerializedName("visits")         val visits: Int = 0,
    @SerializedName("avg_rating")     val avgRating: Double = 0.0,
    @SerializedName("rating_count")   val ratingCount: Int = 0,
    @SerializedName("comments")       val comments: Int = 0,
    @SerializedName("interactions")   val interactions: Int = 0,
    @SerializedName("popularity_pct") val popularityPct: Double = 0.0,
    @SerializedName("ml")             val ml: NodeMlInsight = NodeMlInsight()
)

// ─────────────────────────────────────────────────────────────────────────────
// /insights/sites/{site_id}/history  →  stored daily snapshots (training series)
// ─────────────────────────────────────────────────────────────────────────────
data class SiteInsightSnapshot(
    @SerializedName("snapshot_date")                val snapshotDate: String = "",
    @SerializedName("total_visits")                 val totalVisits: Int = 0,
    @SerializedName("unique_visitors")              val uniqueVisitors: Int = 0,
    @SerializedName("avg_duration_mins")            val avgDurationMins: Double = 0.0,
    @SerializedName("avg_nodes_completed")          val avgNodesCompleted: Double = 0.0,
    @SerializedName("completion_rate")              val completionRate: Double = 0.0,
    @SerializedName("total_interactions")           val totalInteractions: Int = 0,
    @SerializedName("avg_rating")                   val avgRating: Double = 0.0,
    @SerializedName("engagement_score")             val engagementScore: Double = 0.0,
    @SerializedName("predicted_visits_next_day")    val predictedVisitsNextDay: Int = 0,
    @SerializedName("visits_trend")                 val visitsTrend: String = "steady",
    @SerializedName("mins_per_extra_node")          val minsPerExtraNode: Double = 0.0,
    @SerializedName("predicted_full_duration_mins") val predictedFullDurationMins: Double = 0.0
)

// ─────────────────────────────────────────────────────────────────────────────
// Gamification — gems wallet · /gems/{firebase_uid}
// ─────────────────────────────────────────────────────────────────────────────
data class GemTransactionItem(
    @SerializedName("delta")         val delta: Int = 0,
    @SerializedName("reason")        val reason: String = "",
    @SerializedName("balance_after") val balanceAfter: Int = 0,
    @SerializedName("created_at")    val createdAt: String = ""
)

data class GemBalance(
    @SerializedName("gems")    val gems: Int = 0,
    @SerializedName("history") val history: List<GemTransactionItem> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Gamification — final quiz · /quiz/*
// ─────────────────────────────────────────────────────────────────────────────
data class QuizQuestionPublic(
    @SerializedName("question_id") val questionId: Int = 0,
    @SerializedName("idx")         val idx: Int = 0,
    @SerializedName("question")    val question: String = "",
    @SerializedName("options")     val options: List<String> = emptyList(),
    @SerializedName("answered")    val answered: Boolean = false
)

data class QuizStartResponse(
    @SerializedName("session_id")           val sessionId: Int = 0,
    @SerializedName("status")               val status: String = "active",
    @SerializedName("seconds_per_question") val secondsPerQuestion: Int = 10,
    @SerializedName("total_questions")      val totalQuestions: Int = 0,
    @SerializedName("gems_earned")          val gemsEarned: Int = 0,
    @SerializedName("questions")            val questions: List<QuizQuestionPublic> = emptyList(),
    @SerializedName("already_played")       val alreadyPlayed: Boolean = false
)

data class QuizPrepareRequest(
    @SerializedName("node_ids") val nodeIds: List<Int>
)

data class QuizPrepareResponse(
    @SerializedName("session_id")      val sessionId: Int = 0,
    @SerializedName("status")          val status: String = "",
    @SerializedName("total_questions") val totalQuestions: Int = 0,
    @SerializedName("nodes_prepared")  val nodesPrepared: Int = 0
)

data class QuizAnswerRequest(
    @SerializedName("session_id")     val sessionId: Int,
    @SerializedName("question_id")    val questionId: Int,
    @SerializedName("selected_index") val selectedIndex: Int,
    @SerializedName("seconds_taken")  val secondsTaken: Double
)

data class QuizAnswerResponse(
    @SerializedName("correct")       val correct: Boolean = false,
    @SerializedName("correct_index") val correctIndex: Int = 0,
    @SerializedName("gems_awarded")  val gemsAwarded: Int = 0,
    @SerializedName("running_total") val runningTotal: Int = 0
)

data class QuizCompleteRequest(
    @SerializedName("session_id") val sessionId: Int
)

data class QuizCompleteResponse(
    @SerializedName("status")      val status: String = "",
    @SerializedName("gems_earned") val gemsEarned: Int = 0,
    @SerializedName("new_balance") val newBalance: Int = 0
)

data class QuizAbandonRequest(
    @SerializedName("session_id") val sessionId: Int
)

// ─────────────────────────────────────────────────────────────────────────────
// Gamification — coupon store · /store/*
// ─────────────────────────────────────────────────────────────────────────────
data class StorePartner(
    @SerializedName("id")              val id: Int = 0,
    @SerializedName("name")            val name: String = "",
    @SerializedName("type")            val type: String = "",
    @SerializedName("description")     val description: String? = null,
    @SerializedName("latitude")        val latitude: Double? = null,
    @SerializedName("longitude")       val longitude: Double? = null,
    @SerializedName("distance_meters") val distanceMeters: Double? = null
)

data class CouponPurchaseRequest(
    @SerializedName("firebase_uid") val firebaseUid: String,
    @SerializedName("tier")         val tier: String,        // ultimate | special | normal
    @SerializedName("partner_kind") val partnerKind: String, // hotel | restaurant
    @SerializedName("site_id")      val siteId: Int? = null,
    @SerializedName("user_lat")     val userLat: Double? = null,
    @SerializedName("user_lng")     val userLng: Double? = null
)

data class Coupon(
    @SerializedName("id")              val id: Int = 0,
    @SerializedName("partner_name")    val partnerName: String = "",
    @SerializedName("partner_type")    val partnerType: String = "",
    @SerializedName("partner_lat")     val partnerLat: Double? = null,
    @SerializedName("partner_lng")     val partnerLng: Double? = null,
    @SerializedName("tier")            val tier: String = "",
    @SerializedName("discount_pct")    val discountPct: Int = 0,
    @SerializedName("gems_spent")      val gemsSpent: Int = 0,
    @SerializedName("code")            val code: String = "",
    @SerializedName("status")          val status: String = "active",
    @SerializedName("distance_meters") val distanceMeters: Double? = null,
    @SerializedName("created_at")      val createdAt: String = "",
    @SerializedName("expires_at")      val expiresAt: String = ""
)

data class CouponPurchaseResponse(
    @SerializedName("coupon")      val coupon: Coupon = Coupon(),
    @SerializedName("new_balance") val newBalance: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Gamification — bonus "Bingo" challenge · /bonus/*
// ─────────────────────────────────────────────────────────────────────────────
data class BonusOfferRequest(
    @SerializedName("firebase_uid")    val firebaseUid: String,
    @SerializedName("site_id")         val siteId: Int,
    @SerializedName("exclude_node_id") val excludeNodeId: Int? = null
)

data class BonusOfferResponse(
    @SerializedName("challenge_id")     val challengeId: Int = 0,
    @SerializedName("target_node_id")   val targetNodeId: Int = 0,
    @SerializedName("target_node_name") val targetNodeName: String = "",
    @SerializedName("site_name")        val siteName: String = "",
    @SerializedName("minigame")         val minigame: String = "zip",
    @SerializedName("reward_gems")      val rewardGems: Int = 0,
    @SerializedName("deadline_minutes") val deadlineMinutes: Int = 20,
    @SerializedName("expires_at")       val expiresAt: String = ""
)

data class BonusCompleteRequest(
    @SerializedName("firebase_uid")    val firebaseUid: String,
    @SerializedName("challenge_id")    val challengeId: Int,
    @SerializedName("scanned_node_id") val scannedNodeId: Int,
    @SerializedName("solved")          val solved: Boolean
)

data class BonusCompleteResponse(
    @SerializedName("status")      val status: String = "",
    @SerializedName("reward_gems") val rewardGems: Int = 0,
    @SerializedName("new_balance") val newBalance: Int = 0
)

data class BonusSiteStatus(
    @SerializedName("site_id")                val siteId: Int = 0,
    @SerializedName("site_name")              val siteName: String = "",
    @SerializedName("played")                 val played: Boolean = false,
    @SerializedName("reward_gems")            val rewardGems: Int = 0,
    @SerializedName("status")                 val status: String? = null,
    @SerializedName("completed_at")           val completedAt: String? = null,
    @SerializedName("available_after_minutes") val availableAfterMinutes: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// /insights/users/{firebase_uid}  →  personal heritage footprint ("my insights")
// ─────────────────────────────────────────────────────────────────────────────
data class UserInsights(
    @SerializedName("user_id")                      val userId: String = "",
    @SerializedName("total_visits")                 val totalVisits: Int = 0,
    @SerializedName("sites_explored")               val sitesExplored: Int = 0,
    @SerializedName("total_duration_mins")          val totalDurationMins: Int = 0,
    @SerializedName("avg_duration_mins")            val avgDurationMins: Double = 0.0,
    @SerializedName("total_nodes_completed")        val totalNodesCompleted: Int = 0,
    @SerializedName("avg_completion_rate")          val avgCompletionRate: Double = 0.0,
    @SerializedName("total_interactions")           val totalInteractions: Int = 0,
    @SerializedName("favorite_site_id")             val favoriteSiteId: Int? = null,
    @SerializedName("favorite_site_name")           val favoriteSiteName: String? = null,
    @SerializedName("engagement_score")             val engagementScore: Double = 0.0,
    @SerializedName("explorer_level")               val explorerLevel: String = "Newcomer",
    @SerializedName("predicted_next_duration_mins") val predictedNextDurationMins: Double = 0.0,
    @SerializedName("insight_text")                 val insightText: String = ""
)