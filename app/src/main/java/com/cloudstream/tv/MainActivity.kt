package com.cloudstream.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cloudstream.tv.data.DriveFile
import com.cloudstream.tv.data.DriveRepository
import com.cloudstream.tv.ui.screens.HomeScreen
import com.cloudstream.tv.ui.screens.OnboardingScreen
import com.cloudstream.tv.ui.screens.PlaybackScreen
import com.cloudstream.tv.ui.screens.SlideshowScreen
import com.cloudstream.tv.ui.theme.CloudStreamTVTheme

enum class Screen {
    Onboarding,
    Home,
    Playback,
    Slideshow
}

class MainActivity : ComponentActivity() {
    private lateinit var repository: DriveRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        repository = DriveRepository(this)

        setContent {
            var currentScreen by remember {
                mutableStateOf(
                    if (repository.isLoggedIn() || repository.getSavedLinks().isNotEmpty()) {
                        Screen.Home
                    } else {
                        Screen.Onboarding
                    }
                )
            }
            
            // Shared arguments for playback & slideshow screens
            var activeFile by remember { mutableStateOf<DriveFile?>(null) }
            var mediaPlaylist by remember { mutableStateOf<List<DriveFile>>(emptyList()) }
            
            // Dynamic theme configuration state
            var isDarkTheme by remember { mutableStateOf(repository.isDarkTheme()) }

            CloudStreamTVTheme(isDarkTheme = isDarkTheme) {
                // Root-level screen switcher
                when (currentScreen) {
                    Screen.Onboarding -> {
                        OnboardingScreen(
                            repository = repository,
                            onOnboardingComplete = {
                                isDarkTheme = repository.isDarkTheme()
                                currentScreen = Screen.Home
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Screen.Home -> {
                        HomeScreen(
                            repository = repository,
                            lastPlayedFileId = activeFile?.id,
                            onPlayVideo = { video, playlist ->
                                activeFile = video
                                mediaPlaylist = playlist
                                currentScreen = Screen.Playback
                            },
                            onStartSlideshow = { photo, playlist ->
                                activeFile = photo
                                mediaPlaylist = playlist
                                currentScreen = Screen.Slideshow
                            },
                            onNavigateToOnboarding = {
                                currentScreen = Screen.Onboarding
                            },
                            onThemeChanged = { theme ->
                                isDarkTheme = theme
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Screen.Playback -> {
                        val file = activeFile
                        if (file == null) {
                            // BUG-12 Fix: Guard against null activeFile on Playback screen.
                            // Without this, the screen renders blank with no back handler — the user is stuck.
                            LaunchedEffect(Unit) { currentScreen = Screen.Home }
                        } else {
                            PlaybackScreen(
                                currentFile = file,
                                playlist = mediaPlaylist,
                                repository = repository,
                                onBack = {
                                    currentScreen = Screen.Home
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Screen.Slideshow -> {
                        val file = activeFile
                        if (file == null) {
                            // BUG-12 Fix: Same guard for Slideshow — null activeFile = redirect to Home
                            LaunchedEffect(Unit) { currentScreen = Screen.Home }
                        } else {
                            SlideshowScreen(
                                currentFile = file,
                                photos = mediaPlaylist,
                                repository = repository,
                                onBack = {
                                    currentScreen = Screen.Home
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
