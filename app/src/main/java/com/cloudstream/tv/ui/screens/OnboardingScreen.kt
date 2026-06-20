package com.cloudstream.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cloudstream.tv.R
import com.cloudstream.tv.data.DriveLink
import com.cloudstream.tv.data.DriveRepository
import com.cloudstream.tv.network.GoogleDriveClient
import com.cloudstream.tv.ui.components.TVFocusableItem
import com.cloudstream.tv.ui.components.TVSearchBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ValidationState {
    object Idle : ValidationState()
    object Validating : ValidationState()
    object Success : ValidationState()
    data class Error(val message: String) : ValidationState()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OnboardingScreen(
    repository: DriveRepository,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urlInput by remember { mutableStateOf("") }
    var folderNameInput by remember { mutableStateOf("") }
    var validationState by remember { mutableStateOf<ValidationState>(ValidationState.Idle) }
    var showGoogleLoginDialogOption by remember { mutableStateOf<Int?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    
    val inputFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }
    val validateFocusRequester = remember { FocusRequester() }

    // Auto-focus input on launch
    LaunchedEffect(Unit) {
        delay(500)
        inputFocusRequester.requestFocus()
    }

    fun startValidation(url: String, customName: String) {
        if (url.isBlank()) {
            validationState = ValidationState.Error("Please enter a link or folder ID.")
            return
        }

        validationState = ValidationState.Validating
        coroutineScope.launch {
            val folderId = GoogleDriveClient.extractFolderId(url)
            if (folderId == null) {
                validationState = ValidationState.Error("Invalid URL. Could not extract folder ID.")
                return@launch
            }

            val isValid = withContext(Dispatchers.IO) {
                val oauthToken = repository.getAccessToken()
                GoogleDriveClient.validateFolder(folderId, repository.getApiKey(), oauthToken)
            }

            if (isValid) {
                val finalName = customName.ifBlank {
                    if (folderId == "demo-videos") "Demo Videos"
                    else if (folderId == "demo-photos") "Demo Photos"
                    else "Drive Folder (${folderId.take(6)}...)"
                }
                val link = DriveLink(
                    id = folderId,
                    name = finalName,
                    url = url
                )
                repository.saveLink(link)
                repository.setLastSelectedFolderId(folderId)
                validationState = ValidationState.Success
                
                // Wait briefly for visual feedback
                kotlinx.coroutines.delay(1000)
                onOnboardingComplete()
            } else {
                validationState = ValidationState.Error(
                    if (repository.getApiKey().isNullOrBlank())
                        "Could not access folder. Verify that anyone with the link can view it, or try adding a Google API Key in Settings."
                    else
                        "Could not access folder. Verify folder ID and API Key."
                )
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Brand and instructions
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "CloudStream TV Logo",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Welcome to CloudStream TV",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Stream videos, play slideshows, and browse documents directly from your public Google Drive folders.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "IMPORTANT REQUIREMENT",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "The Google Drive folder must be shared as 'Anyone with the link' (Viewer), or you must grant access to it using the Google Account you authenticate with.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Vertical divider
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 24.dp)
        )

        // Right Side: Input & Controls
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .padding(start = 48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Link Google Drive Folder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Enter a public folder link or choose a sample to start.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Folder URL Input
            Text(
                text = "Google Drive Folder URL or ID",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            TVSearchBar(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    // O3 Fix: Clear stale error message as soon as user starts editing
                    if (validationState is ValidationState.Error) {
                        validationState = ValidationState.Idle
                    }
                },
                focusRequester = inputFocusRequester,
                onSearchAction = { nameFocusRequester.requestFocus() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Folder Name Input
            Text(
                text = "Folder Nickname (Optional)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            TVSearchBar(
                value = folderNameInput,
                onValueChange = {
                    folderNameInput = it
                    // O3 Fix: Clear stale error message as soon as user starts editing
                    if (validationState is ValidationState.Error) {
                        validationState = ValidationState.Idle
                    }
                },
                focusRequester = nameFocusRequester,
                onSearchAction = { validateFocusRequester.requestFocus() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (repository.isLoggedIn()) {
                TVFocusableItem(
                    onClick = {
                        if (validationState == ValidationState.Validating) return@TVFocusableItem
                        if (urlInput.isBlank()) {
                            validationState = ValidationState.Error("Please enter a link or folder ID.")
                            return@TVFocusableItem
                        }
                        startValidation(urlInput, folderNameInput)
                    },
                    modifier = Modifier.focusRequester(validateFocusRequester),
                    shape = RoundedCornerShape(20.dp)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .background(
                                if (isFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Link Folder",
                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "If Option 1 fails or reaches its user limit, please try Option 2.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // O2 Fix: Option 1 gets its own FocusRequester.
                    // Previously it shared validateFocusRequester with the logged-in
                    // "Link Folder" button — two nodes sharing one FocusRequester causes
                    // undefined focus behavior and potential crashes.
                    val option1FocusRequester = remember { FocusRequester() }
                    TVFocusableItem(
                        onClick = {
                            if (validationState == ValidationState.Validating) return@TVFocusableItem
                            if (urlInput.isBlank()) {
                                    validationState = ValidationState.Error("Please enter a link or folder ID.")
                                    return@TVFocusableItem
                            }
                            showGoogleLoginDialogOption = 1
                        },
                        modifier = Modifier.focusRequester(option1FocusRequester),
                        shape = RoundedCornerShape(20.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .fillMaxWidth()
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Link Folder & Google Authenticate (Option 1)",
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Separator "— OR —"
                    Text(
                        text = "— OR —",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Button Option 2
                    val option2FocusRequester = remember { FocusRequester() }
                    TVFocusableItem(
                        onClick = {
                            if (validationState == ValidationState.Validating) return@TVFocusableItem
                            if (urlInput.isBlank()) {
                                validationState = ValidationState.Error("Please enter a link or folder ID.")
                                return@TVFocusableItem
                            }
                            showGoogleLoginDialogOption = 2
                        },
                        modifier = Modifier.focusRequester(option2FocusRequester),
                        shape = RoundedCornerShape(20.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .fillMaxWidth()
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Link Folder & Google Authenticate (Option 2)",
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Validation State Indicators
            when (val state = validationState) {
                is ValidationState.Validating -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = "Validating link and listing files...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                }
                is ValidationState.Success -> {
                    Text(
                        text = "✓ Folder linked successfully! Loading...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                is ValidationState.Error -> {
                    Text(
                        text = "✗ " + state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                else -> {}
            }
        }
    }

    if (showGoogleLoginDialogOption != null) {
        GoogleLoginOverlay(
            repository = repository,
            loginOption = showGoogleLoginDialogOption!!,
            onDismiss = { showGoogleLoginDialogOption = null },
            onLoginSuccess = {
                showGoogleLoginDialogOption = null
                startValidation(urlInput, folderNameInput)
            }
        )
    }

}



// Simple delay helper removed — use kotlinx.coroutines.delay() directly
