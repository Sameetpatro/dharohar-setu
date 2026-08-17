// HeritageDetailScreen.kt
// UPDATED: Per-section voice icons, robust TTS floating control bar with stop/pause
// The floating bar is always visible while TTS is active (speaking OR paused).
// Each section has its own speaker icon. "Hear This Page" reads the whole page.

package com.example.humsafar.ui

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.humsafar.ChatbotActivity
import com.example.humsafar.network.Node
import com.example.humsafar.network.SiteDetail
import com.example.humsafar.network.SiteImage
import com.example.humsafar.network.WeatherService
import com.example.humsafar.ui.components.AnimatedOrbBackground
import com.example.humsafar.ui.components.GlassCard
import com.example.humsafar.ui.theme.LocalAccent
import com.example.humsafar.ui.theme.LocalAppColors
import com.example.humsafar.ui.theme.GlassBorder
import com.example.humsafar.ui.theme.GlassWhite10
import com.example.humsafar.ui.theme.TextPrimary
import com.example.humsafar.ui.theme.TextSecondary
import com.example.humsafar.ui.theme.TextTertiary
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// TTS State holder — centralised so ALL composables read the same source
// ─────────────────────────────────────────────────────────────────────────────
private enum class TtsStatus { IDLE, SPEAKING, PAUSED }

@Composable
fun HeritageDetailScreen(
    @Suppress("UNUSED_PARAMETER") siteName: String,
    siteId: String,
    onBack: () -> Unit,
    onNavigateToVoice: (String, String) -> Unit,
    onNavigateToQrScan: (String) -> Unit,
    onNavigateToInsights: (Int, String) -> Unit = { _, _ -> },
    onNavigateToVideo: ((Int, Int) -> Unit)? = null,
    viewModel: HeritageDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val siteIdInt = siteId.toIntOrNull() ?: 0

    var weather by remember { mutableStateOf<WeatherService.WeatherResult?>(null) }
    var forecast by remember { mutableStateOf<List<WeatherService.ForecastDay>>(emptyList()) }

    // ── TTS State (single source of truth) ───────────────────────────────────
    var ttsStatus       by remember { mutableStateOf(TtsStatus.IDLE) }
    // Which section label is currently being spoken (for highlighting)
    var activeSectionLabel by remember { mutableStateOf("") }

    val tts = remember { TextToSpeech(context) { } }

    DisposableEffect(Unit) {
        tts.language = Locale.US
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // ── TTS Core Helpers ──────────────────────────────────────────────────────

    /** Hard stop — clears everything */
    fun stopTts() {
        tts.stop()
        ttsStatus = TtsStatus.IDLE
        activeSectionLabel = ""
    }

    /**
     * Toggle pause/resume.
     * Android TTS has no native pause; we stop and mark PAUSED.
     * Resume is not re-playable from position, so we just go IDLE.
     * (This is consistent with standard Android TTS apps.)
     */
    fun togglePause() {
        when (ttsStatus) {
            TtsStatus.SPEAKING -> {
                tts.stop()
                ttsStatus = TtsStatus.PAUSED
            }
            TtsStatus.PAUSED -> {
                // Can't resume from mid-sentence, just go idle
                ttsStatus = TtsStatus.IDLE
                activeSectionLabel = ""
            }
            TtsStatus.IDLE -> { /* no-op */ }
        }
    }

    /**
     * Speak a list of (header, body) pairs.
     * Sets ttsStatus = SPEAKING until the final utterance completes.
     */
    fun speakSequence(label: String, texts: List<Pair<String, String>>) {
        tts.stop()
        ttsStatus = TtsStatus.SPEAKING
        activeSectionLabel = label

        val progressListener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "final") {
                    ttsStatus = TtsStatus.IDLE
                    activeSectionLabel = ""
                }
            }
            override fun onError(utteranceId: String?) {
                ttsStatus = TtsStatus.IDLE
                activeSectionLabel = ""
            }
        }
        tts.setOnUtteranceProgressListener(progressListener)

        texts.forEachIndexed { index, (header, body) ->
            val isLast = index == texts.size - 1
            tts.speak(header, TextToSpeech.QUEUE_ADD, null, "hdr_$index")
            tts.speak(body, TextToSpeech.QUEUE_ADD, null, if (isLast) "final" else "body_$index")
        }
    }

    /** Speak a single named section */
    fun speakSection(sectionLabel: String, siteNameStr: String, text: String?) {
        if (text.isNullOrBlank()) return
        speakSequence(
            sectionLabel,
            listOf("Now hearing $sectionLabel of $siteNameStr" to text)
        )
    }

    /** Speak the entire page */
    fun speakEntirePage(site: SiteDetail) {
        val texts = mutableListOf<Pair<String, String>>()
        site.summary?.takeIf { it.isNotBlank() }?.let {
            texts += "Now hearing overview of ${site.name}" to it
        }
        site.history?.takeIf { it.isNotBlank() }?.let {
            texts += "Now hearing history of ${site.name}" to it
        }
        site.funFacts?.takeIf { it.isNotBlank() }?.let {
            texts += "Now hearing fun facts about ${site.name}" to it
        }
        if (texts.isNotEmpty()) speakSequence("Full Page", texts)
    }

    LaunchedEffect(siteIdInt) { viewModel.loadSite(siteIdInt) }

    LaunchedEffect(uiState) {
        val site = (uiState as? HeritageDetailUiState.Ready)?.site ?: return@LaunchedEffect
        weather  = WeatherService.fetchWeather(site.latitude, site.longitude)
        forecast = WeatherService.fetchForecast(site.latitude, site.longitude)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedOrbBackground(Modifier.fillMaxSize())

        when (val s = uiState) {
            is HeritageDetailUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏛️", fontSize = 52.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Loading…", color = TextPrimary, fontSize = 16.sp)
                    }
                }
            }

            is HeritageDetailUiState.Ready -> {
                val site       = s.site
                val siteImages = remember(site.images) { site.images.sortedBy { it.displayOrder } }
                var showTicketSheet by remember { mutableStateOf(false) }

                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        HeritageHeroGallery(
                            siteName = site.name,
                            images   = siteImages,
                            onBack   = onBack
                        )

                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            Spacer(Modifier.height(20.dp))

                            weather?.let { w ->
                                HeritageWeatherCard(weather = w)
                                Spacer(Modifier.height(16.dp))
                            }

                            if (forecast.isNotEmpty()) {
                                HeritageForecastSection(forecast = forecast)
                                Spacer(Modifier.height(20.dp))
                            }

                            HeritageBuyTicketCard(onClick = { showTicketSheet = true })
                            Spacer(Modifier.height(16.dp))

                            HeritageActionCard(
                                icon = "📷", title = "Scan Node to Start Trip",
                                subtitle = "Scan QR & explore nodes"
                            ) { onNavigateToQrScan(siteId) }
                            Spacer(Modifier.height(16.dp))

                            HeritageActionCard(
                                icon = "📊", title = "Insights about this site",
                                subtitle = "Visitor trends, time spent & AI engagement"
                            ) { onNavigateToInsights(site.id, site.name) }
                            Spacer(Modifier.height(16.dp))

                            site.introVideoUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                HeritageVideoCard(label = "Watch Intro Video") {
                                    stopTts()
                                    if (onNavigateToVideo != null) {
                                        onNavigateToVideo(site.id, 0)
                                    } else {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                            // ── "Hear This Page" button ─────────────────────
                            HeritageHearPageCard(
                                ttsStatus    = ttsStatus,
                                onSpeak      = { speakEntirePage(site) },
                                onStop       = { stopTts() }
                            )
                            Spacer(Modifier.height(16.dp))

                            // ── Overview ─────────────────────────────────────
                            site.summary?.takeIf { it.isNotBlank() }?.let { text ->
                                HeritageContentCard(
                                    title          = "Overview",
                                    body           = text,
                                    sectionLabel   = "Overview",
                                    ttsStatus      = ttsStatus,
                                    activeSectionLabel = activeSectionLabel,
                                    onSpeak        = { speakSection("Overview", site.name, text) },
                                    onStop         = { stopTts() },
                                    onTogglePause  = { togglePause() }
                                )
                                Spacer(Modifier.height(12.dp))
                            }

                            // ── History ───────────────────────────────────────
                            site.history?.takeIf { it.isNotBlank() }?.let { text ->
                                HeritageContentCard(
                                    title          = "History",
                                    body           = text,
                                    sectionLabel   = "History",
                                    ttsStatus      = ttsStatus,
                                    activeSectionLabel = activeSectionLabel,
                                    onSpeak        = { speakSection("History", site.name, text) },
                                    onStop         = { stopTts() },
                                    onTogglePause  = { togglePause() }
                                )
                                Spacer(Modifier.height(12.dp))
                            }

                            // ── Fun Facts ─────────────────────────────────────
                            site.funFacts?.takeIf { it.isNotBlank() }?.let { text ->
                                HeritageContentCard(
                                    title          = "Fun Facts",
                                    body           = text,
                                    sectionLabel   = "Fun Facts",
                                    ttsStatus      = ttsStatus,
                                    activeSectionLabel = activeSectionLabel,
                                    onSpeak        = { speakSection("Fun Facts", site.name, text) },
                                    onStop         = { stopTts() },
                                    onTogglePause  = { togglePause() }
                                )
                                Spacer(Modifier.height(16.dp))
                            }

                            if (site.nodes.isNotEmpty()) {
                                HeritageNodesSection(nodes = site.nodes.sortedBy { it.sequenceOrder })
                                Spacer(Modifier.height(16.dp))
                            }

                            site.helplineNumber?.takeIf { it.isNotBlank() }?.let { num ->
                                HeritageHelplineCard(number = num) {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                            HeritageActionCard(
                                icon = "💬", title = "Feedback",
                                subtitle = "Share your experience"
                            ) { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() }

                            // Extra bottom padding so floating bar doesn't overlap last item
                            Spacer(Modifier.height(if (ttsStatus != TtsStatus.IDLE) 140.dp else 100.dp))
                        }
                    }
                }

                // ── Floating TTS Control Bar — always visible when active ──
                if (ttsStatus != TtsStatus.IDLE) {
                    TtsControlBar(
                        ttsStatus      = ttsStatus,
                        activeSectionLabel = activeSectionLabel,
                        onTogglePause  = { togglePause() },
                        onStop         = { stopTts() },
                        modifier       = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }

                // ── Chatbot FAB ─────────────────────────────────────────────
                HeritageChatbotFab(
                    siteName = site.name, siteId = site.id,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = if (ttsStatus != TtsStatus.IDLE) 110.dp else 16.dp)
                )

                if (showTicketSheet) {
                    HeritageTicketBottomSheet(
                        siteName  = site.name,
                        onDismiss = { showTicketSheet = false }
                    )
                }
            }

            is HeritageDetailUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        s.message,
                        color = Color(0xFFFF6B6B),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// "Hear This Page" top-level card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeritageHearPageCard(
    ttsStatus: TtsStatus,
    onSpeak:   () -> Unit,
    onStop:    () -> Unit
) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf   = rememberInfiniteTransition(label = "hearPage")
    val glow  by inf.animateFloat(
        0.35f, 0.65f,
        infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        "glow"
    )
    val isActive = ttsStatus != TtsStatus.IDLE

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
                    if (isActive) "Stop Narration" else "Hear This Page",
                    color = tokens.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isActive) "Tap to stop listening" else "Listen to the full page narration",
                    color = accent.dark.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )
            }
            Icon(
                if (isActive) Icons.Default.Stop else Icons.Default.VolumeUp,
                contentDescription = null,
                tint = accent.primary.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating TTS Control Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TtsControlBar(
    ttsStatus:          TtsStatus,
    activeSectionLabel: String,
    onTogglePause:      () -> Unit,
    onStop:             () -> Unit,
    modifier:           Modifier = Modifier
) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf   = rememberInfiniteTransition(label = "tts")
    val pulse by inf.animateFloat(
        0.6f, 1f,
        infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
        "tp"
    )

    val isSpeaking = ttsStatus == TtsStatus.SPEAKING
    val isPaused   = ttsStatus == TtsStatus.PAUSED

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
            verticalAlignment = Alignment.CenterVertically,
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
                        TtsStatus.SPEAKING -> "Narrating…"
                        TtsStatus.PAUSED   -> "Paused"
                        TtsStatus.IDLE     -> ""
                    },
                    color    = tokens.textPrimary,
                    fontSize = 13.sp,
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
// Content Card WITH inline voice icon + active state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeritageContentCard(
    title:              String,
    body:               String,
    sectionLabel:       String,
    ttsStatus:          TtsStatus,
    activeSectionLabel: String,
    onSpeak:            () -> Unit,
    onStop:             () -> Unit,
    onTogglePause:      () -> Unit
) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val isThisActive = activeSectionLabel == sectionLabel && ttsStatus != TtsStatus.IDLE
    val isSpeaking   = isThisActive && ttsStatus == TtsStatus.SPEAKING
    val isPaused     = isThisActive && ttsStatus == TtsStatus.PAUSED

    val inf  = rememberInfiniteTransition(label = "sec_$sectionLabel")
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

            // ── Voice control pill ──────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (isThisActive) {
                    // Pause/Resume
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
                    // Stop
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
                    // Speaker / play icon — tap to start
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accent.tint.copy(alpha = 0.55f))
                            .border(
                                0.7.dp,
                                accent.primary.copy(alpha = 0.35f),
                                CircleShape
                            )
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

        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
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
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Text(body, color = TextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero Gallery
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeritageHeroGallery(siteName: String, images: List<SiteImage>, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    var currentIndex by remember { mutableStateOf(0) }
    LaunchedEffect(listState.firstVisibleItemIndex) { currentIndex = listState.firstVisibleItemIndex }

    Box(Modifier.fillMaxWidth().height(360.dp)) {
        if (images.isNotEmpty()) {
            LazyRow(state = listState, modifier = Modifier.fillMaxSize(), userScrollEnabled = true) {
                itemsIndexed(images) { _, img ->
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(img.imageUrl).crossfade(400).build(),
                        contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight()
                    )
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xCC1A1A2E))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("${currentIndex + 1} / ${images.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }

            if (images.size > 1) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 52.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    images.forEachIndexed { i, _ ->
                        Box(
                            Modifier.size(if (i == currentIndex) 10.dp else 6.dp).clip(CircleShape)
                                .background(if (i == currentIndex) LocalAccent.current.primary else Color.White.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        } else {
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF0D0D1A)))),
                contentAlignment = Alignment.Center
            ) { Text("🏛️", fontSize = 80.sp, modifier = Modifier.alpha(0.3f)) }
        }

        Box(Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color(0xE61A1A2E), Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF050D1A)))))

        Box(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)
                .size(44.dp).clip(CircleShape).background(Color(0xBB1A1A2E))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp)) }

        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(siteName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Weather
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeritageWeatherCard(weather: WeatherService.WeatherResult) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val suggestions = remember(weather) { WeatherService.weatherSuggestions(weather.tempC, weather.weatherCode) }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        tint = accent.primary.copy(alpha = 0.10f),
        borderColor = accent.primary.copy(alpha = 0.22f)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("%.0f°C".format(weather.tempC), color = tokens.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(weather.description, color = tokens.textSecondary, fontSize = 14.sp)
                }
                Text("🌡️", fontSize = 40.sp)
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Suggestions:", color = tokens.textTertiary, fontSize = 12.sp)
                suggestions.forEach { Text("• $it", color = accent.primary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

@Composable
private fun HeritageForecastSection(forecast: List<WeatherService.ForecastDay>) {
    Column {
        Text("7-Day Forecast", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            forecast.forEach { day ->
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(GlassWhite10)
                        .border(0.7.dp, GlassBorder, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.date.takeLast(5), color = TextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("%.0f°".format(day.tempMax), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("%.0f°".format(day.tempMin), color = TextTertiary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Buy Ticket Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeritageBuyTicketCard(onClick: () -> Unit) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf     = rememberInfiniteTransition(label = "ticket")
    val glow    by inf.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse), "tg")
    val shimmer by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), "ts")

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.dark.copy(alpha = 0.08f),
                        accent.tint.copy(alpha = 0.9f),
                        tokens.surface
                    )
                )
            )
            .border(1.5.dp, Brush.linearGradient(colorStops = arrayOf(
                (shimmer + 0.0f).rem(1f) to accent.primary.copy(alpha = glow),
                (shimmer + 0.4f).rem(1f) to accent.dark.copy(alpha = glow * 0.5f),
                (shimmer + 0.8f).rem(1f) to accent.primary.copy(alpha = glow)
            )), RoundedCornerShape(20.dp))
            .clickable { onClick() }.padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accent.primary.copy(alpha = 0.22f), accent.tint.copy(alpha = 0.4f))))
                    .border(1.dp, accent.primary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("🎟️", fontSize = 22.sp) }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Buy Entry Ticket", color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Skip the queue • Fast-track entry", color = accent.dark.copy(alpha = 0.75f), fontSize = 12.sp)
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.primary.copy(alpha = 0.12f))
                    .border(0.5.dp, accent.primary.copy(alpha = 0.3f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) { Text("›", color = accent.primary, fontSize = 20.sp, fontWeight = FontWeight.Light) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ticket Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeritageTicketBottomSheet(siteName: String, onDismiss: () -> Unit) {
    val context    = LocalContext.current
    val accent     = LocalAccent.current
    val tokens     = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    data class TicketTier(val emoji: String, val name: String, val price: String, val desc: String)
    val tiers = listOf(
        TicketTier("🆓", "Free Entry",       "₹0",   "Children under 15 & local residents"),
        TicketTier("🎓", "Student",           "₹50",  "Valid student ID required at entry"),
        TicketTier("👤", "Adult (Indian)",    "₹100", "Indian nationals, general entry"),
        TicketTier("🌍", "Foreign National",  "₹500", "International visitors")
    )
    var selected by remember { mutableStateOf(1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = tokens.surface,
        contentColor   = tokens.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(50)).background(tokens.divider))
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent.tint, tokens.surfaceMuted)))
                        .border(1.dp, accent.primary.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("🎟️", fontSize = 24.sp) }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text("Entry Tickets", color = tokens.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(siteName, color = tokens.textTertiary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(accent.tint.copy(alpha = 0.45f)).border(0.7.dp, accent.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ℹ️", fontSize = 14.sp); Spacer(Modifier.size(8.dp))
                    Text("Tickets valid for same-day entry only. Carry valid ID proof.", color = accent.dark.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            tiers.forEachIndexed { i, tier ->
                val isSelected = selected == i
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) accent.tint.copy(alpha = 0.75f) else tokens.surfaceMuted)
                        .border(if (isSelected) 1.5.dp else 0.7.dp, if (isSelected) accent.primary.copy(alpha = 0.55f) else tokens.border, RoundedCornerShape(16.dp))
                        .clickable { selected = i }.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(22.dp).clip(CircleShape)
                                .background(if (isSelected) accent.primary else tokens.surface)
                                .border(1.5.dp, if (isSelected) accent.primary else tokens.borderStrong, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { if (isSelected) Text("✓", color = accent.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.size(12.dp)); Text(tier.emoji, fontSize = 20.sp); Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tier.name, color = if (isSelected) accent.dark else tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(tier.desc, color = tokens.textTertiary, fontSize = 11.sp)
                        }
                        Text(tier.price, color = if (isSelected) accent.primary else tokens.textSecondary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(accent.primary, accent.dark)))
                    .border(1.dp, accent.primary.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tickets.dharoharsetu.in/buy?site=$siteName&tier=${tiers[selected].name}")))
                        onDismiss()
                    }.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎟️", fontSize = 16.sp); Spacer(Modifier.size(10.dp))
                    Text("Proceed to Payment  •  ${tiers[selected].price}", color = accent.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(tokens.surfaceMuted)
                    .border(0.7.dp, tokens.border, RoundedCornerShape(50)).clickable { onDismiss() }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Cancel", color = tokens.textSecondary, fontSize = 14.sp) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Video Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeritageVideoCard(label: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.tint.copy(alpha = 0.85f), tokens.surface)
                )
            )
            .border(1.dp, accent.primary.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent.primary, accent.dark))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PlayArrow, null, tint = accent.onAccent, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Tap to watch", color = tokens.textTertiary, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = accent.primary.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nodes Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeritageNodesSection(nodes: List<Node>) {
    Column {
        Text("📍 Nodes at this site", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            nodes.forEach { node ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GlassWhite10)
                        .border(0.7.dp, GlassBorder, RoundedCornerShape(12.dp)).padding(14.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2E5A8F).copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) { Text("${node.sequenceOrder}", color = LocalAccent.current.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.size(12.dp))
                        Text(node.name, color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpline Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeritageHelplineCard(number: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.tint.copy(alpha = 0.6f), tokens.surface)
                )
            )
            .border(1.dp, accent.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("📞", fontSize = 28.sp); Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Helpline", color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(number, color = tokens.textSecondary, fontSize = 13.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = accent.primary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeritageActionCard(
    icon: String, title: String, subtitle: String,
    onClick: () -> Unit
) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current
    val inf  = rememberInfiniteTransition(label = "action")
    val glow by inf.animateFloat(0.35f, 0.65f, infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse), "glow")
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.tint.copy(alpha = 0.88f),
                        tokens.surface,
                        tokens.surfaceMuted
                    )
                )
            )
            .border(1.dp, accent.primary.copy(alpha = glow), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp); Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = tokens.textTertiary, fontSize = 11.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = accent.primary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chatbot FAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeritageChatbotFab(siteName: String, siteId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent.primary, accent.dark)))
                .border(2.dp, Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x22FFFFFF))), CircleShape)
                .clickable {
                    context.startActivity(Intent(context, ChatbotActivity::class.java).apply {
                        putExtra("SITE_NAME", siteName)
                        putExtra("SITE_ID", siteId.toString())
                        putExtra("NODE_ID", "")
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = accent.onAccent, modifier = Modifier.size(22.dp))
                Text("Ask", color = accent.onAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}