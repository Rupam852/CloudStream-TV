package com.cloudstream.tv.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cloudstream.tv.network.NetworkUtils
import com.cloudstream.tv.ui.theme.CloudStreamTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ConnectivityDialog(
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isChecking by remember { mutableStateOf(false) }
    
    val retryFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }

    // Auto-focus the Retry button when dialog is shown
    LaunchedEffect(Unit) {
        delay(300)
        try {
            retryFocusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Auto-check for internet connection every 2 seconds.
    // If connection is restored, automatically trigger onRetry() and dismiss dialog.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000L)
            if (NetworkUtils.isInternetAvailable(context)) {
                isChecking = true
                delay(500L) // Visual feedback spinner/check
                onRetry()
                break
            }
        }
    }

    fun handleManualRetry() {
        isChecking = true
        if (NetworkUtils.isInternetAvailable(context)) {
            onRetry()
        } else {
            isChecking = false
            Toast.makeText(context, "No connection. Please check your TV settings.", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = modifier
                    .width(460.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E1E24),
                                Color(0xFF121214)
                            )
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                // Top-Right Cancel Button (X icon)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                ) {
                    TVFocusableItem(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .focusRequester(closeFocusRequester),
                        shape = CircleShape,
                        scaleOnFocus = 1.1f,
                        borderColor = CloudStreamTheme.extraColors.focusBorder
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isFocused) Color.White.copy(alpha = 0.15f)
                                    else Color.Transparent,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Dialog",
                                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Warning Icon with soft amber glow
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFE53935).copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title
                    Text(
                        text = "Connection Disconnected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Description
                    Text(
                        text = "Please check your TV's internet settings. The app will automatically resume your task as soon as the connection is restored.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Retry Button
                        TVFocusableItem(
                            onClick = { handleManualRetry() },
                            modifier = Modifier
                                .width(160.dp)
                                .height(44.dp)
                                .focusRequester(retryFocusRequester),
                            borderColor = CloudStreamTheme.extraColors.focusBorder,
                            shape = RoundedCornerShape(10.dp),
                            scaleOnFocus = 1.05f
                        ) { isFocused ->
                            val backgroundColor = if (isFocused) CloudStreamTheme.extraColors.focusBorder
                            else Color.White.copy(alpha = 0.08f)
                            
                            val textColor = if (isFocused) Color.Black else Color.White

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(backgroundColor, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        tint = textColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isChecking) "Checking..." else "Retry Now",
                                        color = textColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Cancel Button
                        TVFocusableItem(
                            onClick = onDismiss,
                            modifier = Modifier
                                .width(120.dp)
                                .height(44.dp),
                            borderColor = CloudStreamTheme.extraColors.focusBorder.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            scaleOnFocus = 1.05f
                        ) { isFocused ->
                            val backgroundColor = if (isFocused) Color.White.copy(alpha = 0.15f)
                            else Color.Transparent

                            val textColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f)

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(backgroundColor, RoundedCornerShape(10.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = textColor,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
