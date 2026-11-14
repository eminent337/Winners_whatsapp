package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AuthScreen
import com.example.auth.LocalProfile
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.ui.screens.GradePulseMainScreen
import com.example.ui.theme.GradePulseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GradePulseTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Profiles available on this device. Created on first launch if absent.
    val profiles = remember(context) {
        com.example.auth.availableProfiles(context).ifEmpty {
            listOf(LocalProfile("local_user", "This Device"))
        }
    }

    val user by authStateFlow().collectAsStateWithLifecycle(initialValue = null)

    val currentUser = user
    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                scope.launch {
                    com.example.auth.signIn(context, profiles.first())
                }
            }
        )
    } else {
        val uid = currentUser.id
        key(uid) {
            GradePulseMainScreen(
                currentUserId = uid,
                userDisplayName = currentUser.displayName,
                onSignOut = {
                    signOut()
                }
            )
        }
    }
}
