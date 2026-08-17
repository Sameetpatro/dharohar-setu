// app/src/main/java/com/example/humsafar/ui/NodeDetailScreen.kt

package com.example.humsafar.ui

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.humsafar.ChatbotActivity
import com.example.humsafar.data.TripManager
import com.example.humsafar.models.AmenityResponse
import com.example.humsafar.network.Node
import com.example.humsafar.network.NodeImage
import com.example.humsafar.network.SiteDetail
import com.example.humsafar.ui.components.AnimatedOrbBackground
import com.example.humsafar.ui.components.GlassCard
import com.example.humsafar.ui.components.rememberTripSafeBack
import com.example.humsafar.ui.components.TripInfoButton
import com.example.humsafar.ui.theme.*
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// TTS state
// ─────────────────────────────────────────────────────────────────────────────

private enum class NodeTtsStatus { IDLE, SPEAKING, PAUSED }

// ─────────────────────────────────────────────────────────────────────────────
// NodeDetailScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NodeDetailScreen(
    nodeId:                 Int,
    siteId:                 Int,
    isKing:                 Boolean,
    onBack:                 () -> Unit,
    onNavigateToQr:         (Long) -> Unit,
    onNavigateToVoice:      (String, String) -> Unit,
    onNavigateToDirections: ((Int, String) -> Unit)? = null,
    onNavigateToReview:     ((Int, Int, String, Int, Int) -> Unit)? = null,
    onNavigateToQuiz:       ((Int, Int, String, Int, Int) -> Unit)? = null,   // (tripId, siteId, siteName, visited, total)
    onNavigateToAmenity:    (Int) -> Unit = {},          // ← NEW
    onNavigateToComments:   ((Int, Int, String) -> Unit)? = null,   // (nodeId, siteId, nodeName)
    onNavigateToInstants:   ((Int, Int, String) -> Unit)? = null,   // (nodeId, siteId, nodeName)
    onNavigateToInsights:   ((Int, String) -> Unit)? = null,        // (siteId, siteName)
    onNavigateToVideo:      ((Int, Int) -> Unit)? = null,           // (siteId, nodeId)
    viewModel:              NodeDetailViewModel = viewModel()
) {
    val context   = LocalContext.current
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val tripState by TripManager.state.collectAsStateWithLifecycle()
    val safeBack  = rememberTripSafeBack(onBack)

    // ── TTS State ─────────────────────────────────────────────────────────────
    var ttsStatus          by remember { mutableStateOf(NodeTtsStatus.IDLE) }
    var activeSectionLabel by remember { mutableStateOf("") }

    val tts = remember { TextToSpeech(context) { } }

    DisposableEffect(Unit) {
        tts.language = Locale.US
        onDispose { tts.stop(); tts.shutdown() }
    }

    // ── TTS Helpers ───────────────────────────────────────────────────────────

    fun stopTts() {
        tts.stop()
        ttsStatus = NodeTtsStatus.IDLE
        activeSectionLabel = ""
    }

    fun togglePause() {
        when (ttsStatus) {
            NodeTtsStatus.SPEAKING -> {
                tts.stop()
                ttsStatus = NodeTtsStatus.PAUSED
            }
            NodeTtsStatus.PAUSED -> {
                ttsStatus = NodeTtsStatus.IDLE
                activeSectionLabel = ""
            }
            NodeTtsStatus.IDLE -> {}
        }
    }

    fun speakSequence(label: String, texts: List<Pair<String, String>>) {
        tts.stop()
        ttsStatus = NodeTtsStatus.SPEAKING
        activeSectionLabel = label

        val listener = object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == "final") {
                    ttsStatus = NodeTtsStatus.IDLE
                    activeSectionLabel = ""
                }
            }
            override fun onError(id: String?) {
                ttsStatus = NodeTtsStatus.IDLE
                activeSectionLabel = ""
            }
        }
        tts.setOnUtteranceProgressListener(listener)

        texts.forEachIndexed { i, (header, body) ->
            val isLast = i == texts.size - 1
            tts.speak(header, TextToSpeech.QUEUE_ADD, null, "hdr_$i")
            tts.speak(body,   TextToSpeech.QUEUE_ADD, null, if (isLast) "final" else "body_$i")
        }
    }

    fun speakSection(label: String, siteName: String, text: String?) {
        if (text.isNullOrBlank()) return
        speakSequence(label, listOf("Now hearing $label at $siteName" to text))
    }

    fun speakEntirePage(node: Node, site: SiteDetail) {
        val texts = mutableListOf<Pair<String, String>>()
        site.summary?.takeIf { it.isNotBlank() }?.let {
            texts += "Now hearing about ${site.name}" to it
        }
        site.history?.takeIf { it.isNotBlank() }?.let {
            texts += "Now hearing the history of ${site.name}" to it
        }
        node.description?.takeIf { it.isNotBlank() }?.let {
            texts += "Now hearing about ${node.name} at ${site.name}" to it
        }
        if (texts.isNotEmpty()) speakSequence("Full Page", texts)
    }

    // ── Load ──────────────────────────────────────────────────────────────────
    LaunchedEffect(nodeId, siteId) { viewModel.loadNode(nodeId, siteId) }

    Box(Modifier.fillMaxSize()) {
        AnimatedOrbBackground(Modifier.fillMaxSize())

        when (val s = uiState) {
            is NodeDetailUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏛️", fontSize = 52.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Loading…", color = TextPrimary, fontSize = 16.sp)
                    }
                }
            }

            is NodeDetailUiState.Ready -> {
                val node     = s.node
                val site     = s.site
                val allNodes = s.allNodes

                val nodeImages = remember(node.images, node.imageUrl) {
                    if (node.images.isNotEmpty()) {
                        node.images.sortedBy { it.displayOrder }
                    } else if (!node.imageUrl.isNullOrBlank()) {
                        listOf(NodeImage(id = 0, imageUrl = node.imageUrl!!, displayOrder = 0))
                    } else {
                        emptyList()
                    }
                }

                Box(Modifier.fillMaxSize()) {

                    // ── Scrollable content ────────────────────────────────────
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            NodeImageGallery(
                                nodeName = node.name,
                                images   = nodeImages,
                                onBack   = safeBack
                            )

                            Column(Modifier.padding(horizontal = 20.dp)) {
                                Spacer(Modifier.height(16.dp))

                                // ── "Hear This Node" top card ─────────────────
                                NodeHearPageCard(
                                    ttsStatus = ttsStatus,
                                    onSpeak   = { speakEntirePage(node, site) },
                                    onStop    = { stopTts() }
                                )
                                Spacer(Modifier.height(16.dp))

                                // ── Action grid (Watch Video, Scan QR) ────────
                                NodeActionsGrid(
                                    node         = node,
                                    siteId       = siteId,
                                    context      = context,
                                    onQr         = onNavigateToQr,
                                    onWatchVideo = { sId, nId ->
                                        stopTts()
                                        onNavigateToVideo?.invoke(sId, nId)
                                    }
                                )
                                Spacer(Modifier.height(20.dp))

                                // ── About site ────────────────────────────────
                                if (!site.summary.isNullOrBlank()) {
                                    NodeInfoCard(
                                        title              = "📖 About ${site.name}",
                                        body               = site.summary!!,
                                        sectionLabel       = "About Site",
                                        ttsStatus          = ttsStatus,
                                        activeSectionLabel = activeSectionLabel,
                                        onSpeak            = { speakSection("About Site", site.name, site.summary) },
                                        onStop             = { stopTts() },
                                        onTogglePause      = { togglePause() }
                                    )
                                    Spacer(Modifier.height(14.dp))
                                }

                                // ── History ───────────────────────────────────
                                if (!site.history.isNullOrBlank()) {
                                    NodeInfoCard(
                                        title              = "📜 History",
                                        body               = site.history!!,
                                        sectionLabel       = "History",
                                        ttsStatus          = ttsStatus,
                                        activeSectionLabel = activeSectionLabel,
                                        onSpeak            = { speakSection("History", site.name, site.history) },
                                        onStop             = { stopTts() },
                                        onTogglePause      = { togglePause() }
                                    )
                                    Spacer(Modifier.height(14.dp))
                                }

                                // ── Node Description ──────────────────────────
                                if (!node.description.isNullOrBlank()) {
                                    NodeInfoCard(
                                        title              = "🗺️ About This Spot",
                                        body               = node.description!!,
                                        sectionLabel       = "About This Spot",
                                        ttsStatus          = ttsStatus,
                                        activeSectionLabel = activeSectionLabel,
                                        onSpeak            = { speakSection("About This Spot", node.name, node.description) },
                                        onStop             = { stopTts() },
                                        onTogglePause      = { togglePause() }
                                    )
                                    Spacer(Modifier.height(14.dp))
                                }

                                // ── Nearby Amenities ──────────────────────────
                                if (s.amenities.isNotEmpty()) {
                                    AmenitiesSection(
                                        amenities = s.amenities,
                                        onNavigateToAmenity = onNavigateToAmenity
                                    )
                                    Spacer(Modifier.height(14.dp))
                                }

                                // ── Comments entry point ──────────────────────
                                CommentsEntryCard(
                                    onClick = {
                                        onNavigateToComments?.invoke(node.id, siteId, node.name)
                                    }
                                )
                                Spacer(Modifier.height(14.dp))

                                // ── Per-spot insights ─────────────────────────
                                NodeInsightsEntryCard(
                                    nodeId   = node.id,
                                    onOpenFull = { onNavigateToInsights?.invoke(siteId, site.name) }
                                )
                                Spacer(Modifier.height(14.dp))

                                // ── Trip Progress ─────────────────────────────
                                if (tripState.isTripActive && allNodes.isNotEmpty()) {
                                    TripProgressSection(
                                        nodes      = allNodes,
                                        visitedIds = tripState.visitedNodeIds,
                                        currentId  = tripState.currentNodeId
                                    )
                                    Spacer(Modifier.height(14.dp))
                                }

                                // Extra bottom padding so floating bar / FABs don't overlap
                                Spacer(Modifier.height(if (ttsStatus != NodeTtsStatus.IDLE) 150.dp else 100.dp))
                            }
                        }
                    }

                    // ── Floating TTS Control Bar ──────────────────────────────
                    if (ttsStatus != NodeTtsStatus.IDLE) {
                        NodeTtsControlBar(
                            ttsStatus          = ttsStatus,
                            activeSectionLabel = activeSectionLabel,
                            onTogglePause      = { togglePause() },
                            onStop             = { stopTts() },
                            modifier           = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    }

                    // ── Ask FAB ───────────────────────────────────────────────
                    FloatingAskShreeButton(
                        node     = node,
                        siteId   = siteId,
                        context  = context,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(
                                end    = 16.dp,
                                bottom = if (ttsStatus != NodeTtsStatus.IDLE) 110.dp else 16.dp
                            )
                    )

                    // ── Instant quick action (Instagram-style wall tab) ────────
                    FloatingInstantWallTab(
                        onClick = { onNavigateToInstants?.invoke(node.id, siteId, node.name) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(
                                end = 0.dp,
                                bottom = if (ttsStatus != NodeTtsStatus.IDLE) 170.dp else 90.dp
                            )
                    )

                    // ── Trip Info Button ──────────────────────────────────────
                    if (tripState.isTripActive) {
                        TripInfoButton(
                            siteName          = tripState.siteName,
                            visitedCount      = tripState.visitedNodeIds.size,
                            totalCount        = allNodes.size,
                            onDirectionsClick = {
                                onNavigateToDirections?.invoke(siteId, tripState.siteName)
                            },
                            onEndTripClick    = {
                                val tripId       = tripState.tripId
                                val tripSiteId   = tripState.siteId
                                val visitedCount = tripState.visitedNodeIds.size
                                val totalCount   = allNodes.size
                                val sName        = tripState.siteName
                                // Process-scoped — survives the popUpTo("home")
                                // navigation that destroys this NodeDetailViewModel.
                                TripManager.endTripOnServer(
                                    visitedNodeIds = tripState.visitedNodeIds,
                                    lastLat        = tripState.lastLat,
                                    lastLng        = tripState.lastLng
                                )
                                // The geofence-exit path must not also fire this trip's quiz.
                                com.example.humsafar.data.QuizTrigger.markHandled(tripId)
                                // Final quiz sits between ending the trip and the review.
                                if (onNavigateToQuiz != null) {
                                    onNavigateToQuiz(tripId, tripSiteId, sName, visitedCount, totalCount)
                                } else {
                                    onNavigateToReview?.invoke(tripId, tripSiteId, sName, visitedCount, totalCount)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .navigationBarsPadding()
                                .padding(
                                    start  = 16.dp,
                                    bottom = if (ttsStatus != NodeTtsStatus.IDLE) 110.dp else 16.dp
                                )
                        )
                    }

                    // ── Trip timer pill (top) ─────────────────────────────────
                    if (tripState.isTripActive && tripState.startedAtMs > 0L) {
                        TripTimerPill(
                            startedAtMs = tripState.startedAtMs,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 10.dp)
                        )
                    }
                }
            }

            is NodeDetailUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        s.message,
                        color     = Color(0xFFFF6B6B),
                        fontSize  = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TripTimerPill(
    startedAtMs: Long,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAppColors.current
    val accent = LocalAccent.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }

    val elapsed = (now - startedAtMs).coerceAtLeast(0L) / 1000L
    val hh = elapsed / 3600
    val mm = (elapsed % 3600) / 60
    val ss = elapsed % 60
    val label = if (hh > 0) "%d:%02d:%02d".format(hh, mm, ss) else "%02d:%02d".format(mm, ss)

    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.primary.copy(alpha = 0.92f), accent.dark.copy(alpha = 0.92f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏱", fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Trip time $label",
                color = accent.onAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nearby Amenities section — shown inside NodeDetailScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AmenitiesSection(
    amenities:           List<AmenityResponse>,
    onNavigateToAmenity: (Int) -> Unit
) {
    val washrooms = amenities.filter { it.type == "washroom" }
    val shops     = amenities.filter { it.type == "shop" }

    Column {
        Text(
            "🗺️ Nearby Amenities",
            color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))

        if (washrooms.isNotEmpty()) {
            Text(
                "🚻 Washrooms",
                color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            washrooms.forEach { amenity ->
                AmenityRowCard(
                    amenity = amenity,
                    onClick = { onNavigateToAmenity(amenity.id) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (shops.isNotEmpty()) {
            if (washrooms.isNotEmpty()) Spacer(Modifier.height(4.dp))
            Text(
                "🛍️ Shops",
                color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            shops.forEach { amenity ->
                AmenityRowCard(
                    amenity = amenity,
                    onClick = { onNavigateToAmenity(amenity.id) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AmenityRowCard(
    amenity: AmenityResponse,
    onClick: () -> Unit
) {
    val isShop    = amenity.type == "shop"
    val accent    = LocalAccent.current
    val tokens    = LocalAppColors.current
    val hue       = if (isShop) accent.primary else Color(0xFF64B5F6)
    val bgGrad    = listOf(tokens.surface, tokens.surfaceMuted)
    val borderClr = hue.copy(alpha = 0.28f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(bgGrad))
            .border(0.7.dp, borderClr, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            // Type icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(hue.copy(alpha = 0.14f))
                    .border(1.dp, hue.copy(alpha = 0.40f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isShop) "🛍️" else "🚻", fontSize = 20.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    amenity.name,
                    color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                // Distance if available, else price, else timing
                val subtitle = amenity.distanceMeters?.let { d ->
                    if (d >= 1000) "${"%.1f".format(d / 1000)} km away"
                    else "${d.toInt()} m away"
                } ?: amenity.priceInfo ?: amenity.timing ?: ""

                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = TextTertiary, fontSize = 12.sp)
                }
            }

            // Price badge
            amenity.priceInfo?.takeIf { it.isNotBlank() }?.let { price ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(hue.copy(alpha = 0.10f))
                        .border(0.5.dp, hue.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(price, color = hue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }

            Icon(
                Icons.Default.ChevronRight, null,
                tint = TextTertiary, modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun FloatingInstantWallTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf = rememberInfiniteTransition(label = "instantTab")
    val pulse by inf.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "p"
    )

    Box(modifier) {
        Box(
            Modifier
                // Push it to the right wall (partially off-screen)
                .offset(x = 22.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .size(width = 64.dp, height = 104.dp)
                .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = accent.primary.copy(alpha = 0.18f))
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(accent.primary, accent.dark)))
                .border(2.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "I\nN\nS\nT\nA\nN\nT",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 11.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// "Hear This Node" top card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NodeHearPageCard(
    ttsStatus: NodeTtsStatus,
    onSpeak:   () -> Unit,
    onStop:    () -> Unit
) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf  = rememberInfiniteTransition(label = "hearNode")
    val glow by inf.animateFloat(
        0.35f, 0.65f,
        infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        "g"
    )
    val isActive = ttsStatus != NodeTtsStatus.IDLE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.tint.copy(alpha = if (isActive) 0.95f else 0.75f), tokens.surface)
                )
            )
            .border(
                1.dp,
                accent.primary.copy(alpha = if (isActive) glow else 0.35f),
                RoundedCornerShape(16.dp)
            )
            .clickable { if (isActive) onStop() else onSpeak() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.primary.copy(alpha = if (isActive) glow * 0.35f else 0.14f))
                    .border(1.dp, accent.primary.copy(alpha = if (isActive) glow else 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isActive) "⏹️" else "🎧", fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isActive) "Stop Narration" else "Hear This Node",
                    color      = tokens.textPrimary,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isActive) "Tap to stop listening"
                    else "Listen to full node narration",
                    color    = accent.dark.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )
            }
            Icon(
                if (isActive) Icons.Default.Stop else Icons.Default.VolumeUp,
                contentDescription = null,
                tint     = accent.primary.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating TTS Control Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NodeTtsControlBar(
    ttsStatus:          NodeTtsStatus,
    activeSectionLabel: String,
    onTogglePause:      () -> Unit,
    onStop:             () -> Unit,
    modifier:           Modifier = Modifier
) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf   = rememberInfiniteTransition(label = "nodeTts")
    val pulse by inf.animateFloat(
        0.6f, 1f,
        infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
        "p"
    )

    val isSpeaking = ttsStatus == NodeTtsStatus.SPEAKING
    val isPaused   = ttsStatus == NodeTtsStatus.PAUSED

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.linearGradient(
                    listOf(tokens.surface.copy(alpha = 0.98f), accent.tint.copy(alpha = 0.55f))
                )
            )
            .border(
                1.dp,
                accent.primary.copy(alpha = if (isSpeaking) pulse else 0.35f),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        accent.primary.copy(
                            alpha = if (isSpeaking) pulse * 0.22f else 0.12f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint     = accent.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = when (ttsStatus) {
                        NodeTtsStatus.SPEAKING -> "Narrating…"
                        NodeTtsStatus.PAUSED   -> "Paused"
                        NodeTtsStatus.IDLE     -> ""
                    },
                    color      = tokens.textPrimary,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (activeSectionLabel.isNotBlank()) {
                    Text(
                        activeSectionLabel,
                        color    = accent.dark.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.primary.copy(alpha = 0.12f))
                    .border(0.7.dp, accent.primary.copy(alpha = 0.35f), CircleShape)
                    .clickable { onTogglePause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    tint     = accent.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FF5252))
                    .border(0.7.dp, Color(0xFFFF5252).copy(alpha = 0.5f), CircleShape)
                    .clickable { onStop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint     = Color(0xFFFF6B6B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Info Card with inline voice controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NodeInfoCard(
    title:              String,
    body:               String,
    sectionLabel:       String,
    ttsStatus:          NodeTtsStatus,
    activeSectionLabel: String,
    onSpeak:            () -> Unit,
    onStop:             () -> Unit,
    onTogglePause:      () -> Unit
) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val isThisActive = activeSectionLabel == sectionLabel && ttsStatus != NodeTtsStatus.IDLE
    val isSpeaking   = isThisActive && ttsStatus == NodeTtsStatus.SPEAKING
    val isPaused     = isThisActive && ttsStatus == NodeTtsStatus.PAUSED

    val inf  = rememberInfiniteTransition(label = "nodeCard_$sectionLabel")
    val glow by inf.animateFloat(
        0.4f, 0.9f,
        infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
        "g"
    )

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth()
        ) {
            Text(
                title,
                color      = if (isThisActive) accent.dark else TextPrimary,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (isThisActive) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(accent.primary.copy(alpha = 0.18f))
                            .border(0.7.dp, accent.primary.copy(alpha = 0.45f), CircleShape)
                            .clickable { onTogglePause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint     = accent.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FF5252))
                            .border(0.7.dp, Color(0xFFFF5252).copy(alpha = 0.5f), CircleShape)
                            .clickable { onStop() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint     = Color(0xFFFF6B6B),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accent.tint.copy(alpha = 0.55f))
                            .border(0.7.dp, accent.primary.copy(alpha = 0.35f), CircleShape)
                            .clickable { onSpeak() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Listen to $title",
                            tint     = accent.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isThisActive)
                        Brush.linearGradient(
                            listOf(accent.tint.copy(alpha = 0.75f), tokens.surfaceMuted)
                        )
                    else
                        Brush.linearGradient(listOf(GlassWhite10, GlassWhite10))
                )
                .border(
                    if (isThisActive) 1.dp else 0.7.dp,
                    if (isThisActive) accent.primary.copy(alpha = if (isSpeaking) glow else 0.35f)
                    else GlassBorder,
                    RoundedCornerShape(14.dp)
                )
                .padding(14.dp)
        ) {
            Text(body, color = TextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action grid — Watch Video + Scan QR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NodeActionsGrid(
    node:         Node,
    siteId:       Int,
    context:      android.content.Context,
    onQr:         (Long) -> Unit,
    onWatchVideo: ((Int, Int) -> Unit)? = null
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NodeActionCard(
            icon           = "🎬",
            title          = "Watch Video",
            subtitle       = "Visual tour",
            modifier       = Modifier.weight(1f),
            onClick        = {
                if (!node.videoUrl.isNullOrBlank()) {
                    if (onWatchVideo != null) {
                        onWatchVideo(siteId, node.id)
                    } else {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(node.videoUrl)))
                    }
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "No video available for this node",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        NodeActionCard(
            icon           = "📷",
            title          = "Scan Next QR",
            subtitle       = "Continue journey",
            modifier       = Modifier.weight(1f),
            onClick        = { onQr(siteId.toLong()) }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Node image gallery
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NodeImageGallery(
    nodeName: String,
    images:   List<NodeImage>,
    onBack:   () -> Unit
) {
    val listState    = rememberLazyListState()
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentIndex = listState.firstVisibleItemIndex
    }

    Box(Modifier.fillMaxWidth().height(300.dp)) {
        if (images.isNotEmpty()) {
            LazyRow(
                state             = listState,
                modifier          = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) {
                itemsIndexed(images) { _, img ->
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(img.imageUrl).crossfade(400).build(),
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillParentMaxWidth().fillMaxHeight()
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Loading -> ShimmerBox()
                            is AsyncImagePainter.State.Error   -> EmptyImageBox()
                            else                               -> SubcomposeAsyncImageContent()
                        }
                    }
                }
            }

            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0xBB000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "${currentIndex + 1} / ${images.size}",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }

            if (images.size > 1) {
                Row(
                    modifier              = Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    images.forEachIndexed { i, _ ->
                        Box(
                            Modifier
                                .size(if (i == currentIndex) 8.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == currentIndex) LocalAccent.current.primary else Color(0x66FFFFFF)
                                )
                        )
                    }
                }
            }
        } else {
            EmptyImageBox()
        }

        Box(
            Modifier.fillMaxWidth().height(100.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color(0xCC050D1A), Color.Transparent)))
        )
        Box(
            Modifier.fillMaxWidth().height(100.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF050D1A))))
        )

        Box(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)
                .size(42.dp).clip(CircleShape).background(Color(0xBB000000))
                .border(0.5.dp, GlassBorder, CircleShape).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, null,
                tint = Color.White, modifier = Modifier.size(20.dp)
            )
        }

        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(nodeName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }

    // Thumbnail strip
    if (images.size > 1) {
        LazyRow(
            modifier              = Modifier
                .fillMaxWidth()
                .background(Color(0xFF050D1A))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            itemsIndexed(images) { i, img ->
                val selected = i == currentIndex
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(img.imageUrl).crossfade(300).build(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(64.dp, 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            if (selected) 2.dp else 0.dp,
                            if (selected) LocalAccent.current.primary else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .alpha(if (selected) 1f else 0.5f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WatchVideoBar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WatchVideoBar(
    siteId:       Int,
    nodeId:       Int,
    videoUrl:     String,
    label:        String,
    modifier:     Modifier = Modifier,
    onWatchVideo: ((Int, Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf     = rememberInfiniteTransition(label = "vb")
    val pulse   by inf.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse), "vp")
    val glow    by inf.animateFloat(0.5f,  1f,    infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse), "vg")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, tokens.bgWarm.copy(alpha = 0.92f))
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(accent.tint.copy(alpha = 0.85f), tokens.surface, tokens.surfaceMuted)
                    )
                )
                .border(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            accent.primary.copy(alpha = glow),
                            accent.dark.copy(alpha = glow * 0.55f),
                            accent.primary.copy(alpha = 0.22f)
                        )
                    ),
                    RoundedCornerShape(18.dp)
                )
                .clickable {
                    if (onWatchVideo != null) {
                        onWatchVideo(siteId, nodeId)
                    } else {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)))
                    }
                }
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).scale(pulse).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent.primary, accent.dark))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = accent.onAccent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Tap to play video", color = accent.dark.copy(alpha = 0.72f), fontSize = 11.sp)
                }
                Text("▶", color = accent.primary.copy(alpha = glow), fontSize = 18.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small reusable cards / helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NodeActionCard(
    icon:           String,
    title:          String,
    subtitle:       String,
    modifier:       Modifier = Modifier,
    onClick:        () -> Unit
) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf      = rememberInfiniteTransition(label = "ac")
    val glowAlpha by inf.animateFloat(
        0.3f, 0.6f,
        infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        "glow"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.tint.copy(alpha = 0.75f), tokens.surface, tokens.surfaceMuted)
                )
            )
            .border(1.dp, accent.primary.copy(alpha = glowAlpha), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon,     fontSize = 26.sp)
            Text(title,    color = tokens.textPrimary,  fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = tokens.textTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FloatingAskShreeButton(
    node:     Node,
    siteId:   Int,
    context:  android.content.Context,
    modifier: Modifier = Modifier
) {
    val inf       = rememberInfiniteTransition(label = "ask")
    val pulse     by inf.animateFloat(1f,   1.05f, infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse), "ps")
    val glowAlpha by inf.animateFloat(0.4f, 0.8f,  infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse), "ga")

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .scale(pulse).size(68.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(LocalAccent.current.primary.copy(alpha = glowAlpha * 0.4f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(60.dp).clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(LocalAccent.current.primary, LocalAccent.current.dark)
                    )
                )
                .border(2.dp, Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x22FFFFFF))), CircleShape)
                .clickable {
                    context.startActivity(
                        Intent(context, ChatbotActivity::class.java).apply {
                            putExtra("SITE_NAME", node.name)
                            putExtra("SITE_ID", siteId.toString())
                            putExtra("NODE_ID", node.id.toString())
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = LocalAccent.current.onAccent, modifier = Modifier.size(22.dp))
                Text("Ask", color = LocalAccent.current.onAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TripProgressSection(
    nodes:      List<Node>,
    visitedIds: List<Int>,
    currentId:  Int
) {
    Column {
        Text("🗺️ Trip Progress", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val ordered = remember(nodes) { nodes.sortedBy { it.sequenceOrder } }
        ordered.forEachIndexed { i, n ->
            val visited   = n.id in visitedIds
            val isCurrent = n.id == currentId
            val stopLabel = (i + 1).toString()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(
                        when {
                            isCurrent -> LocalAccent.current.primary
                            visited   -> Color(0xFF4ADE80)
                            else      -> GlassWhite15
                        }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (visited || isCurrent) "✓" else stopLabel,
                        color      = if (visited || isCurrent) Color.Black else TextTertiary,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    n.name,
                    color    = when { isCurrent -> LocalAccent.current.primary; visited -> TextTertiary; else -> TextPrimary },
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Comments entry point card — opens NodeCommentsScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CommentsEntryCard(onClick: () -> Unit) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf       = rememberInfiniteTransition(label = "commentsCard")
    val glowAlpha by inf.animateFloat(
        0.3f, 0.55f,
        infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        "ga"
    )
    val borderColor = accent.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.tint.copy(alpha = 0.75f), tokens.surface, tokens.surfaceMuted)
                )
            )
            .border(1.dp, borderColor.copy(alpha = glowAlpha), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp).clip(CircleShape)
                    .background(borderColor.copy(alpha = 0.18f))
                    .border(1.dp, borderColor.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("💬", fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Comments",
                    color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "Share what you noticed · reply to other travellers",
                    color = tokens.textSecondary,
                    fontSize = 11.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight, null,
                tint     = borderColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Instants entry point card — opens NodeInstantsScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InstantsEntryCard(onClick: () -> Unit) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf = rememberInfiniteTransition(label = "instantsCard")
    val glowAlpha by inf.animateFloat(
        0.35f, 0.65f,
        infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        "iga"
    )
    val borderColor = accent.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.tint.copy(alpha = 0.75f), tokens.surface, tokens.surfaceMuted)
                )
            )
            .border(1.dp, borderColor.copy(alpha = glowAlpha), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp).clip(CircleShape)
                    .background(borderColor.copy(alpha = 0.18f))
                    .border(1.dp, borderColor.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("📸", fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Instants",
                    color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "See top-loved moments · share yours",
                    color = tokens.textSecondary,
                    fontSize = 11.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight, null,
                tint     = borderColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-spot insights entry card — shows this node's engagement + opens full dashboard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NodeInsightsEntryCard(nodeId: Int, onOpenFull: () -> Unit) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    var insight by remember(nodeId) { mutableStateOf<com.example.humsafar.models.NodeInsights?>(null) }

    LaunchedEffect(nodeId) {
        runCatching {
            val resp = com.example.humsafar.network.HumsafarClient.api.getNodeInsights(nodeId)
            if (resp.isSuccessful) insight = resp.body()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(accent.tint.copy(alpha = 0.7f), tokens.surface)))
            .border(1.dp, accent.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { onOpenFull() }
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp).clip(CircleShape)
                        .background(accent.primary.copy(alpha = 0.16f))
                        .border(1.dp, accent.primary.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("📊", fontSize = 20.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Insights for this spot", color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        insight?.ml?.insightText?.takeIf { it.isNotBlank() }
                            ?: "How explorers engage with this spot",
                        color = accent.dark.copy(alpha = 0.85f), fontSize = 11.sp
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = accent.primary, modifier = Modifier.size(20.dp))
            }
            insight?.let { ni ->
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NodeInsightChip("⚡ ${ni.ml.engagementScore.toInt()}/100", "engagement")
                    NodeInsightChip("👣 ${ni.visits}", "visits")
                    NodeInsightChip("🔥 ${ni.popularityPct.toInt()}%", "reach")
                }
            }
        }
    }
}

@Composable
private fun NodeInsightChip(value: String, label: String) {
    val tokens = LocalAppColors.current
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(tokens.surfaceMuted).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(label, color = tokens.textTertiary, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ShimmerBox() {
    val inf = rememberInfiniteTransition(label = "sh")
    val x   by inf.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), "sx")
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF0D1F3C), Color(0xFF1E3050), Color(0xFF0D1F3C)),
                start = androidx.compose.ui.geometry.Offset(x * 800f, 0f),
                end   = androidx.compose.ui.geometry.Offset((x + 1f) * 800f, 0f)
            )
        )
    )
}

@Composable
private fun EmptyImageBox() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1628), Color(0xFF050D1A)))),
        contentAlignment = Alignment.Center
    ) {
        Text("🏛️", fontSize = 64.sp, modifier = Modifier.alpha(0.2f))
    }
}