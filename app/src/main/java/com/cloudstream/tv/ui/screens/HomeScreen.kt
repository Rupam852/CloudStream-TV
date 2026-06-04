package com.cloudstream.tv.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.cloudstream.tv.data.DriveFile
import com.cloudstream.tv.data.DriveLink
import com.cloudstream.tv.data.DriveRepository
import com.cloudstream.tv.network.GoogleDriveClient
import com.cloudstream.tv.ui.components.TVCard
import com.cloudstream.tv.ui.components.TVFocusableItem
import com.cloudstream.tv.ui.components.TVSearchBar
import com.cloudstream.tv.ui.components.TVSidebarItem
import com.cloudstream.tv.ui.components.TVWideCard
import com.cloudstream.tv.ui.theme.CloudStreamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: DriveRepository,
    onPlayVideo: (DriveFile, List<DriveFile>) -> Unit,
    onStartSlideshow: (DriveFile, List<DriveFile>) -> Unit,
    onNavigateToOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Preferences & Layout States
    var isDarkTheme by remember { mutableStateOf(repository.isDarkTheme()) }
    var isGridView by remember { mutableStateOf(repository.isGridView()) }

    // Saved folders list
    var savedFolders = remember { mutableStateListOf<DriveLink>().apply { addAll(repository.getSavedLinks()) } }
    var selectedFolderId by remember { mutableStateOf(repository.getLastSelectedFolderId()) }
    
    // Navigation stack for folders
    val folderNavigationStack = remember { mutableStateListOf<String>() }
    var currentFolderId by remember { mutableStateOf(selectedFolderId) }

    // Files state
    var filesList by remember { mutableStateOf<List<DriveFile>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(false) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    
    // Search & Filter
    var searchQuery by remember { mutableStateOf("") }
    
    // Backdrop blur representation
    var backdropUrl by remember { mutableStateOf<String?>(null) }
    
    // Modals/Overlays
    var showAddFolderDialog by remember { mutableStateOf(false) }

    // Load folder contents
    fun loadFolder(folderId: String?) {
        if (folderId == null) {
            filesList = emptyList()
            return
        }
        
        isLoadingFiles = true
        loadingError = null
        coroutineScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val oauthToken = repository.getAccessToken()
                    GoogleDriveClient.fetchFolderContents(folderId, repository.getApiKey(), oauthToken)
                }
                filesList = results
            } catch (e: Exception) {
                loadingError = "Failed to load files: ${e.localizedMessage}"
                filesList = emptyList()
            } finally {
                isLoadingFiles = false
            }
        }
    }

    // Trigger load when active folder ID changes
    LaunchedEffect(currentFolderId) {
        if (currentFolderId == "root" && !repository.isLoggedIn()) {
            val fallback = savedFolders.firstOrNull()?.id
            selectedFolderId = fallback
            currentFolderId = fallback
        } else {
            loadFolder(currentFolderId)
        }
    }

    // Handle physical TV Back Button
    BackHandler(enabled = folderNavigationStack.isNotEmpty()) {
        val previousFolder = folderNavigationStack.removeAt(folderNavigationStack.lastIndex)
        currentFolderId = previousFolder
    }

    // Filter files based on search
    val filteredFiles = remember(filesList, searchQuery) {
        if (searchQuery.isBlank()) {
            filesList
        } else {
            filesList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Recently viewed list
    var recentlyViewedList by remember { mutableStateOf(repository.getRecentlyViewed()) }

    CloudStreamTheme.extraColors.run {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Dynamic Backdrop Blur of Selected Card (using provider to isolate recomposition)
            HomeScreenBackdrop(backdropUrlProvider = { backdropUrl })

            // 2. Main Row Layout: Sidebar + Content
            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar panel
                var isSidebarExpanded by remember { mutableStateOf(false) }
                val sidebarWidth by animateFloatAsState(
                    targetValue = if (isSidebarExpanded) 220f else 64f,
                    animationSpec = tween(200),
                    label = "sidebarWidth"
                )

                Column(
                    modifier = Modifier
                        .width(sidebarWidth.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                        .focusRequester(remember { FocusRequester() })
                        .onFocusChanged { focusState ->
                            isSidebarExpanded = focusState.hasFocus
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Brand / Logo
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Expand/Collapse Sidebar focus trigger
                    TVFocusableItem(
                        onClick = { isSidebarExpanded = !isSidebarExpanded },
                        shape = RoundedCornerShape(8.dp),
                        scaleOnFocus = 1.02f
                    ) { isFocused ->
                        LaunchedEffect(isFocused) {
                            if (isFocused) isSidebarExpanded = true
                        }
                        TVSidebarItem(
                            title = "Folders List",
                            icon = Icons.Default.SwapHoriz,
                            isSelected = false,
                            onSelect = { isSidebarExpanded = !isSidebarExpanded },
                            isExpanded = isSidebarExpanded
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dynamic folder switching items
                    TvLazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedFolders) { folder ->
                            TVSidebarItem(
                                title = folder.name,
                                icon = Icons.Default.Folder,
                                isSelected = currentFolderId == folder.id && selectedFolderId != "root",
                                onSelect = {
                                    folderNavigationStack.clear()
                                    selectedFolderId = folder.id
                                    currentFolderId = folder.id
                                    repository.setLastSelectedFolderId(folder.id)
                                    loadFolder(folder.id)
                                    Toast.makeText(context, "Switched to: ${folder.name}", Toast.LENGTH_SHORT).show()
                                },
                                isExpanded = isSidebarExpanded,
                                onLongSelect = {
                                    repository.deleteLink(folder.id)
                                    savedFolders.clear()
                                    savedFolders.addAll(repository.getSavedLinks())
                                    selectedFolderId = repository.getLastSelectedFolderId()
                                    currentFolderId = selectedFolderId
                                    Toast.makeText(context, "Removed: ${folder.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TVSidebarItem(
                        title = "Add Link",
                        icon = Icons.Default.Add,
                        isSelected = false,
                        onSelect = { showAddFolderDialog = true },
                        isExpanded = isSidebarExpanded
                    )


                    TVSidebarItem(
                        title = if (isDarkTheme) "Light Theme" else "Dark Theme",
                        icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        isSelected = false,
                        onSelect = {
                            isDarkTheme = !isDarkTheme
                            repository.setDarkTheme(isDarkTheme)
                            // Restart or recompose theme
                        },
                        isExpanded = isSidebarExpanded
                    )

                    TVSidebarItem(
                        title = if (isGridView) "List View" else "Grid View",
                        icon = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                        isSelected = false,
                        onSelect = {
                            isGridView = !isGridView
                            repository.setGridView(isGridView)
                        },
                        isExpanded = isSidebarExpanded
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TVSidebarItem(
                        title = "Logout",
                        icon = Icons.Default.ExitToApp,
                        isSelected = false,
                        onSelect = {
                            // Reset credentials
                            repository.clearOAuthTokens()
                            // Delete all saved links
                            repository.getSavedLinks().forEach { repository.deleteLink(it.id) }
                            // Clear history
                            repository.clearRecentlyViewed()
                            // Navigate to welcome onboarding screen
                            onNavigateToOnboarding()
                            Toast.makeText(context, "Logged out and reset successfully!", Toast.LENGTH_SHORT).show()
                        },
                        isExpanded = isSidebarExpanded
                    )
                }

                // Main Content Panel
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    // Header / Active Folder Title
                    val currentFolderTitle = savedFolders.find { it.id == selectedFolderId }?.name ?: "CloudStream TV"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentFolderTitle + (if (currentFolderId != selectedFolderId) " (Subfolder)" else ""),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Search bar
                        TVSearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.width(300.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Recently Viewed" Shelf (if present)
                    if (recentlyViewedList.isNotEmpty() && currentFolderId == selectedFolderId && searchQuery.isBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recently Streamed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            TVFocusableItem(
                                onClick = {
                                    repository.clearRecentlyViewed()
                                    recentlyViewedList = emptyList()
                                    Toast.makeText(context, "History cleared successfully!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) { isFocused ->
                                Text(
                                    text = "Clear History",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .background(
                                            if (isFocused) MaterialTheme.colorScheme.errorContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(recentlyViewedList) { file ->
                                val icon = if (file.isVideo) Icons.Default.Movie else Icons.Default.Image
                                TVWideCard(
                                    title = file.name,
                                    subtitle = if (file.isVideo) "Video File" else "Photo File",
                                    icon = icon,
                                    badgeText = "Recent",
                                    onClick = {
                                        if (file.isVideo) {
                                            onPlayVideo(file, listOf(file))
                                        } else if (file.isImage) {
                                            onStartSlideshow(file, listOf(file))
                                        }
                                    },
                                    onLongClick = {
                                        // Option to remove from history
                                        val current = recentlyViewedList.toMutableList()
                                        current.remove(file)
                                        repository.clearRecentlyViewed()
                                        current.forEach { repository.addToRecentlyViewed(it) }
                                        recentlyViewedList = repository.getRecentlyViewed()
                                        Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    // Main items view
                    Box(modifier = Modifier.weight(1f)) {
                        if (isLoadingFiles) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (loadingError != null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = loadingError!!,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TVFocusableItem(
                                    onClick = { loadFolder(currentFolderId) },
                                    shape = RoundedCornerShape(20.dp)
                                ) { isFocused ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isFocused) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "Retry Loading",
                                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        } else if (savedFolders.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Google Drive folders linked yet.",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TVFocusableItem(
                                    onClick = { onNavigateToOnboarding() },
                                    shape = RoundedCornerShape(20.dp)
                                ) { isFocused ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isFocused) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 24.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = "Link Google Drive Folder",
                                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else if (filteredFiles.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No files match your search." else "No media files found in this folder.",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TVFocusableItem(
                                    onClick = { showAddFolderDialog = true },
                                    shape = RoundedCornerShape(20.dp)
                                ) { isFocused ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isFocused) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "Add Google Drive Folder",
                                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            // Layout choice: Grid vs List
                            if (isGridView) {
                                TvLazyVerticalGrid(
                                    columns = TvGridCells.Adaptive(150.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    items(filteredFiles) { file ->
                                        val icon = when {
                                            file.isFolder -> Icons.Default.Folder
                                            file.isVideo -> Icons.Default.Movie
                                            else -> Icons.Default.Image
                                        }
                                        TVCard(
                                            title = file.name,
                                            subtitle = when {
                                                file.isFolder -> "Folder"
                                                file.isVideo -> "Video"
                                                else -> "Image"
                                            },
                                            icon = icon,
                                            onClick = {
                                                if (file.isFolder) {
                                                    folderNavigationStack.add(currentFolderId!!)
                                                    currentFolderId = file.id
                                                } else if (file.isVideo) {
                                                    repository.addToRecentlyViewed(file)
                                                    recentlyViewedList = repository.getRecentlyViewed()
                                                    // Pass list of other videos in this folder for playlist next/prev support
                                                    onPlayVideo(file, filesList.filter { it.isVideo })
                                                } else if (file.isImage) {
                                                    repository.addToRecentlyViewed(file)
                                                    recentlyViewedList = repository.getRecentlyViewed()
                                                    // Pass list of other images in this folder for slideshow cycle
                                                    onStartSlideshow(file, filesList.filter { it.isImage })
                                                }
                                            },
                                            onFocus = {
                                                // Update background image dynamic glow preview
                                                backdropUrl = if (file.isFolder) null else file.thumbnailUrl ?: file.streamUrl
                                            },
                                            iconTint = if (file.isFolder) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                            onLongClick = if (file.isFolder) {
                                                {
                                                    // Delete folder capability on long press
                                                    if (file.id != selectedFolderId) {
                                                        Toast.makeText(context, "Actions only available on sidebar", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        repository.deleteLink(file.id)
                                                        savedFolders.clear()
                                                        savedFolders.addAll(repository.getSavedLinks())
                                                        selectedFolderId = repository.getLastSelectedFolderId()
                                                        currentFolderId = selectedFolderId
                                                        Toast.makeText(context, "Deleted: ${file.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else null
                                        )
                                    }
                                }
                            } else {
                                // List View Layout
                                TvLazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    items(filteredFiles) { file ->
                                        val icon = when {
                                            file.isFolder -> Icons.Default.Folder
                                            file.isVideo -> Icons.Default.Movie
                                            else -> Icons.Default.Image
                                        }
                                        TVFocusableItem(
                                            onClick = {
                                                if (file.isFolder) {
                                                    folderNavigationStack.add(currentFolderId!!)
                                                    currentFolderId = file.id
                                                } else if (file.isVideo) {
                                                    repository.addToRecentlyViewed(file)
                                                    recentlyViewedList = repository.getRecentlyViewed()
                                                    onPlayVideo(file, filesList.filter { it.isVideo })
                                                } else if (file.isImage) {
                                                    repository.addToRecentlyViewed(file)
                                                    recentlyViewedList = repository.getRecentlyViewed()
                                                    onStartSlideshow(file, filesList.filter { it.isImage })
                                                }
                                            },
                                            onFocus = {
                                                backdropUrl = if (file.isFolder) null else file.thumbnailUrl ?: file.streamUrl
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            scaleOnFocus = 1.02f,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { isFocused ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                                                        else MaterialTheme.colorScheme.surface
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (file.isFolder) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = file.name,
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = if (file.isFolder) "Folder" else if (file.isVideo) "Video File" else "Photo File",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = CloudStreamTheme.extraColors.textMuted
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
            }

            // 3. Dialog Overlays
            if (showAddFolderDialog) {
                AddFolderOverlay(
                    repository = repository,
                    onDismiss = { showAddFolderDialog = false },
                    onFolderAdded = {
                        savedFolders.clear()
                        savedFolders.addAll(repository.getSavedLinks())
                        selectedFolderId = repository.getLastSelectedFolderId()
                        currentFolderId = selectedFolderId
                        showAddFolderDialog = false
                    }
                )
            }
        }
    }
}

// Dialog-like overlay for adding a new folder
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddFolderOverlay(
    repository: DriveRepository,
    onDismiss: () -> Unit,
    onFolderAdded: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        var urlInput by remember { mutableStateOf("") }
        var nameInput by remember { mutableStateOf("") }
        var isVerifying by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
            ) {
                Text(
                    text = "Add Google Drive Link",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Folder Link or ID", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                TVSearchBar(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    focusRequester = focusRequester
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Custom Name", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                TVSearchBar(value = nameInput, onValueChange = { nameInput = it })

                Spacer(modifier = Modifier.height(20.dp))

                if (isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    if (errorMsg != null) {
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TVFocusableItem(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) { isFocused ->
                            Text(
                                text = "Cancel",
                                modifier = Modifier
                                    .background(
                                        if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                                        else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        TVFocusableItem(
                            onClick = {
                                if (urlInput.isBlank()) {
                                    errorMsg = "Link cannot be empty."
                                    return@TVFocusableItem
                                }
                                isVerifying = true
                                errorMsg = null
                                scope.launch {
                                    val folderId = GoogleDriveClient.extractFolderId(urlInput)
                                    if (folderId == null) {
                                        errorMsg = "Could not extract folder ID."
                                        isVerifying = false
                                        return@launch
                                    }
                                    val isValid = withContext(Dispatchers.IO) {
                                        val oauthToken = repository.getAccessToken()
                                        GoogleDriveClient.validateFolder(folderId, repository.getApiKey(), oauthToken)
                                    }
                                    if (isValid) {
                                        val link = DriveLink(
                                            id = folderId,
                                            name = nameInput.ifBlank { "Drive Folder (${folderId.take(6)}...)" },
                                            url = urlInput
                                        )
                                        repository.saveLink(link)
                                        repository.setLastSelectedFolderId(folderId)
                                        Toast.makeText(context, "Folder added successfully!", Toast.LENGTH_SHORT).show()
                                        onFolderAdded()
                                    } else {
                                        errorMsg = "Verification failed. Link is private or inaccessible."
                                        isVerifying = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) { isFocused ->
                            Text(
                                text = "Add Link",
                                modifier = Modifier
                                    .background(
                                        if (isFocused) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


// Dialog-like overlay for Google Device Sign-in
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GoogleLoginOverlay(
    repository: DriveRepository,
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        var userCode by remember { mutableStateOf("") }
        var verificationUrl by remember { mutableStateOf("") }
        var isPolling by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        
        var retryKey by remember { mutableStateOf(0) }

        val context = LocalContext.current
        val cancelFocusRequester = remember { FocusRequester() }

        LaunchedEffect(retryKey) {
            isPolling = true
            errorMsg = null
            userCode = ""
            verificationUrl = ""
            val clientId = repository.getOAuthClientId()
            try {
                val response = com.cloudstream.tv.network.GoogleDriveClient.requestDeviceCode(clientId)
                userCode = response.user_code
                verificationUrl = response.verification_url
                
                var timeRemaining = response.expires_in
                val interval = response.interval.toLong()
                val clientSecret = repository.getOAuthClientSecret()
                
                while (isPolling && timeRemaining > 0) {
                    kotlinx.coroutines.delay(interval * 1000)
                    timeRemaining -= interval.toInt()
                    
                    val tokenResponse = com.cloudstream.tv.network.GoogleDriveClient.pollDeviceToken(clientId, clientSecret, response.device_code)
                    if (tokenResponse != null) {
                        if (tokenResponse.access_token != null) {
                            val email = com.cloudstream.tv.network.GoogleDriveClient.fetchUserEmail(tokenResponse.access_token)
                            val expiry = System.currentTimeMillis() + (tokenResponse.expires_in ?: 3600) * 1000
                            repository.saveOAuthTokens(
                                tokenResponse.access_token,
                                tokenResponse.refresh_token,
                                expiry,
                                email
                            )
                            isPolling = false
                            Toast.makeText(context, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                            break
                        } else if (tokenResponse.error == "authorization_pending") {
                            // Keep waiting
                        } else {
                            errorMsg = tokenResponse.error_description ?: "Authentication failed."
                            isPolling = false
                            break
                        }
                    }
                }
                if (timeRemaining <= 0 && isPolling) {
                    errorMsg = "Code expired. Please try again."
                    isPolling = false
                }
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to communicate with Google authentication servers."
                isPolling = false
            }
        }

        // Auto-request focus on the Cancel button when dialog opens
        LaunchedEffect(Unit) {
            cancelFocusRequester.requestFocus()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(480.dp)
                    .height(380.dp) // Fixed height to prevent layout jumps/flickering
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sign In with Google",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Scroll-free / jump-free content container
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (userCode.isBlank() && errorMsg == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Requesting code from Google...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else if (errorMsg != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = errorMsg!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        val qrCodeUrl = remember(verificationUrl, userCode) {
                            val qrUrl = if (verificationUrl.contains("google.com")) {
                                "${verificationUrl.ifBlank { "https://www.google.com/device" }}?user_code=$userCode"
                            } else {
                                verificationUrl
                            }
                            "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${java.net.URLEncoder.encode(qrUrl, "UTF-8")}"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1.2f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "On your phone or computer, go to:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = verificationUrl.ifBlank { "google.com/device" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "And enter this code:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = userCode,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Waiting for activation...",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    AsyncImage(
                                        model = qrCodeUrl,
                                        contentDescription = "Sign in QR Code",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Or scan this QR code",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Single, permanent bottom action row to anchor focus & prevent flickering
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (errorMsg != null) {
                        TVFocusableItem(
                            onClick = {
                                retryKey++
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .background(
                                        if (isFocused) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Retry",
                                    color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    TVFocusableItem(
                        onClick = {
                            isPolling = false
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(cancelFocusRequester),
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .width(if (errorMsg != null) 180.dp else 260.dp)
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreenBackdrop(
    backdropUrlProvider: () -> String?,
    modifier: Modifier = Modifier
) {
    val backdropUrl = backdropUrlProvider()
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(0.12f)
    ) {
        AnimatedVisibility(
            visible = !backdropUrl.isNullOrBlank(),
            enter = fadeIn(animationSpec = tween(600)),
            exit = fadeOut(animationSpec = tween(600))
        ) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Ambient gradient overlay to meld into theme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                        )
                    )
                )
        )
    }
}
