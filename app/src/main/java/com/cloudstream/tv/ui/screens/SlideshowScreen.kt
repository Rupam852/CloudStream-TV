package com.cloudstream.tv.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.cloudstream.tv.data.DriveFile
import com.cloudstream.tv.data.DriveRepository
import com.cloudstream.tv.ui.components.TVFocusableItem
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun SlideshowScreen(
    currentFile: DriveFile,
    photos: List<DriveFile>,
    repository: DriveRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var activeIndex by remember { mutableIntStateOf(photos.indexOfFirst { it.id == currentFile.id }.coerceAtLeast(0)) }
    val activePhoto = remember(activeIndex) { photos.getOrNull(activeIndex) ?: currentFile }
    var oauthToken by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activePhoto) {
        if (repository.isLoggedIn()) {
            oauthToken = repository.getAccessToken()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var intervalSeconds by remember { mutableIntStateOf(5) } // default 5 seconds
    
    var controlsVisible by remember { mutableStateOf(true) }
    var userActivityTrigger by remember { mutableLongStateOf(0L) }

    val playButtonFocusRequester = remember { FocusRequester() }

    // Auto-hide controls overlay after 5 seconds of inactivity
    LaunchedEffect(controlsVisible, userActivityTrigger) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    // Auto-focus play button on overlay reveal
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(100)
            playButtonFocusRequester.requestFocus()
        }
    }

    // Timer loop for cycling images
    LaunchedEffect(isPlaying, activeIndex, intervalSeconds) {
        if (isPlaying) {
            delay(intervalSeconds * 1000L)
            activeIndex = (activeIndex + 1) % photos.size
        }
    }

    fun showControls() {
        controlsVisible = true
        userActivityTrigger = System.currentTimeMillis()
    }

    fun togglePlayPause() {
        showControls()
        isPlaying = !isPlaying
    }

    fun goNext() {
        showControls()
        activeIndex = (activeIndex + 1) % photos.size
    }

    fun goPrev() {
        showControls()
        activeIndex = if (activeIndex > 0) activeIndex - 1 else photos.size - 1
    }

    fun toggleInterval() {
        showControls()
        intervalSeconds = when (intervalSeconds) {
            3 -> 5
            5 -> 10
            10 -> 15
            else -> 3
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls() }
            .onPreviewKeyEvent { keyEvent ->
                showControls()
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            goPrev()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            goNext()
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // 1. Cross-fade Animated Image container
        AnimatedContent(
            targetState = activePhoto,
            transitionSpec = {
                fadeIn(animationSpec = tween(700)) with fadeOut(animationSpec = tween(700))
            },
            modifier = Modifier.fillMaxSize(),
            label = "photoFade"
        ) { photo ->
            val context = LocalContext.current
            val imageModel = remember(photo, oauthToken) {
                val token = oauthToken
                if (token != null && !photo.id.startsWith("http")) {
                    ImageRequest.Builder(context)
                        .data("https://www.googleapis.com/drive/v3/files/${photo.id}?alt=media")
                        .setHeader("Authorization", "Bearer $token")
                        .crossfade(true)
                        .build()
                } else {
                    photo.streamUrl
                }
            }
            AsyncImage(
                model = imageModel,
                contentDescription = photo.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Control overlays (Top detail & Bottom bar)
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
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            ) {
                // Top area: Info & Count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = activePhoto.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Photo Slideshow Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = "${activeIndex + 1} / ${photos.size}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Bottom area: Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Photo
                    TVFocusableItem(onClick = { goPrev() }, shape = CircleShape) { isFocused ->
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
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous Photo",
                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Play / Pause
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

                    Spacer(modifier = Modifier.width(20.dp))

                    // Next Photo
                    TVFocusableItem(onClick = { goNext() }, shape = CircleShape) { isFocused ->
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
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Photo",
                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(48.dp))

                    // Interval Timer Adjustment
                    TVFocusableItem(onClick = { toggleInterval() }, shape = RoundedCornerShape(8.dp)) { isFocused ->
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Slideshow Speed",
                                    tint = if (isFocused) MaterialTheme.colorScheme.onSecondary else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${intervalSeconds}s Interval",
                                    color = if (isFocused) MaterialTheme.colorScheme.onSecondary else Color.White,
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
