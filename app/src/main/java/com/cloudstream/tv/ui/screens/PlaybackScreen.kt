package com.cloudstream.tv.ui.screens

import android.annotation.SuppressLint
import android.view.KeyEvent
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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.cloudstream.tv.ui.components.TVFocusableItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
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

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Playback and UI States
    var isPlaying by remember { mutableStateOf(exoPlayer.playWhenReady) }
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

    // Resolve URL on change
    LaunchedEffect(activeFile) {
        isResolving = true
        resolvedUrl = null
        val token = if (repository.isLoggedIn()) repository.getAccessToken() else null
        oauthToken = token
        val url = GoogleDriveClient.resolveDriveDirectUrl(activeFile.id, token)
        resolvedUrl = url
        isResolving = false
    }

    // Load active file into player when resolved URL is ready
    LaunchedEffect(resolvedUrl, oauthToken) {
        val url = resolvedUrl ?: return@LaunchedEffect
        val mediaItem = MediaItem.fromUri(url)
        val token = oauthToken
        if (token != null && url.contains("googleapis.com")) {
            val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
                setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            }
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            exoPlayer.setMediaSource(mediaSource)
        } else {
            exoPlayer.setMediaItem(mediaItem)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Listen to player state changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady
            }

            override fun onPlaybackStateChanged(state: Int) {
                duration = exoPlayer.duration.coerceAtLeast(0L)
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    if (activeIndex < playlist.size - 1) {
                        activeIndex++
                    } else {
                        exoPlayer.seekTo(0)
                        exoPlayer.pause()
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodically update playback positions (seekbar timeline)
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            bufferPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
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

    // Focus play button on overlay reveal
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(100)
            playButtonFocusRequester.requestFocus()
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
        } else {
            exoPlayer.play()
        }
    }

    fun seekForward() {
        showControls()
        exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration))
        currentPosition = exoPlayer.currentPosition
    }

    fun seekRewind() {
        showControls()
        exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
        currentPosition = exoPlayer.currentPosition
    }

    fun skipNext() {
        if (activeIndex < playlist.size - 1) {
            showControls()
            activeIndex++
        }
    }

    fun skipPrevious() {
        if (activeIndex > 0) {
            showControls()
            activeIndex--
        }
    }

    fun toggleAspectRatio() {
        showControls()
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
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
                // Top details: File Name & Time
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
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
                    Text(
                        text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Bottom area: Seekbar & Controls buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 32.dp, end = 32.dp)
                ) {
                    // Custom seekbar (Progress & Buffer bars)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        // Buffer indicator
                        val bufferFraction = if (duration > 0) bufferPosition.toFloat() / duration else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(bufferFraction)
                                .height(6.dp)
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                        // Progress indicator
                        val progressFraction = if (duration > 0) currentPosition.toFloat() / duration else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

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
                            modifier = Modifier.focusRequester(playButtonFocusRequester),
                            shape = CircleShape
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFocused) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                    ),
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

                        // Playback Speed Toggle
                        TVFocusableItem(onClick = { toggleSpeed() }, shape = RoundedCornerShape(8.dp)) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .background(
                                        if (isFocused) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        RoundedCornerShape(8.dp)
                                    )
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
        shape = CircleShape,
        modifier = Modifier.alpha(if (enabled) 1.0f else 0.4f)
    ) { isFocused ->
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
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
