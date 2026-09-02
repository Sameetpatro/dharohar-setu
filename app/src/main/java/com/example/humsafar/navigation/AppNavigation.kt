// app/src/main/java/com/example/humsafar/navigation/AppNavigation.kt

package com.example.humsafar.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.humsafar.auth.AuthManager
import com.example.humsafar.data.ActiveSiteManager
import com.example.humsafar.data.QuizTrigger
import com.example.humsafar.data.TripManager
import com.example.humsafar.network.HumsafarClient
import com.example.humsafar.prefs.AppPreferences
import com.example.humsafar.ui.*
import com.example.humsafar.ui.bonus.BonusGameHost
import com.example.humsafar.ui.theme.Accent
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNavigation(
    appPrefs: AppPreferences,
    onAccentChange: (Accent) -> Unit
) {
    val navController = rememberNavController()

    val currentUser by AuthManager.currentUser.collectAsState()
    val needsOnboarding = !appPrefs.onboardingComplete
    val startDest = when {
        needsOnboarding -> "splash"
        currentUser != null -> "home"
        else -> "login"
    }

    // Geofence-exit trigger: when the user leaves a site mid-trip we queue the
    // final quiz. Route to it here (immediately if foreground, else on resume).
    val pendingQuiz by QuizTrigger.pending.collectAsState()
    LaunchedEffect(pendingQuiz) {
        pendingQuiz?.let { pq ->
            navController.navigate(
                tripMomentHubRoute(pq.tripId, pq.siteId, pq.siteName, pq.visitedCount, pq.totalCount)
            ) { popUpTo("home") { inclusive = false } }
            QuizTrigger.consume()
        }
    }

    // ── App Links / Deep Link Trigger ─────────────────────────────────────────
    val pendingDeepLink by DeepLinkHandler.pendingDeepLink.collectAsState()
    var deepLinkError by remember { mutableStateOf<String?>(null) }
    var isResolvingDeepLink by remember { mutableStateOf(false) }

    LaunchedEffect(pendingDeepLink) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        isResolvingDeepLink = true
        try {
            val resp = HumsafarClient.api.scanQr(target.nodeIdentifier)
            if (resp.isSuccessful && resp.body() != null) {
                val result = resp.body()!!
                if (result.isValid && result.nodeId != null && result.siteId != null) {
                    ActiveSiteManager.onNodeScanned(result.nodeId)
                    navController.navigate(
                        nodeDetailRoute(result.nodeId, result.siteId, result.isKingNode)
                    ) {
                        launchSingleTop = true
                    }
                } else {
                    deepLinkError = "The requested node (${target.nodeIdentifier}) could not be found."
                }
            } else {
                deepLinkError = "Node not found (${resp.code()}). Please verify the link or QR code."
            }
        } catch (e: Exception) {
            deepLinkError = "Network error loading node: ${e.message ?: "Unknown error"}"
        } finally {
            isResolvingDeepLink = false
            DeepLinkHandler.consume()
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDest,
            enterTransition = {
                fadeIn(tween(280)) +
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(320))
            },
            exitTransition = {
                fadeOut(tween(220)) +
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(320))
            },
            popEnterTransition = {
                fadeIn(tween(280)) +
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(320))
            },
            popExitTransition = {
                fadeOut(tween(220)) +
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(320))
            }
        ) {

            composable("splash") {
                SplashScreen(
                    onDone = {
                        navController.navigate("onboarding") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }

            composable("onboarding") {
                OnboardingScreen(
                    onAccentPicked = onAccentChange,
                    onFinish = {
                        appPrefs.onboardingComplete = true
                        val target = if (currentUser != null) "home" else "login"
                        navController.navigate(target) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            composable("login") {
                LoginScreen(
                    onSignupClick  = { navController.navigate("signup") },
                    onBypassClick  = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onNeedPhoneNumber = {
                        navController.navigate("collect_phone") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            composable("signup") {
                SignUpScreen(
                    onLoginClick    = { navController.popBackStack() },
                    onBypassClick   = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onSignUpSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onNeedPhoneNumber = {
                        navController.navigate("collect_phone") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            // ── Collect mobile number (after Google sign-in) ──────────────────────
            composable("collect_phone") {
                CollectPhoneScreen(
                    onDone = {
                        navController.navigate("home") {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("home") {
                MapScreen(
                    onNavigateToVoice    = { name, id -> navController.navigate(voiceChatRoute(name, id, "")) },
                    onNavigateToDetail   = { name, id -> navController.navigate(heritageDetailRoute(name, id)) },
                    onNavigateToProfile  = { navController.navigate("profile") },
                    onNavigateToQrScan   = { siteId ->
                        navController.navigate(qrScanRoute("Site", siteId.toString()))
                    },
                    onNavigateToSiteInfo = { siteId, siteName ->
                        navController.navigate(siteInfoRoute(siteId, siteName))
                    },
                    onNavigateToStore    = { navController.navigate("store") }
                )
            }

            // ── Coupon store + spinning wheels ─────────────────────────────────
            composable("store") {
                StoreScreen(
                    onBack = { navController.popBackStack() },
                    onSpin = { tier, kind, sId ->
                        navController.navigate("spin/$tier/$kind/$sId")
                    },
                    onOpenCoupons = { navController.navigate("coupons") }
                )
            }
            composable(
                route = "spin/{tier}/{kind}/{siteId}",
                arguments = listOf(
                    navArgument("tier")   { type = NavType.StringType },
                    navArgument("kind")   { type = NavType.StringType },
                    navArgument("siteId") { type = NavType.IntType }
                )
            ) { backStack ->
                val tier   = backStack.arguments?.getString("tier") ?: "normal"
                val kind   = backStack.arguments?.getString("kind") ?: "hotel"
                val siteId = backStack.arguments?.getInt("siteId") ?: -1
                SpinWheelScreen(
                    tier = tier,
                    kind = kind,
                    siteId = siteId,
                    onBack = { navController.popBackStack() },
                    onViewCoupons = {
                        navController.navigate("coupons") {
                            popUpTo("store") { inclusive = false }
                        }
                    }
                )
            }
            composable("coupons") {
                MyCouponsScreen(onBack = { navController.popBackStack() })
            }

            // ── Site Info ─────────────────────────────────────────────────────
            composable(
                route = "site_info/{siteId}/{siteName}",
                arguments = listOf(
                    navArgument("siteId")   { type = NavType.IntType },
                    navArgument("siteName") { type = NavType.StringType }
                )
            ) { backStack ->
                val siteId   = backStack.arguments?.getInt("siteId") ?: 0
                val siteName = backStack.arguments?.getString("siteName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "Heritage Site"
                val site = com.example.humsafar.data.HeritageRepository.sites
                    .find { it.id == siteId.toString() }
                if (site != null) {
                    SiteInfoScreen(
                        siteId    = siteId,
                        siteName  = siteName,
                        latitude  = site.latitude,
                        longitude = site.longitude,
                        onBack    = { navController.popBackStack() },
                        onExplore = { name, id ->
                            navController.navigate(heritageDetailRoute(name, id))
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("Site not found")
                    }
                }
            }

            // ── Heritage Detail ───────────────────────────────────────────────
            composable(
                route     = "detail/{siteName}/{siteId}",
                arguments = listOf(
                    navArgument("siteName") { type = NavType.StringType },
                    navArgument("siteId")   { type = NavType.StringType }
                )
            ) { backStack ->
                val siteName = backStack.arguments?.getString("siteName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "Heritage Site"
                val siteId   = backStack.arguments?.getString("siteId") ?: ""
                HeritageDetailScreen(
                    siteName             = siteName,
                    siteId               = siteId,
                    onBack               = { navController.popBackStack() },
                    onNavigateToVoice    = { name, id -> navController.navigate(voiceChatRoute(name, id, "")) },
                    onNavigateToQrScan   = { id -> navController.navigate(qrScanRoute("Site", id.toString())) },
                    onNavigateToInsights = { id, name -> navController.navigate(insightsRoute(id, name)) }
                )
            }

            // ── Node detail ───────────────────────────────────────────────────
            composable(
                route     = "node/{nodeId}/{siteId}/{isKing}",
                arguments = listOf(
                    navArgument("nodeId") { type = NavType.IntType },
                    navArgument("siteId") { type = NavType.IntType },
                    navArgument("isKing") { type = NavType.BoolType }
                )
            ) { backStack ->
                val nodeId = backStack.arguments?.getInt("nodeId") ?: 0
                val siteId = backStack.arguments?.getInt("siteId") ?: 0
                val isKing = backStack.arguments?.getBoolean("isKing") ?: false
                NodeDetailScreen(
                    nodeId                 = nodeId,
                    siteId                 = siteId,
                    isKing                 = isKing,
                    onBack                 = { navController.popBackStack() },
                    onNavigateToQr         = { id: Long -> navController.navigate(qrScanRoute("Site", id.toString())) },
                    onNavigateToVoice      = { name: String, _: String ->
                        navController.navigate(voiceChatRoute(name, siteId.toString(), nodeId.toString()))
                    },
                    onNavigateToDirections = { dirSiteId: Int, dirSiteName: String ->
                        navController.navigate(directionsRoute(dirSiteId, dirSiteName))
                    },
                    onNavigateToReview     = { tripId: Int, cSiteId: Int, cSiteName: String, visited: Int, total: Int ->
                        navController.navigate(reviewRoute(tripId, cSiteId, cSiteName, visited, total)) {
                            popUpTo("home") { inclusive = false }
                        }
                    },
                    onNavigateToQuiz       = { tripId: Int, cSiteId: Int, cSiteName: String, visited: Int, total: Int ->
                        navController.navigate(tripMomentHubRoute(tripId, cSiteId, cSiteName, visited, total)) {
                            popUpTo("home") { inclusive = false }
                        }
                    },
                    onNavigateToInstants   = { iNodeId, iSiteId, iNodeName ->
                        navController.navigate(nodeInstantsRoute(iNodeId, iSiteId, iNodeName))
                    },
                    onNavigateToAmenity    = { amenityId ->
                        navController.navigate(amenityDetailRoute(amenityId))
                    },
                    onNavigateToComments   = { cNodeId, cSiteId, cNodeName ->
                        navController.navigate(nodeCommentsRoute(cNodeId, cSiteId, cNodeName))
                    },
                    onNavigateToInsights   = { iSiteId, iSiteName ->
                        navController.navigate(insightsRoute(iSiteId, iSiteName))
                    },
                    onNavigateToVideo = { vSiteId: Int, vNodeId: Int ->
                        navController.navigate(videoPlayerRoute(vSiteId, vNodeId))
                    }
                )
            }

            // ── Node Comments ─────────────────────────────────────────────────
            composable(
                route     = "node_comments/{nodeId}/{siteId}/{nodeName}",
                arguments = listOf(
                    navArgument("nodeId")   { type = NavType.IntType },
                    navArgument("siteId")   { type = NavType.IntType },
                    navArgument("nodeName") { type = NavType.StringType }
                )
            ) { backStack ->
                val nodeId   = backStack.arguments?.getInt("nodeId") ?: 0
                val siteId   = backStack.arguments?.getInt("siteId") ?: 0
                val nodeName = backStack.arguments?.getString("nodeName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "this spot"
                NodeCommentsScreen(
                    nodeId   = nodeId,
                    siteId   = siteId,
                    nodeName = nodeName,
                    onBack   = { navController.popBackStack() }
                )
            }

            // ── Amenity detail ────────────────────────────────────────────────
            composable(
                route     = "amenity/{amenityId}",
                arguments = listOf(
                    navArgument("amenityId") { type = NavType.IntType }
                )
            ) { backStack ->
                val amenityId = backStack.arguments?.getInt("amenityId") ?: return@composable
                AmenityDetailScreen(
                    amenityId = amenityId,
                    onBack    = { navController.popBackStack() }
                )
            }

            // ── Directions screen ─────────────────────────────────────────────
            composable(
                route     = "directions/{siteId}/{siteName}",
                arguments = listOf(
                    navArgument("siteId")   { type = NavType.IntType },
                    navArgument("siteName") { type = NavType.StringType }
                )
            ) { backStack ->
                val siteId   = backStack.arguments?.getInt("siteId") ?: 0
                val siteName = backStack.arguments?.getString("siteName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "Heritage Site"
                DirectionsScreen(
                    siteId   = siteId,
                    siteName = siteName,
                    onBack   = { navController.popBackStack() }
                )
            }

            // ── Review screen ─────────────────────────────────────────────────
            composable(
                route     = "review/{tripId}/{siteId}/{siteName}/{visitedCount}/{totalCount}",
                arguments = listOf(
                    navArgument("tripId")       { type = NavType.IntType },
                    navArgument("siteId")       { type = NavType.IntType },
                    navArgument("siteName")     { type = NavType.StringType },
                    navArgument("visitedCount") { type = NavType.IntType },
                    navArgument("totalCount")   { type = NavType.IntType }
                )
            ) { backStack ->
                val tripId       = backStack.arguments?.getInt("tripId") ?: 0
                val siteId       = backStack.arguments?.getInt("siteId") ?: 0
                val siteName     = backStack.arguments?.getString("siteName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "Heritage Site"
                val visitedCount = backStack.arguments?.getInt("visitedCount") ?: 0
                val totalCount   = backStack.arguments?.getInt("totalCount") ?: 0
                ReviewScreen(
                    tripId                     = tripId,
                    siteId                     = siteId,
                    siteName                   = siteName,
                    visitedCount               = visitedCount,
                    totalCount                 = totalCount,
                    onNavigateToTripCompletion = {
                        navController.navigate(tripCompletionRoute(siteId, siteName, visitedCount, totalCount)) {
                            popUpTo("home") { inclusive = false }
                        }
                    },
                    onSkip = {
                        navController.navigate(tripCompletionRoute(siteId, siteName, visitedCount, totalCount)) {
                            popUpTo("home") { inclusive = false }
                        }
                    }
                )
            }

            // ── Trip completion screen ────────────────────────────────────────
            composable(
                route     = "trip_completion/{siteId}/{siteName}/{visitedCount}/{totalCount}",
                arguments = listOf(
                    navArgument("siteId")       { type = NavType.IntType },
                    navArgument("siteName")     { type = NavType.StringType },
                    navArgument("visitedCount") { type = NavType.IntType },
                    navArgument("totalCount")   { type = NavType.IntType }
                )
            ) { backStack ->
                val siteId       = backStack.arguments?.getInt("siteId") ?: 0
                val siteName     = backStack.arguments?.getString("siteName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "Heritage Site"
                val visitedCount = backStack.arguments?.getInt("visitedCount") ?: 0
                val totalCount   = backStack.arguments?.getInt("totalCount") ?: 0
                TripCompletionScreen(
                    siteId                   = siteId,
                    siteName                 = siteName,
                    visitedNodesCount        = visitedCount,
                    totalNodesCount          = totalCount,
                    onExploreRecommendations = {
                        TripManager.clear()
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    onSkip = {
                        TripManager.clear()
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }

            // ── Insights (per-site analytics + per-node breakdown + ML) ─────────
            composable(
                route = "insights/{siteId}/{siteName}",
                arguments = listOf(
                    navArgument("siteId")   { type = NavType.IntType },
                    navArgument("siteName") { type = NavType.StringType }
                )
            ) { backStack ->
                val siteId   = backStack.arguments?.getInt("siteId") ?: 0
                val siteName = backStack.arguments?.getString("siteName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "Heritage Site"
                InsightsScreen(
                    siteId   = siteId,
                    siteName = siteName,
                    onBack   = { navController.popBackStack() }
                )
            }

            // ── Trip moment hub (between end-trip and quiz) ────────────────────
            composable(
                route = "trip_moment_hub/{tripId}/{siteId}/{siteName}/{visitedCount}/{totalCount}",
                arguments = listOf(
                    navArgument("tripId")       { type = NavType.IntType },
                    navArgument("siteId")       { type = NavType.IntType },
                    navArgument("siteName")     { type = NavType.StringType },
                    navArgument("visitedCount") { type = NavType.IntType },
                    navArgument("totalCount")   { type = NavType.IntType }
                )
            ) { backStack ->
                val tripId       = backStack.arguments?.getInt("tripId") ?: 0
                val siteId       = backStack.arguments?.getInt("siteId") ?: 0
                val siteName     = backStack.arguments?.getString("siteName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "Heritage Site"
                val visitedCount = backStack.arguments?.getInt("visitedCount") ?: 0
                val totalCount   = backStack.arguments?.getInt("totalCount") ?: 0
                TripMomentHubScreen(
                    tripId       = tripId,
                    siteName     = siteName,
                    visitedCount = visitedCount,
                    totalCount   = totalCount,
                    onContinue   = {
                        navController.navigate(quizRoute(tripId, siteId, siteName, visitedCount, totalCount)) {
                            popUpTo("home") { inclusive = false }
                        }
                    },
                    onSkipQuiz   = {
                        navController.navigate(tripCompletionRoute(siteId, siteName, visitedCount, totalCount)) {
                            popUpTo("home") { inclusive = false }
                        }
                    }
                )
            }

            // ── Node Instants (Instagram-style per-node gallery) ───────────────
            composable(
                route     = "node_instants/{nodeId}/{siteId}/{nodeName}",
                arguments = listOf(
                    navArgument("nodeId")   { type = NavType.IntType },
                    navArgument("siteId")   { type = NavType.IntType },
                    navArgument("nodeName") { type = NavType.StringType }
                )
            ) { backStack ->
                val nodeId   = backStack.arguments?.getInt("nodeId") ?: 0
                val siteId   = backStack.arguments?.getInt("siteId") ?: 0
                val nodeName = backStack.arguments?.getString("nodeName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "this spot"
                NodeInstantsScreen(
                    nodeId   = nodeId,
                    siteId   = siteId,
                    nodeName = nodeName,
                    onBack   = { navController.popBackStack() }
                )
            }

            // ── Final Quiz (between end-trip and review) ───────────────────────
            composable(
                route = "quiz/{tripId}/{siteId}/{siteName}/{visitedCount}/{totalCount}",
                arguments = listOf(
                    navArgument("tripId")       { type = NavType.IntType },
                    navArgument("siteId")       { type = NavType.IntType },
                    navArgument("siteName")     { type = NavType.StringType },
                    navArgument("visitedCount") { type = NavType.IntType },
                    navArgument("totalCount")   { type = NavType.IntType }
                )
            ) { backStack ->
                val tripId       = backStack.arguments?.getInt("tripId") ?: 0
                val siteId       = backStack.arguments?.getInt("siteId") ?: 0
                val siteName     = backStack.arguments?.getString("siteName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "Heritage Site"
                val visitedCount = backStack.arguments?.getInt("visitedCount") ?: 0
                val totalCount   = backStack.arguments?.getInt("totalCount") ?: 0
                QuizScreen(
                    tripId = tripId,
                    onFinish = {
                        navController.navigate(reviewRoute(tripId, siteId, siteName, visitedCount, totalCount)) {
                            popUpTo("home") { inclusive = false }
                        }
                    }
                )
            }

            // ── Profile ───────────────────────────────────────────────────────
            composable("profile") {
                ProfileScreen(
                    onBack       = { navController.popBackStack() },
                    onSignOut    = {
                        AuthManager.signOut()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenHistory  = { navController.navigate("history") },
                    onOpenFeedback = { navController.navigate("feedback") },
                    onOpenMyInsights = { navController.navigate("my_insights") },
                    onOpenStore    = { navController.navigate("store") },
                    onOpenCoupons  = { navController.navigate("coupons") },
                    onReplayOnboarding = { navController.navigate("onboarding") },
                    onAccentChange = onAccentChange
                )
            }

            // ── Personal insights ─────────────────────────────────────────────
            composable("my_insights") {
                MyInsightsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Visit history ─────────────────────────────────────────────────
            composable("history") {
                HistoryScreen(
                    onBack        = { navController.popBackStack() },
                    onWriteReview = { tripId, siteId, siteName, visited, total ->
                        navController.navigate(reviewRoute(tripId, siteId, siteName, visited, total))
                    }
                )
            }

            // ── Feedback / bug report ─────────────────────────────────────────
            composable("feedback") {
                FeedbackScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Voice ─────────────────────────────────────────────────────────
            composable(
                route     = "voice/{siteName}/{siteId}/{nodeId}",
                arguments = listOf(
                    navArgument("siteName") { type = NavType.StringType },
                    navArgument("siteId")   { type = NavType.StringType },
                    navArgument("nodeId")   { type = NavType.StringType }
                )
            ) { backStack ->
                val siteName = backStack.arguments?.getString("siteName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: "Heritage Site"
                val siteId   = backStack.arguments?.getString("siteId") ?: ""
                val nodeId   = backStack.arguments?.getString("nodeId") ?: ""
                VoiceChatScreen(
                    siteName             = siteName,
                    siteId               = siteId,
                    nodeId               = nodeId,
                    onBack               = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate("profile") }
                )
            }

            // ── QR Scan ───────────────────────────────────────────────────────
            composable(
                route     = "qr/{siteName}/{siteId}",
                arguments = listOf(
                    navArgument("siteName") { type = NavType.StringType },
                    navArgument("siteId")   { type = NavType.StringType }
                )
            ) { backStack ->
                val siteName = backStack.arguments?.getString("siteName")
                    ?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
                val siteId   = backStack.arguments?.getString("siteId") ?: ""
                val trip     = TripManager.state.collectAsState()
                QrScanScreen(
                    monumentId  = siteId.toLongOrNull() ?: 0L,
                    currentLat  = trip.value.lastLat,
                    currentLng  = trip.value.lastLng,
                    onNodeReady = { nodeId, _, isKing, scanSiteId ->
                        navController.navigate(nodeDetailRoute(nodeId, scanSiteId, isKing)) {
                            popUpTo("home") { inclusive = false }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Video Player ───────────────────────────────────────────────────
            composable(
                route     = "video_player/{siteId}/{nodeId}",
                arguments = listOf(
                    navArgument("siteId") { type = NavType.IntType },
                    navArgument("nodeId") { type = NavType.IntType }
                )
            ) { backStack ->
                val siteId = backStack.arguments?.getInt("siteId") ?: 0
                val nodeId = backStack.arguments?.getInt("nodeId") ?: 0
                VideoPlayerScreen(
                    siteId = siteId,
                    nodeId = nodeId,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        BonusGameHost()

        if (isResolvingDeepLink) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        if (deepLinkError != null) {
            AlertDialog(
                onDismissRequest = { deepLinkError = null },
                title = { Text("Node Link", fontWeight = FontWeight.Bold) },
                text = { Text(deepLinkError ?: "") },
                confirmButton = {
                    TextButton(onClick = { deepLinkError = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

// ── Route helpers ─────────────────────────────────────────────────────────────

fun heritageDetailRoute(siteName: String, siteId: String): String {
    val encoded = URLEncoder.encode(siteName, "UTF-8")
    return "detail/$encoded/$siteId"
}

fun voiceChatRoute(siteName: String, siteId: String, nodeId: String): String {
    val encoded = URLEncoder.encode(siteName, "UTF-8")
    return "voice/$encoded/$siteId/$nodeId"
}

fun qrScanRoute(siteName: String, siteId: String): String {
    val encoded = URLEncoder.encode(siteName, "UTF-8")
    return "qr/$encoded/$siteId"
}

fun nodeDetailRoute(nodeId: Int, siteId: Int, isKing: Boolean): String =
    "node/$nodeId/$siteId/$isKing"

fun directionsRoute(siteId: Int, siteName: String): String {
    val encoded = URLEncoder.encode(siteName, "UTF-8")
    return "directions/$siteId/$encoded"
}

fun tripCompletionRoute(siteId: Int, siteName: String, visitedCount: Int, totalCount: Int): String {
    val encoded = URLEncoder.encode(siteName, "UTF-8")
    return "trip_completion/$siteId/$encoded/$visitedCount/$totalCount"
}

fun siteInfoRoute(siteId: Int, siteName: String): String {
    val encoded = URLEncoder.encode(siteName, "UTF-8")
    return "site_info/$siteId/$encoded"
}

fun reviewRoute(tripId: Int, siteId: Int, siteName: String, visitedCount: Int, totalCount: Int): String {
    val encoded = URLEncoder.encode(siteName, "UTF-8")
    return "review/$tripId/$siteId/$encoded/$visitedCount/$totalCount"
}

fun amenityDetailRoute(amenityId: Int): String =
    "amenity/$amenityId"

fun insightsRoute(siteId: Int, siteName: String): String {
    val encoded = URLEncoder.encode(siteName, "UTF-8")
    return "insights/$siteId/$encoded"
}

fun tripMomentHubRoute(tripId: Int, siteId: Int, siteName: String, visitedCount: Int, totalCount: Int): String {
    val encoded = URLEncoder.encode(siteName, "UTF-8")
    return "trip_moment_hub/$tripId/$siteId/$encoded/$visitedCount/$totalCount"
}

fun nodeInstantsRoute(nodeId: Int, siteId: Int, nodeName: String): String {
    val encoded = URLEncoder.encode(nodeName, "UTF-8")
    return "node_instants/$nodeId/$siteId/$encoded"
}

fun quizRoute(tripId: Int, siteId: Int, siteName: String, visitedCount: Int, totalCount: Int): String {
    val encoded = URLEncoder.encode(siteName, "UTF-8")
    return "quiz/$tripId/$siteId/$encoded/$visitedCount/$totalCount"
}

fun nodeCommentsRoute(nodeId: Int, siteId: Int, nodeName: String): String {
    val encoded = URLEncoder.encode(nodeName, "UTF-8")
    return "node_comments/$nodeId/$siteId/$encoded"
}

fun videoPlayerRoute(siteId: Int, nodeId: Int): String =
    "video_player/$siteId/$nodeId"
