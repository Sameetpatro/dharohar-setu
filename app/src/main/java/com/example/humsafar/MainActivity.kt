package com.example.humsafar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.humsafar.auth.AuthManager
import com.example.humsafar.data.BonusGameManager
import com.example.humsafar.data.GamificationRepository
import com.example.humsafar.data.StatsRepository
import com.example.humsafar.data.TripManager
import com.example.humsafar.data.UserRepository
import com.example.humsafar.navigation.AppNavigation
import com.example.humsafar.prefs.AppPreferences
import com.example.humsafar.ui.theme.Accent
import com.example.humsafar.ui.theme.HumsafarTheme
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TripManager.init(applicationContext)
        UserRepository.init(applicationContext)
        BonusGameManager.init(applicationContext)
        requestNotificationPermissionIfNeeded()
        // Record this app open + keep the user counted as "active now".
        StatsRepository.start()

        val appPrefs = AppPreferences(applicationContext)

        // Returning users (already signed in) shouldn't be force-marched through
        // onboarding when they upgrade to this build. Treat an existing Firebase
        // session as implicit consent that they've seen the app before.
        if (AuthManager.currentUser.value != null && !appPrefs.onboardingComplete) {
            appPrefs.onboardingComplete = true
        }

        // Sync the currently-signed-in Firebase user with our backend so that
        // any subsequent call needing firebase_uid (trips, reviews, chat history)
        // succeeds. Fires whenever the Firebase user changes (login / logout).
        lifecycleScope.launch {
            AuthManager.currentUser
                .distinctUntilChangedBy { it?.uid }
                .collect { user ->
                    if (user == null) {
                        UserRepository.clear()
                    } else {
                        UserRepository.syncFirebaseUser(user)
                        GamificationRepository.refresh()
                    }
                }
        }

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            var accent by remember { mutableStateOf<Accent>(appPrefs.getAccent()) }

            HumsafarTheme(accent = accent) {
                AppNavigation(
                    appPrefs = appPrefs,
                    onAccentChange = { picked ->
                        appPrefs.setAccent(picked)
                        accent = picked
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        BonusGameManager.onAppForeground()
    }

    override fun onStop() {
        BonusGameManager.onAppBackground()
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val link = intent?.data?.toString() ?: return

        Log.d("MainActivity", "Received intent link: $link")

        if (AuthManager.isSignInWithEmailLink(link)) {
            Log.d("MainActivity", "Valid email link detected")

            val email = AuthManager.getPendingEmailLinkEmail(this)

            if (email != null) {
                lifecycleScope.launch {
                    AuthManager.signInWithEmailLink(email, link)
                        .onSuccess { user ->
                            Log.i("MainActivity", "Email link sign-in success: ${user.uid}")
                            AuthManager.clearPendingEmailLinkEmail(this@MainActivity)
                        }
                        .onFailure { error ->
                            Log.e("MainActivity", "Email link sign-in failed", error)
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Sign-in failed: ${error.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                }
            } else {
                Log.w("MainActivity", "Email not found in storage, user needs to re-enter")
                android.widget.Toast.makeText(
                    this,
                    "Please enter your email to complete sign-in",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } else {
            // Android App Link or node URL
            com.example.humsafar.navigation.DeepLinkHandler.handleIntent(intent)
        }
    }
}
