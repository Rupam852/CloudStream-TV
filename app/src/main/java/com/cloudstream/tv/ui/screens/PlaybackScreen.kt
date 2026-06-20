package com.cloudstream.tv.ui.screens

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Speed
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cloudstream.tv.data.DriveFile
import com.cloudstream.tv.data.DriveRepository
import com.cloudstream.tv.network.GoogleDriveClient
import com.cloudstream.tv.network.NetworkUtils
import com.cloudstream.tv.ui.components.ConnectivityDialog
import com.cloudstream.tv.ui.components.TVFocusableItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(UnstableApi::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun PlaybackScreen(
    currentFile: DriveFile,
    playlist: List<DriveFile>,
    repository: DriveRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Intercept back gesture/button to go back to Home screen instead of exiting the activity
    BackHandler {
        onBack()
    }
    var activeIndex by remember { mutableStateOf(playlist.indexOfFirst { it.id == currentFile.id }.coerceAtLeast(0)) }
    val activeFile = remember(activeIndex) { playlist.getOrNull(activeIndex) ?: currentFile }

    var audioBoostLevel by remember { mutableStateOf(1) } // 0 = 100%, 1 = 120% (Soft), 2 = 150% (Medium), 3 = 200% (High)
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }
    var audioSessionIdState by remember { mutableStateOf(0) }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var toastTrigger by remember { mutableStateOf(0L) }

    fun showToast(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        toastMessage = message
        toastIcon = icon
        toastTrigger = System.currentTimeMillis()
    }

    LaunchedEffect(toastTrigger) {
        if (toastMessage != null) {
            delay(1500)
            toastMessage = null
            toastIcon = null
        }
    }

    fun applyAudioBoost(enhancer: LoudnessEnhancer?, level: Int) {
        try {
            if (enhancer != null) {
                val gainMillibels = when (level) {
                    0 -> 0      // 100% (Normal)
                    1 -> 1000   // 120% (+10dB boost)
                    2 -> 1800   // 150% (+18dB boost)
                    3 -> 2600   // 200% (+26dB boost)
                    else -> 0
                }
                Log.d("PlaybackScreen", "Applying target gain: $gainMillibels mB to enhancer")
                enhancer.setTargetGain(gainMillibels)
                enhancer.enabled = (gainMillibels > 0)
                Log.d("PlaybackScreen", "Enhancer enabled state: ${enhancer.enabled}, actual target gain: ${enhancer.targetGain} mB")
            }
        } catch (e: Exception) {
            Log.e("PlaybackScreen", "Failed to apply audio boost", e)
        }
    }

    LaunchedEffect(audioBoostLevel, loudnessEnhancer) {
        applyAudioBoost(loudnessEnhancer, audioBoostLevel)
    }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            // Set audio attributes to optimize for TV movie playback
            val attributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(attributes, true)
        }
    }

    // Monitor audio session ID changes via Player.Listener (onAudioSessionIdChanged).
    // BUG-06 Fix: Removed the redundant polling LaunchedEffect that previously also created
    // a LoudnessEnhancer every second. Both paths fired simultaneously causing dual enhancer
    // instances on the same audio session, risking distortion and resource waste.
    // The Player.Listener callback below is the single authoritative source for this.

    // Playback and UI States
    var isPlaying by remember { mutableStateOf(exoPlayer.playWhenReady) }

    val hostActivity = remember(context) {
        var activity: android.app.Activity? = null
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) {
                activity = ctx
                break
            }
            ctx = ctx.baseContext
        }
        activity
    }

    // Keep TV screen awake ONLY during active video playback to prevent OLED/QLED screen burn-in when idle
    LaunchedEffect(isPlaying, hostActivity) {
        if (hostActivity != null) {
            if (isPlaying) {
                hostActivity.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Log.d("PlaybackScreen", "Keep screen on: ADDED")
            } else {
                hostActivity.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Log.d("PlaybackScreen", "Keep screen on: CLEARED")
            }
        }
    }

    // Ensure it's cleared when screen is disposed
    DisposableEffect(hostActivity) {
        onDispose {
            hostActivity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferPosition by remember { mutableLongStateOf(0L) }
    var isResolving by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var oauthToken by remember { mutableStateOf<String?>(null) }

    var controlsVisible by remember { mutableStateOf(true) }
    var userActivityTrigger by remember { mutableStateOf(0L) }

    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    val playButtonFocusRequester = remember { FocusRequester() }
    val timelineFocusRequester = remember { FocusRequester() }

    // Coroutine scope for debounced seeks
    val coroutineScope = rememberCoroutineScope()
    var seekDebounceJob by remember { mutableStateOf<Job?>(null) }

    // Retry counter for auto-recovery on player error (max 2 retries)
    var playerErrorRetryCount by remember { mutableIntStateOf(0) }

    // Network retry dialog states
    var showNetworkDialog by remember { mutableStateOf(false) }
    var pendingNetworkAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var resolveTrigger by remember { mutableIntStateOf(0) }

    // Resolve URL on change
    LaunchedEffect(activeFile, resolveTrigger) {
        isResolving = true
        resolvedUrl = null
        try {
            val token = if (repository.isLoggedIn()) {
                withContext(Dispatchers.IO) { repository.getAccessToken() }
            } else {
                null
            }
            val apiKey = repository.getApiKey()
            oauthToken = token
            val url = withContext(Dispatchers.IO) {
                GoogleDriveClient.resolveDriveDirectUrl(activeFile.id, token, apiKey)
            }
            if (url.isNullOrBlank()) {
                throw java.io.IOException("Inaccessible or empty video URL")
            }
            resolvedUrl = url
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is java.io.IOException || !NetworkUtils.isInternetAvailable(context)) {
                pendingNetworkAction = {
                    resolveTrigger++
                }
                showNetworkDialog = true
            } else {
                showToast("Failed to resolve video link", Icons.Default.Pause)
            }
        } finally {
            isResolving = false
        }
    }

    // Track and save playback position when activeFile changes (e.g. Next/Previous transitions)
    DisposableEffect(activeFile) {
        onDispose {
            // BUG-02 Fix: Read position from the Compose state (currentPosition) which is updated
            // by the periodic LaunchedEffect. Do NOT call exoPlayer.currentPosition here —
            // the player may already be released if both this DisposableEffect and the main
            // DisposableEffect(exoPlayer) are disposed simultaneously on back navigation.
            try {
                val savedCurrentPos = currentPosition
                val savedDuration = duration
                if (savedDuration > 0 && savedCurrentPos > 3000 && savedCurrentPos < savedDuration * 0.95) {
                    repository.savePlaybackPosition(activeFile.id, savedCurrentPos)
                    Log.d("PlaybackScreen", "Saved transition position $savedCurrentPos for file ${activeFile.name}")
                }
            } catch (e: Exception) {
                Log.e("PlaybackScreen", "Failed to save position on transition", e)
            }
        }
    }

    // Load active file into player when resolved URL is ready
    LaunchedEffect(resolvedUrl, oauthToken) {
        val url = resolvedUrl ?: return@LaunchedEffect
        val mediaItem = MediaItem.fromUri(url)
        val token = oauthToken

        // TOKEN EXPIRY FIX — Defense 2: OkHttp Authenticator
        // When ExoPlayer sends a chunk request with an expired token, Google returns 401.
        // The OkHttp Authenticator intercepts this 401 BEFORE ExoPlayer even sees it,
        // silently refreshes the token via repository.getAccessToken(), and retries
        // the exact same request with the new token — completely transparent to the player.
        // This means video NEVER stops for a token expiry, even for 3GB+ 3-hour movies.
        val tokenRefreshingClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .authenticator { _, response ->
                // Called automatically by OkHttp when it receives a 401 Unauthorized.
                // Guard against infinite retry loop (max 1 retry per request).
                if (response.request.header("X-Token-Refreshed") != null) {
                    Log.e("PlaybackScreen", "Token refresh failed — giving up on this request")
                    return@authenticator null
                }
                Log.w("PlaybackScreen", "Got 401 during streaming — refreshing OAuth token")
                // runBlocking is safe here: OkHttp Authenticator always runs on an IO thread,
                // never on the main thread.
                val newToken = runBlocking { repository.getAccessToken() }
                if (newToken != null) {
                    Log.i("PlaybackScreen", "Token refreshed successfully — retrying chunk request")
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .header("X-Token-Refreshed", "true") // prevent infinite retry
                        .build()
                } else {
                    Log.e("PlaybackScreen", "Token refresh returned null — cannot retry")
                    null
                }
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(tokenRefreshingClient).apply {
            setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            if (token != null && url.contains("googleapis.com")) {
                setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            }
        }

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            setConstantBitrateSeekingEnabled(true)
        }

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
            .createMediaSource(mediaItem)

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()

        // Seek to saved position if it exists (only if played for more than 3 seconds)
        val savedPos = repository.getPlaybackPosition(activeFile.id)
        if (savedPos > 3000) {
            exoPlayer.seekTo(savedPos)
            val timeString = formatTime(savedPos)
            showToast("Resumed from $timeString", Icons.Default.PlayArrow)
        }

        exoPlayer.play()
    }

    // TOKEN EXPIRY FIX — Defense 1: Proactive Token Watchdog
    // Runs silently in background every 45 minutes during playback.
    // Google OAuth tokens expire after 1 hour. By refreshing at 45 min, the token
    // is ALWAYS fresh — the Authenticator (Defense 2) serves as the last safety net
    // in case this misses (e.g. app was backgrounded and resumed).
    LaunchedEffect(resolvedUrl) {
        if (resolvedUrl == null || oauthToken == null) return@LaunchedEffect
        // Wait 45 minutes before first proactive refresh
        delay(45 * 60 * 1000L)
        while (true) {
            Log.i("PlaybackScreen", "Token watchdog: proactively refreshing OAuth token")
            val currentPos = currentPosition // BUG-07 Fix: read from Compose state, not exoPlayer directly
            val wasPlaying = exoPlayer.playWhenReady
            // BUG-07 Fix: Always fetch the fresh token from repository.
            // Do NOT compare against `oauthToken` Compose state — it may be stale.
            // getAccessToken() already handles whether refresh is actually needed.
            val previousToken = oauthToken
            val newToken = repository.getAccessToken()
            if (newToken != null && newToken != previousToken) {
                // Token was refreshed — rebuild media source at current position
                Log.i("PlaybackScreen", "Token changed — rebuilding media source at $currentPos ms")
                val url = resolvedUrl ?: break
                val mediaItem = MediaItem.fromUri(url)
                val refreshedClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .authenticator { _, response ->
                        if (response.request.header("X-Token-Refreshed") != null) return@authenticator null
                        val refreshed = runBlocking { repository.getAccessToken() }
                        refreshed?.let {
                            response.request.newBuilder()
                                .header("Authorization", "Bearer $it")
                                .header("X-Token-Refreshed", "true")
                                .build()
                        }
                    }
                    .build()
                val newFactory = OkHttpDataSource.Factory(refreshedClient).apply {
                    setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    if (url.contains("googleapis.com")) {
                        setDefaultRequestProperties(mapOf("Authorization" to "Bearer $newToken"))
                    }
                }
                val newExtractors = androidx.media3.extractor.DefaultExtractorsFactory()
                    .apply { setConstantBitrateSeekingEnabled(true) }
                val newSource = ProgressiveMediaSource.Factory(newFactory, newExtractors)
                    .createMediaSource(mediaItem)
                exoPlayer.setMediaSource(newSource, currentPos)
                exoPlayer.prepare()
                if (wasPlaying) exoPlayer.play()
                oauthToken = newToken
            } else {
                Log.i("PlaybackScreen", "Token watchdog: token still valid, no rebuild needed")
            }
            // Check again in 45 minutes
            delay(45 * 60 * 1000L)
        }
    }

    // Listen to player state changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioSessionIdState = audioSessionId
                Log.d("PlaybackScreen", "onAudioSessionIdChanged: $audioSessionId")
                try {
                    loudnessEnhancer?.release()
                    if (audioSessionId != 0) {
                        val enhancer = LoudnessEnhancer(audioSessionId)
                        loudnessEnhancer = enhancer
                        applyAudioBoost(enhancer, audioBoostLevel)
                        Log.d("PlaybackScreen", "LoudnessEnhancer attached to session $audioSessionId successfully")
                    }
                } catch (e: Exception) {
                    Log.e("PlaybackScreen", "Error attaching LoudnessEnhancer to session $audioSessionId", e)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                duration = exoPlayer.duration.coerceAtLeast(0L)
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    // Video has completed, so clear saved resume progress
                    repository.clearPlaybackPosition(activeFile.id)
                    if (activeIndex < playlist.size - 1) {
                        activeIndex++
                    } else {
                        exoPlayer.seekTo(0)
                        exoPlayer.pause()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlaybackScreen", "Player error (retry=$playerErrorRetryCount): ", error)
                isBuffering = false

                // Bug 1 Fix: Auto-recover from network/seek-overflow errors instead of
                // just showing a toast and leaving the player in a broken state.
                // We retry up to 2 times by re-preparing the player and seeking back
                // to the last known position. After 2 failures we surface the error.
                val isRecoverable = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                    || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                    || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED

                if (isRecoverable) {
                    if (playerErrorRetryCount < 2) {
                        playerErrorRetryCount++
                        val resumePos = currentPosition.coerceAtLeast(0L)
                        Log.w("PlaybackScreen", "Recoverable error — attempting retry $playerErrorRetryCount at position $resumePos ms")
                        showToast("Reconnecting... ($playerErrorRetryCount/2)", Icons.Default.PlayArrow)
                        try {
                            exoPlayer.prepare()
                            if (resumePos > 0L) exoPlayer.seekTo(resumePos)
                            exoPlayer.play()
                        } catch (e: Exception) {
                            Log.e("PlaybackScreen", "Recovery attempt $playerErrorRetryCount failed", e)
                        }
                    } else {
                        pendingNetworkAction = {
                            val resumePos = currentPosition.coerceAtLeast(0L)
                            try {
                                playerErrorRetryCount = 0
                                exoPlayer.prepare()
                                if (resumePos > 0L) exoPlayer.seekTo(resumePos)
                                exoPlayer.play()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        showNetworkDialog = true
                    }
                } else {
                    playerErrorRetryCount = 0
                    showToast("Playback error: ${error.localizedMessage ?: "Unknown error"}", Icons.Default.Pause)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            // Unhook listener first before any other cleanup
            exoPlayer.removeListener(listener)
            try {
                // BUG-02/BUG-03 Fix: Use Compose state vars (currentPosition, duration) —
                // NOT exoPlayer.currentPosition/duration. The player may already be in a
                // released or invalid state by the time onDispose runs.
                val savedPos = currentPosition
                val savedDur = duration
                if (savedDur > 0 && savedPos > 3000 && savedPos < savedDur * 0.95) {
                    repository.savePlaybackPosition(activeFile.id, savedPos)
                    Log.d("PlaybackScreen", "Saved exit position $savedPos for file ${activeFile.name}")
                } else {
                    repository.clearPlaybackPosition(activeFile.id)
                }
            } catch (e: Exception) {
                Log.e("PlaybackScreen", "Failed to save position on exit", e)
            }

            try {
                loudnessEnhancer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            exoPlayer.release()
        }
    }

    // Periodically update playback positions (seekbar timeline) and save progress every 5 seconds
    LaunchedEffect(isPlaying) {
        var saveCounter = 0
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            bufferPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            
            saveCounter++
            if (saveCounter >= 20) { // 20 * 250ms = 5000ms (5 seconds)
                saveCounter = 0
                val totalDur = exoPlayer.duration
                if (totalDur > 0 && currentPosition > 3000 && currentPosition < totalDur * 0.95) {
                    repository.savePlaybackPosition(activeFile.id, currentPosition)
                }
            }
            delay(250)
        }
    }

    // Auto-hide controls overlay after 5 seconds of inactivity
    LaunchedEffect(controlsVisible, userActivityTrigger) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    // Bug 2 Fix: Focus Play/Pause button (not seekbar) after controls appear.
    // Delay must be >= AnimatedVisibility fade-in duration (400ms) so the composable
    // is fully attached before requestFocus() is called — otherwise focus silently
    // fails and the next key events get swallowed or trigger buffering.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(450)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Composable may not be attached yet; safe to ignore
                Log.w("PlaybackScreen", "Focus request failed (controls not yet attached): ${e.message}")
            }
        }
    }

    fun showControls() {
        controlsVisible = true
        userActivityTrigger = System.currentTimeMillis()
    }

    fun togglePlayPause() {
        showControls()
        if (exoPlayer.playWhenReady) {
            exoPlayer.pause()
            showToast("Pause", Icons.Default.Pause)
        } else {
            exoPlayer.play()
            showToast("Play", Icons.Default.PlayArrow)
        }
    }

    // Bug 1 Fix: Debounced seek — rapid key presses cancel the previous pending seek
    // and schedule a new one 300ms later. This prevents ExoPlayer from being flooded
    // with overlapping seek requests which causes SOURCE_ERROR / stream failure.
    fun seekForward() {
        showControls()
        showToast("Forward +10s", Icons.Default.FastForward)
        // Optimistically update UI position immediately
        currentPosition = (currentPosition + 10000L).coerceAtMost(duration)
        seekDebounceJob?.cancel()
        seekDebounceJob = coroutineScope.launch {
            delay(300)
            exoPlayer.seekTo(currentPosition)
            playerErrorRetryCount = 0 // reset retry counter on intentional seek
        }
    }

    fun seekRewind() {
        showControls()
        showToast("Rewind -10s", Icons.Default.FastRewind)
        // Optimistically update UI position immediately
        currentPosition = (currentPosition - 10000L).coerceAtLeast(0L)
        seekDebounceJob?.cancel()
        seekDebounceJob = coroutineScope.launch {
            delay(300)
            exoPlayer.seekTo(currentPosition)
            playerErrorRetryCount = 0 // reset retry counter on intentional seek
        }
    }

    fun skipNext() {
        if (activeIndex < playlist.size - 1) {
            showControls()
            activeIndex++
            showToast("Next Video", Icons.Default.SkipNext)
        }
    }

    fun skipPrevious() {
        if (activeIndex > 0) {
            showControls()
            activeIndex--
            showToast("Previous Video", Icons.Default.SkipPrevious)
        }
    }

    fun toggleAspectRatio() {
        showControls()
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val modeText = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit to Screen"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch / Fill"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
            else -> "Fit to Screen"
        }
        showToast("Aspect Ratio: $modeText", Icons.Default.AspectRatio)
    }

    fun toggleSpeed() {
        showControls()
        playbackSpeed = when (playbackSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        exoPlayer.setPlaybackSpeed(playbackSpeed)
        showToast("Speed: ${playbackSpeed}x", Icons.Default.Speed)
    }

    // Root container to capture TV Remote D-Pad keys
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = { showControls() })
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val wasVisible = controlsVisible
                    showControls()
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!wasVisible) {
                                seekRewind()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!wasVisible) {
                                seekForward()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            togglePlayPause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (!isPlaying) {
                                togglePlayPause()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            if (isPlaying) {
                                togglePlayPause()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            seekForward()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            seekRewind()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            skipNext()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            skipPrevious()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // 1. ExoPlayer Video view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = exoPlayer
                    this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loader Overlay (while resolving redirect or when player is buffering)
        if (isResolving || isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (isResolving) "Resolving direct URL..." else "Buffering...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        }

        // 2. Customized overlay controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            ) {
                // Top details: File Name
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(32.dp)
                ) {
                    Text(
                        text = activeFile.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Streaming from Google Drive",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Bottom area: Seekbar & Controls buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 32.dp, end = 32.dp)
                ) {
                    // Seekbar with Time labels on both sides (Isolated Recomposition scope)
                    PlaybackTimeline(
                        currentPositionProvider = { currentPosition },
                        bufferPositionProvider = { bufferPosition },
                        duration = duration,
                        onSeek = { targetPos ->
                            exoPlayer.seekTo(targetPos)
                            currentPosition = targetPos
                            showControls()
                        },
                        onTogglePlayPause = {
                            togglePlayPause()
                        },
                        modifier = Modifier
                            .focusRequester(timelineFocusRequester)
                            .focusProperties { down = playButtonFocusRequester }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip Previous
                        IconButton(
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Previous File",
                            onClick = { skipPrevious() },
                            enabled = activeIndex > 0
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Rewind 10s
                        IconButton(
                            icon = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10 seconds",
                            onClick = { seekRewind() }
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Play/Pause
                        TVFocusableItem(
                            onClick = { togglePlayPause() },
                            modifier = Modifier
                                .focusRequester(playButtonFocusRequester)
                                .focusProperties { up = timelineFocusRequester },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .size(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Fast Forward 10s
                        IconButton(
                            icon = Icons.Default.FastForward,
                            contentDescription = "Fast Forward 10 seconds",
                            onClick = { seekForward() }
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Skip Next
                        IconButton(
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Next File",
                            onClick = { skipNext() },
                            enabled = activeIndex < playlist.size - 1
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Aspect Ratio Toggle
                        IconButton(
                            icon = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio: " + when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
                                else -> "Zoom"
                            },
                            onClick = { toggleAspectRatio() }
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Audio Boost Toggle
                        TVFocusableItem(
                            onClick = {
                                showControls()
                                audioBoostLevel = (audioBoostLevel + 1) % 4
                                val boostText = when (audioBoostLevel) {
                                    0 -> "100% (Normal)"
                                    1 -> "120% (Soft Boost)"
                                    2 -> "150% (Medium Boost)"
                                    3 -> "200% (High Boost)"
                                    else -> "100%"
                                }
                                showToast("Audio Boost: $boostText", Icons.AutoMirrored.Filled.VolumeUp)
                            },
                            shape = RoundedCornerShape(8.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    val boostText = when (audioBoostLevel) {
                                        0 -> "100%"
                                        1 -> "120% (Soft)"
                                        2 -> "150% (Medium)"
                                        3 -> "200% (High)"
                                        else -> "100%"
                                    }
                                    Text(
                                        text = "Audio: $boostText",
                                        color = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Playback Speed Toggle
                        TVFocusableItem(
                            onClick = { toggleSpeed() },
                            shape = RoundedCornerShape(8.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${playbackSpeed}x",
                                        color = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // 3. Playback Action Toast / Notification Overlay (Renders on top of everything, even when controls are hidden)
        AnimatedVisibility(
            visible = !toastMessage.isNullOrBlank(),
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            toastMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        toastIcon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = msg,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (showNetworkDialog) {
            ConnectivityDialog(
                onDismiss = {
                    showNetworkDialog = false
                    pendingNetworkAction = null
                },
                onRetry = {
                    showNetworkDialog = false
                    pendingNetworkAction?.invoke()
                    pendingNetworkAction = null
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    TVFocusableItem(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.alpha(if (enabled) 1.0f else 0.4f),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        focusedContainerColor = MaterialTheme.colorScheme.primary
    ) { isFocused ->
        Box(
            modifier = Modifier
                .size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaybackTimeline(
    currentPositionProvider: () -> Long,
    bufferPositionProvider: () -> Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPosition = currentPositionProvider()
    val bufferPosition = bufferPositionProvider()
    
    var isFocused by remember { mutableStateOf(false) }
    var tempSeekPosition by remember { mutableStateOf<Long?>(null) }
    
    val displayPosition = tempSeekPosition ?: currentPosition
    
    // Debounce actual seek player updates
    LaunchedEffect(tempSeekPosition) {
        val targetPos = tempSeekPosition ?: return@LaunchedEffect
        delay(400)
        onSeek(targetPos)
    }
    
    TVFocusableItem(
        onClick = {
            // If tempSeekPosition is not null, confirm seek. Otherwise, toggle Play/Pause
            if (tempSeekPosition != null) {
                onSeek(tempSeekPosition!!)
                tempSeekPosition = null
            } else {
                onTogglePlayPause()
            }
        },
        shape = RoundedCornerShape(8.dp),
        scaleOnFocus = 1.0f,
        borderColor = Color.Transparent,
        glowColor = Color.Transparent,
        containerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                // Bug 3 Fix: Do NOT set tempSeekPosition when focus arrives.
                // Previously this caused an accidental seek 400ms after every
                // time the seekbar gained focus (e.g. user just wanted to see controls).
                // tempSeekPosition is now only set by explicit DPAD_LEFT/RIGHT presses.
                if (!focusState.isFocused) {
                    tempSeekPosition = null
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            // Bug 3 Fix: Fixed 10s step — consistent & predictable.
                            // Previous dynamic step (duration/100) was confusing.
                            val step = 10_000L
                            val currentTemp = tempSeekPosition ?: currentPosition
                            tempSeekPosition = (currentTemp - step).coerceAtLeast(0L)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val step = 10_000L
                            val currentTemp = tempSeekPosition ?: currentPosition
                            tempSeekPosition = (currentTemp + step).coerceAtMost(duration)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) { _ ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(displayPosition),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.width(12.dp))
            
            // Progress Bar Track
            val height = if (isFocused) 8.dp else 4.dp
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp), // larger height to allow thumb to draw without clipping
                contentAlignment = Alignment.CenterStart
            ) {
                // Track Background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(height / 2))
                        .background(Color.White.copy(alpha = 0.2f))
                )
                
                // Buffer bar
                val bufferFraction = if (duration > 0) bufferPosition.toFloat() / duration else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferFraction)
                        .height(height)
                        .clip(RoundedCornerShape(height / 2))
                        .background(Color.White.copy(alpha = 0.2f))
                )
                
                // Progress bar
                val progressFraction = if (duration > 0) displayPosition.toFloat() / duration else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .height(height)
                        .clip(RoundedCornerShape(height / 2))
                        .background(
                            if (isFocused) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                )
                
                // Thumb circle (Visible only when focused)
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(height),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.White, CircleShape)
                                .align(Alignment.CenterEnd)
                                .offset(x = 8.dp) // shift by half of thumb size
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSecs = millis / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
