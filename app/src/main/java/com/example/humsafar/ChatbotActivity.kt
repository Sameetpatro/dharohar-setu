// app/src/main/java/com/example/humsafar/ChatbotActivity.kt

package com.example.humsafar

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.humsafar.auth.AuthManager
import com.example.humsafar.data.ActiveSiteManager
import com.example.humsafar.data.ActiveSiteManager.activeNodeId
import com.example.humsafar.data.TripManager
import com.example.humsafar.models.ChatMessage as UiChatMessage  // ← RENAMED for UI
import com.example.humsafar.models.ChatHistoryItem as ApiChatMessage  // ← matches API schema
import com.example.humsafar.models.ChatRequest
import com.example.humsafar.network.HumsafarClient
import com.example.humsafar.prefs.AppPreferences
import com.example.humsafar.prefs.LanguagePreferences
import com.example.humsafar.ui.components.AnimatedOrbBackground
import com.example.humsafar.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

// ─────────────────────────────────────────────────────────────────────────────
// Intent extra keys — keep these for manual launches (e.g. from HeritageDetail)
// ─────────────────────────────────────────────────────────────────────────────

object ChatbotExtras {
    const val SITE_ID   = "SITE_ID"
    const val SITE_NAME = "SITE_NAME"
    const val NODE_ID   = "NODE_ID"
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────────────────────────────────────

class ChatbotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── Resolve site_id ───────────────────────────────────────────────
        // Primary: Intent extra (when launched from a specific site/node screen or QR result)
        // Fallback: ActiveSiteManager (active physical site detected via GPS)
        val intentSiteId   = intent.getStringExtra(ChatbotExtras.SITE_ID)?.toIntOrNull()
        val activeSiteId   = ActiveSiteManager.activeSiteId
        val resolvedSiteId = intentSiteId ?: activeSiteId ?: -1

        // ── Resolve node_id ───────────────────────────────────────────────
        // Primary: Intent extra (when launched from a specific node screen)
        // Fallback: ActiveSiteManager (last scanned node)
        val intentNodeId   = intent.getStringExtra(ChatbotExtras.NODE_ID)?.toIntOrNull()
        val activeNodeId   = ActiveSiteManager.activeNodeId.value
        val resolvedNodeId = intentNodeId ?: activeNodeId

        // ── Site name ─────────────────────────────────────────────────────
        val intentSiteName = intent.getStringExtra(ChatbotExtras.SITE_NAME)
        val resolvedSiteName = if (!intentSiteName.isNullOrBlank()) {
            intentSiteName
        } else {
            ActiveSiteManager.activeSiteName.ifBlank { "Heritage Site" }
        }

        Log.d("ChatbotActivity", "SITE_ID_DEBUG: intentSiteId=$intentSiteId | activeSiteId=$activeSiteId | resolvedSiteId=$resolvedSiteId | intentNodeId=$intentNodeId | activeNodeId=$activeNodeId | resolvedNodeId=$resolvedNodeId")

        Log.d("ChatbotActivity", """
            ▶ LAUNCHED
              intentSiteId=$intentSiteId  activeSiteId=$activeSiteId  → resolved=$resolvedSiteId
              intentNodeId=$intentNodeId  activeNodeId=$activeNodeId  → resolved=$resolvedNodeId
              siteName='$resolvedSiteName'
        """.trimIndent())

        if (resolvedSiteId == -1) {
            Log.e("ChatbotActivity",
                "⚠️ Could not resolve site_id. " +
                        "User may not be inside a geofence and no SITE_ID was passed in the Intent.")
        }

        setContent {
            val appPrefs = remember { AppPreferences(applicationContext) }
            HumsafarTheme(accent = appPrefs.getAccent()) {
                ChatbotScreen(
                    siteName = resolvedSiteName,
                    siteId   = resolvedSiteId.toString(),
                    nodeId   = resolvedNodeId?.toString(),
                    onBack   = { finish() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Network helper
// ─────────────────────────────────────────────────────────────────────────────

suspend fun callChatApi(
    message : String,
    siteId  : String,
    nodeId  : String?,
    history : List<UiChatMessage>,
    langCode: String? = null
): String = withContext(Dispatchers.IO) {
    try {
        val siteIdInt = siteId.toIntOrNull()
        val nodeIdInt = nodeId?.toIntOrNull()

        if ((siteIdInt == null || siteIdInt == -1) && (nodeIdInt == null || nodeIdInt == -1)) {
            Log.e("ChatbotActivity", "⚠️ callChatApi: invalid siteId='$siteId' and nodeId='$nodeId'")
            return@withContext "Error: not inside a heritage site. Please scan a QR code or move closer to the entrance."
        }

        Log.d("ChatbotActivity",
            "→ POST /chat/  site_id=$siteIdInt  node_id=$nodeIdInt  msg='${message.take(60)}'")

        // ← FIXED: Convert UI messages to API messages
        val historyItems = history
            .filter { !it.isLoading }
            .map { msg ->
                ApiChatMessage(  // ← Use ApiChatMessage
                    role    = if (msg.isUser) "user" else "assistant",
                    content = msg.text
                )
            }

        // Resolve persistence fields (best-effort — backend silently skips
        // history writes if the user isn't registered).
        val firebaseUser = AuthManager.currentUser.value
        val firebaseUid  = firebaseUser?.uid
        val activeTrip   = TripManager.current()
        val tripId       = activeTrip.tripId.takeIf { it != 0 }

        val request = ChatRequest(
            siteId       = siteIdInt,    // heritage_sites.id PK — from DB via GPS
            nodeId       = nodeIdInt,    // nodes.id PK — set after QR scan, null otherwise
            message      = message,
            history      = historyItems,
            firebaseUid  = firebaseUid,  // ← drives user_chat_history persistence
            tripId       = tripId,       // ← null when no active trip
            langCode     = langCode      // ← e.g. "en-IN" / "hi-IN"
        )
        Log.d("ChatbotActivity", "API_REQUEST_DEBUG: siteId=$siteIdInt nodeId=$nodeIdInt message=${message.take(30)}")
        val resp = HumsafarClient.api.sendChat(request)
        Log.d("ChatbotActivity", "← /chat/ responded ${resp.code()}")

        if (resp.isSuccessful && resp.body() != null) {
            resp.body()!!.reply
        } else {
            "Server error ${resp.code()} — please try again"
        }
    } catch (e: Exception) {
        Log.e("ChatbotActivity", "callChatApi exception: ${e.message}", e)
        "Connection failed: ${e.message}"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen — unchanged from your original
// ─────────────────────────────────────────────────────────────────────────────

private data class QuickChip(val label: String, val query: String)

@Composable
fun ChatbotScreen(
    siteName : String,
    siteId   : String,
    nodeId   : String?,
    onBack   : () -> Unit
) {
    val accent = LocalAccent.current
    val tokens = LocalAppColors.current

    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    val chips = remember(siteName) {
        listOf(
            QuickChip("📜 History",      "Tell me the history of $siteName"),
            QuickChip("🏗️ Who built it", "Who built $siteName?"),
            QuickChip("🌅 Best time",    "What is the best time to visit $siteName?"),
            QuickChip("✨ Fun facts",    "Tell me some fun facts about $siteName"),
            QuickChip("🎭 Legends",      "What are the legends and myths around $siteName?")
        )
    }

    val messages = remember {
        mutableStateListOf(
            UiChatMessage(  // ← Use UiChatMessage
                text   = "👋 Welcome to $siteName!\n\nI'm your AI heritage guide. Ask me anything — history, architecture, legends, or visiting tips!",
                isUser = false
            )
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val langPrefs = remember(context) { LanguagePreferences(context) }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        messages.add(UiChatMessage(text = userText, isUser = true))  // ← Use UiChatMessage
        val historySnapshot = messages.toList()
        val loadingMsg      = UiChatMessage(text = "", isUser = false, isLoading = true)  // ← Use UiChatMessage
        messages.add(loadingMsg)

        scope.launch {
            listState.animateScrollToItem(messages.size - 1)
            val reply = callChatApi(
                message  = userText,
                siteId   = siteId,
                nodeId   = nodeId,
                history  = historySnapshot,
                langCode = langPrefs.selectedLanguage.bcp47Code
            )
            val botMsg = UiChatMessage(text = reply, isUser = false)  // ← Use UiChatMessage
            val idx = messages.indexOf(loadingMsg)
            if (idx != -1) messages[idx] = botMsg else messages.add(botMsg)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedOrbBackground(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(tokens.surface.copy(alpha = 0.97f), tokens.surfaceMuted.copy(alpha = 0.93f))
                        )
                    )
                    .drawBehind {
                        drawLine(tokens.divider, Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx())
                    }
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GlassWhite15)
                            .border(0.5.dp, GlassBorder, CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint     = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(accent.primary, accent.dark))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏛️", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text       = siteName,
                            color      = TextPrimary,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4ADE80))
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                // Show if in node context vs site context
                                text     = if (nodeId != null)
                                    "Node Guide · Online"
                                else
                                    "Heritage Guide · Online",
                                color    = TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // ── Messages ──────────────────────────────────────────────────
            LazyColumn(
                state               = listState,
                modifier            = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding      = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { msg: UiChatMessage ->  // ← Use UiChatMessage
                    GlassChatBubble(message = msg)
                }
            }

            // ── Quick chips ───────────────────────────────────────────────
            val lastMsg = messages.lastOrNull()
            if (lastMsg != null && !lastMsg.isUser && !lastMsg.isLoading) {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.padding(bottom = 8.dp)
                ) {
                    items(chips) { chip: QuickChip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(GlassWhite15)
                                .border(0.5.dp, GlassBorder, RoundedCornerShape(50))
                                .clickable { sendMessage(chip.query) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(chip.label, color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── Input bar ─────────────────────────────────────────────────
            val canSend = inputText.isNotBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(tokens.surfaceMuted.copy(alpha = 0.6f), tokens.surface.copy(alpha = 0.98f))
                        )
                    )
                    .drawBehind {
                        drawLine(tokens.divider, Offset(0f, 0f), Offset(size.width, 0f), 0.5.dp.toPx())
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Voice button - opens VoiceChatActivity
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF8A2BE2), Color(0xFF6B1FA8)))
                            )
                            .border(0.5.dp, Color(0x44FFFFFF), CircleShape)
                            .clickable {
                                context.startActivity(
                                    android.content.Intent(context, VoiceChatActivity::class.java).apply {
                                        putExtra(VoiceChatExtras.SITE_NAME, siteName)
                                        putExtra(VoiceChatExtras.SITE_ID, siteId)
                                        nodeId?.let { putExtra(VoiceChatExtras.NODE_ID, it) }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice Chat",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(GlassWhite15)
                            .border(0.7.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value         = inputText,
                            onValueChange = { inputText = it },
                            textStyle     = TextStyle(color = TextPrimary, fontSize = 15.sp),
                            modifier      = Modifier.fillMaxWidth(),
                            maxLines      = 4,
                            decorationBox = { inner ->
                                if (inputText.isEmpty()) {
                                    Text(
                                        text     = "Ask about $siteName…",
                                        color    = TextTertiary,
                                        fontSize = 15.sp
                                    )
                                }
                                inner()
                            }
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend)
                                    Brush.linearGradient(listOf(accent.primary, accent.dark))
                                else
                                    Brush.linearGradient(listOf(GlassWhite15, GlassWhite15))
                            )
                            .border(
                                0.5.dp,
                                if (canSend) Color(0x44FFFFFF) else GlassBorder,
                                CircleShape
                            )
                            .clickable(enabled = canSend) {
                                val t = inputText.trim()
                                inputText = ""
                                sendMessage(t)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint     = if (canSend) accent.onAccent else TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat bubble — unchanged
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GlassChatBubble(message: UiChatMessage) {  // ← Use UiChatMessage
    val accent = LocalAccent.current
    val isUser      = message.isUser
    val alignment   = if (isUser) Alignment.End else Alignment.Start
    val bubbleShape = RoundedCornerShape(
        topStart    = 20.dp,
        topEnd      = 20.dp,
        bottomStart = if (isUser) 20.dp else 5.dp,
        bottomEnd   = if (isUser) 5.dp  else 20.dp
    )

    Column(
        modifier             = Modifier.fillMaxWidth(),
        horizontalAlignment  = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(
                    if (isUser)
                        Brush.linearGradient(listOf(accent.primary, accent.dark))
                    else
                        Brush.linearGradient(listOf(GlassWhite20, GlassWhite15))
                )
                .border(
                    0.5.dp,
                    if (isUser)
                        Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x11FFFFFF)))
                    else
                        Brush.verticalGradient(listOf(GlassBorderBright, GlassBorder)),
                    bubbleShape
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (message.isLoading) {
                TypingIndicator()
            } else {
                Text(
                    text       = message.text,
                    color      = if (isUser) accent.onAccent else TextPrimary,
                    fontSize   = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Typing indicator — unchanged
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")

    @Composable
    fun Dot(offsetMs: Int) {
        val scale by transition.animateFloat(
            initialValue   = 0.7f,
            targetValue    = 1.35f,
            animationSpec  = infiniteRepeatable(
                animation  = tween(450, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(offsetMs)
            ),
            label = "dot_$offsetMs"
        )
        Box(
            Modifier
                .size(7.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(TextTertiary)
        )
    }

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier              = Modifier.padding(vertical = 2.dp)
    ) {
        Dot(0)
        Dot(150)
        Dot(300)
    }
}