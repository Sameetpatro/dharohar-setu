// app/src/main/java/com/example/humsafar/ui/VideoPlayerScreen.kt

package com.example.humsafar.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.humsafar.network.HumsafarClient
import com.example.humsafar.ui.components.AnimatedOrbBackground
import com.example.humsafar.ui.components.GlassCard
import com.example.humsafar.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// UI State & ViewModel
// ─────────────────────────────────────────────────────────────────────────────

sealed class VideoPlayerUiState {
    data object Loading : VideoPlayerUiState()
    data class Ready(
        val monumentName: String,
        val spotName: String,
        val videoUrl: String
    ) : VideoPlayerUiState()
    data class Error(val message: String) : VideoPlayerUiState()
}

class VideoPlayerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<VideoPlayerUiState>(VideoPlayerUiState.Loading)
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    fun loadVideo(siteId: Int, nodeId: Int) = viewModelScope.launch {
        _uiState.value = VideoPlayerUiState.Loading
        try {
            val resp = HumsafarClient.api.getSiteDetail(siteId)
            if (!resp.isSuccessful || resp.body() == null) {
                _uiState.value = VideoPlayerUiState.Error("Could not load monument information (${resp.code()})")
                return@launch
            }

            val site = resp.body()!!
            if (nodeId > 0) {
                val node = site.nodes.find { it.id == nodeId }
                if (node == null) {
                    _uiState.value = VideoPlayerUiState.Error("Spot not found at this monument")
                    return@launch
                }
                val url = node.videoUrl?.trim()
                if (url.isNullOrBlank()) {
                    _uiState.value = VideoPlayerUiState.Error("No video available for ${node.name}")
                    return@launch
                }
                _uiState.value = VideoPlayerUiState.Ready(
                    monumentName = site.name,
                    spotName = node.name,
                    videoUrl = url
                )
            } else {
                val url = site.introVideoUrl?.trim()
                if (url.isNullOrBlank()) {
                    _uiState.value = VideoPlayerUiState.Error("No introductory video available for ${site.name}")
                    return@launch
                }
                _uiState.value = VideoPlayerUiState.Ready(
                    monumentName = site.name,
                    spotName = "Introductory Tour",
                    videoUrl = url
                )
            }
        } catch (e: Exception) {
            _uiState.value = VideoPlayerUiState.Error(e.message ?: "Failed to connect to server")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VideoPlayerScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    siteId: Int,
    nodeId: Int,
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = viewModel()
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accent         = LocalAccent.current
    val tokens         = LocalAppColors.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // Create ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Playback error occurred"
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.stop()
                    exoPlayer.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(siteId, nodeId) {
        viewModel.loadVideo(siteId, nodeId)
    }

    LaunchedEffect(uiState) {
        if (uiState is VideoPlayerUiState.Ready) {
            val readyState = uiState as VideoPlayerUiState.Ready
            playbackError = null
            isBuffering = true
            exoPlayer.setMediaItem(MediaItem.fromUri(readyState.videoUrl))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1117))
    ) {
        when (val state = uiState) {
            is VideoPlayerUiState.Loading -> {
                AnimatedOrbBackground(Modifier.fillMaxSize())
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = accent.primary,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Loading visual tour…",
                            color = tokens.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            is VideoPlayerUiState.Error -> {
                AnimatedOrbBackground(Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎬", fontSize = 44.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Video Unavailable",
                                color = tokens.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                color = tokens.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = onBack,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tokens.surfaceMuted,
                                        contentColor = tokens.textPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Go Back")
                                }
                                Button(
                                    onClick = { viewModel.loadVideo(siteId, nodeId) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accent.primary,
                                        contentColor = accent.onAccent
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }

            is VideoPlayerUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // ── Header Bar ──────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .border(0.7.dp, Color(0x44FFFFFF), CircleShape)
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.monumentName,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accent.primary.copy(alpha = 0.25f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (nodeId > 0) "Spot: ${state.spotName}" else state.spotName,
                                        color = accent.tint,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // ── Video Player Area ───────────────────────────────────
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Buffering indicator
                        if (isBuffering && playbackError == null) {
                            CircularProgressIndicator(
                                color = accent.primary,
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 3.dp
                            )
                        }

                        // Playback error overlay
                        if (playbackError != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xDD000000))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("⚠️", fontSize = 40.sp)
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "Unable to play video",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = playbackError ?: "Network or decode error",
                                        color = Color(0xBBFFFFFF),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            playbackError = null
                                            isBuffering = true
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accent.primary,
                                            contentColor = accent.onAccent
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Retry Playback")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
