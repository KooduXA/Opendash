// MainActivity.kt - Updated for new navigation
package com.kooduXA.opendash


import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.kooduXA.opendash.ui.screens.*
import com.kooduXA.opendash.ui.theme.OpendashTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpendashTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OpenDashApp()
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun OpenDashApp() {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Dashboard) }

    // GLOBAL BACK HANDLER
    // If we are NOT on Dashboard, the Back button takes us to Dashboard.
    // If we ARE on Dashboard, the system handles it (closes app).
    BackHandler(enabled = currentScreen != AppScreen.Dashboard) {
        currentScreen = AppScreen.Dashboard
    }

    when (currentScreen) {
        AppScreen.Dashboard -> {
            // FIX: Inject the ViewModel here
            DashboardScreen(
                viewModel = hiltViewModel(),
                onNavigateToSettings = { currentScreen = AppScreen.Settings },
                onNavigateToGallery = { currentScreen = AppScreen.Gallery },
                onNavigateToFiles = { currentScreen = AppScreen.RemoteFiles }
            )
        }
        AppScreen.Settings -> {
            SettingsScreen(
                onBack = { currentScreen = AppScreen.Dashboard }
            )
        }
        AppScreen.Gallery -> {
            EnhancedLocalGalleryScreen(
                colors = DayColors,
                onBack = { currentScreen = AppScreen.Dashboard }
            )
        }
        AppScreen.RemoteFiles -> {
            // Ensure we use the wrapper that connects the ViewModel
            EnhancedRemoteBrowserWrapper(
                viewModel = hiltViewModel(),
                colors = DayColors,
                onBack = { currentScreen = AppScreen.Dashboard }
            )
        }
    }
}

sealed class AppScreen {
    object Dashboard : AppScreen()
    object Settings : AppScreen()
    object Gallery : AppScreen()
    object RemoteFiles : AppScreen()
}