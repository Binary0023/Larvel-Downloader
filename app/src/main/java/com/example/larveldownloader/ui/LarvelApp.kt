package com.example.larveldownloader.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.larveldownloader.download.DownloadProgress
import com.example.larveldownloader.model.DownloadItem
import com.example.larveldownloader.model.DownloadStatus
import com.example.larveldownloader.model.VideoInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

data class StreamItem(
    val displayName: String,
    val url: String,
    val format: String,
    val fileSize: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LarvelApp(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var showDownloadStarted by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val surfaceColor = MaterialTheme.colorScheme.surface

    LaunchedEffect(showDownloadStarted) {
        if (showDownloadStarted) {
            delay(2000)
            showDownloadStarted = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (selectedTab == 0) "Extract" else "Downloads",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Settings, "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (selectedTab == 0) Icons.Filled.Search else Icons.Outlined.Search,
                                "Extract"
                            )
                        },
                        label = { Text("Extract") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NavigationBarItem(
                        icon = {
                            BadgedBox(
                                badge = {
                                    val activeCount = downloads.count { it.status == DownloadStatus.DOWNLOADING }
                                    if (activeCount > 0) {
                                        Badge { Text("$activeCount") }
                                    }
                                }
                            ) {
                                Icon(
                                    if (selectedTab == 1) Icons.Filled.Download else Icons.Outlined.Download,
                                    "Downloads"
                                )
                            }
                        },
                        label = { Text("Downloads") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .drawWithContent {
                        drawContent()
                        // Top gradient overlay
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    surfaceColor,
                                    surfaceColor.copy(alpha = 0.8f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = 150f
                            )
                        )
                    }
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.padding(padding),
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "tab_content"
                ) { tab ->
                    when (tab) {
                        0 -> ExtractScreen(
                            viewModel = viewModel,
                            onDownloadStarted = {
                                showDownloadStarted = true
                                selectedTab = 1
                            }
                        )
                        1 -> DownloadsScreen(viewModel)
                    }
                }
            }
        }

        // Download started snackbar
        AnimatedVisibility(
            visible = showDownloadStarted,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface
                    )
                    Text(
                        "Download started!",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Settings bottom sheet
        if (showSettings) {
            SettingsSheet(
                viewModel = viewModel,
                onDismiss = { showSettings = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Settings DataStore
    val settingsDataStore = remember { com.example.larveldownloader.data.SettingsDataStore(context) }
    val savedBufferSize by settingsDataStore.bufferSize.collectAsState(initial = 32768)
    val savedMaxParallel by settingsDataStore.maxParallelDownloads.collectAsState(initial = 3)
    
    var ytDlpVersion by remember { mutableStateOf("Checking...") }
    var isInstallingYtDlp by remember { mutableStateOf(false) }
    var installProgress by remember { mutableIntStateOf(0) }
    
    val ytDlpExtractor = remember { com.example.larveldownloader.extractor.YtDlpExtractor(context) }
    
    LaunchedEffect(Unit) {
        ytDlpVersion = if (ytDlpExtractor.isInstalled()) {
            ytDlpExtractor.getVersion()
        } else {
            "Not installed"
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(24.dp))
            
            // yt-dlp Section
            Text(
                "Extractors",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            
            // yt-dlp Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("yt-dlp", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                when {
                                    ytDlpExtractor.isInstalled() && ytDlpVersion != "Unknown" && ytDlpVersion != "Checking..." -> "v$ytDlpVersion • Ready"
                                    ytDlpExtractor.isInstalled() -> "Ready"
                                    else -> "Not initialized"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ytDlpExtractor.isInstalled()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        // Status indicator
                        Surface(
                            shape = CircleShape,
                            color = if (ytDlpExtractor.isInstalled()) 
                                Color(0xFF4CAF50) // Green
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        ) {}
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    if (isInstallingYtDlp) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Updating...", style = MaterialTheme.typography.bodySmall)
                    } else {
                        // Only show update button - library handles installation automatically
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isInstallingYtDlp = true
                                    ytDlpExtractor.update().onSuccess { status ->
                                        ytDlpVersion = ytDlpExtractor.getVersion()
                                        Toast.makeText(context, "Update: $status", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    isInstallingYtDlp = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = ytDlpExtractor.isInstalled()
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Check for Updates")
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Instagram • Facebook • TikTok • Twitter • Reddit • 1000+ more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (!ytDlpExtractor.isInstalled()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "⚠️ Not ready. Restart app if this persists.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // NewPipe info
            SettingsItem(
                icon = Icons.Default.PlayCircle,
                title = "NewPipe Extractor",
                subtitle = "YouTube • SoundCloud • Bandcamp",
                onClick = {}
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            Text(
                "Downloads",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            
            var showBufferDialog by remember { mutableStateOf(false) }
            var showParallelDialog by remember { mutableStateOf(false) }
            var showAboutDialog by remember { mutableStateOf(false) }
            var selectedBuffer by remember { mutableIntStateOf(savedBufferSize / 1024) } // Convert to KB
            var selectedParallel by remember { mutableIntStateOf(savedMaxParallel) }
            
            // Update when saved values change
            LaunchedEffect(savedBufferSize) { selectedBuffer = savedBufferSize / 1024 }
            LaunchedEffect(savedMaxParallel) { selectedParallel = savedMaxParallel }
            
            // Buffer Size
            SettingsItem(
                icon = Icons.Default.Speed,
                title = "Buffer Size",
                subtitle = "${selectedBuffer} KB",
                onClick = { showBufferDialog = true }
            )
            
            // Max Parallel Downloads
            SettingsItem(
                icon = Icons.Default.Download,
                title = "Max Parallel Downloads",
                subtitle = "$selectedParallel downloads",
                onClick = { showParallelDialog = true }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // About
            SettingsItem(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "Larvel Downloader v1.0",
                onClick = { showAboutDialog = true }
            )
            
            // Buffer Size Dialog
            if (showBufferDialog) {
                var tempBuffer by remember { mutableIntStateOf(selectedBuffer) }
                AlertDialog(
                    onDismissRequest = { showBufferDialog = false },
                    title = { Text("Buffer Size") },
                    text = {
                        Column {
                            listOf(8, 16, 32, 64, 128).forEach { size ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { tempBuffer = size }
                                        .background(if (tempBuffer == size) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = tempBuffer == size,
                                        onClick = { tempBuffer = size }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("$size KB" + if (size == 32) " (Recommended)" else "")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { 
                            selectedBuffer = tempBuffer
                            scope.launch { settingsDataStore.setBufferSize(tempBuffer * 1024) }
                            showBufferDialog = false 
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBufferDialog = false }) { Text("Cancel") }
                    }
                )
            }
            
            // Parallel Downloads Dialog
            if (showParallelDialog) {
                var tempParallel by remember { mutableIntStateOf(selectedParallel) }
                AlertDialog(
                    onDismissRequest = { showParallelDialog = false },
                    title = { Text("Max Parallel Downloads") },
                    text = {
                        Column {
                            (1..5).forEach { count ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { tempParallel = count }
                                        .background(if (tempParallel == count) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = tempParallel == count,
                                        onClick = { tempParallel = count }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("$count download${if (count > 1) "s" else ""}")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { 
                            selectedParallel = tempParallel
                            scope.launch { settingsDataStore.setMaxParallelDownloads(tempParallel) }
                            showParallelDialog = false 
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showParallelDialog = false }) { Text("Cancel") }
                    }
                )
            }
            
            // About Screen (Full Screen Dialog)
            if (showAboutDialog) {
                AboutScreen(onDismiss = { showAboutDialog = false })
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
fun ExtractScreen(viewModel: MainViewModel, onDownloadStarted: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val videoInfo by viewModel.currentVideoInfo.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    var urlText by remember { mutableStateOf(urlInput) }
    var selectedStreamTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(urlInput) {
        if (urlInput.isNotEmpty()) urlText = urlInput
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            UrlInputCard(
                url = urlText,
                onUrlChange = { urlText = it },
                onExtract = { viewModel.extractVideo(urlText) },
                isLoading = uiState.isLoading
            )
        }

        if (uiState.isLoading) {
            item { LoadingCard() }
        }

        uiState.error?.let { error ->
            item { ErrorCard(error) }
        }

        videoInfo?.let { info ->
            item { VideoInfoCard(info) }

            item {
                StreamTabs(
                    selectedTab = selectedStreamTab,
                    onTabSelected = { selectedStreamTab = it },
                    videoCount = info.videoStreams.size,
                    audioCount = info.audioStreams.size,
                    videoOnlyCount = info.videoOnlyStreams.size
                )
            }

            val streams = when (selectedStreamTab) {
                0 -> info.videoStreams.map { StreamItem(it.displayName, it.url, it.format, it.fileSize) }
                1 -> info.audioStreams.map { StreamItem(it.displayName, it.url, it.format, it.fileSize) }
                else -> info.videoOnlyStreams.map { StreamItem(it.displayName, it.url, it.format, it.fileSize) }
            }

            items(streams, key = { it.url }) { stream ->
                StreamItemCard(
                    stream = stream,
                    onDownload = {
                        when (selectedStreamTab) {
                            0 -> viewModel.startDownload(info, stream.url, stream.displayName, stream.format, stream.fileSize)
                            1 -> viewModel.startAudioDownload(info, stream.url, stream.displayName, stream.format, stream.fileSize)
                            else -> viewModel.startVideoOnlyDownload(info, stream.url, stream.displayName, stream.format, stream.fileSize)
                        }
                        onDownloadStarted()
                    }
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text("Extracting video info...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun UrlInputCard(url: String, onUrlChange: (String) -> Unit, onExtract: () -> Unit, isLoading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Link icon with background
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 8.dp).size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Paste video URL here") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(20.dp)
            )

            FilledIconButton(
                onClick = onExtract,
                enabled = !isLoading && url.isNotBlank(),
                modifier = Modifier.size(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Search, "Extract")
                }
            }
        }
    }
}

@Composable
fun VideoInfoCard(info: VideoInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = info.thumbnailUrl,
                    contentDescription = info.title,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Text(formatDuration(info.duration), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(info.uploaderName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(formatViewCount(info.viewCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamTabs(selectedTab: Int, onTabSelected: (Int) -> Unit, videoCount: Int, audioCount: Int, videoOnlyCount: Int) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(selected = selectedTab == 0, onClick = { onTabSelected(0) }, shape = SegmentedButtonDefaults.itemShape(0, 3)) { 
            Text("Video ($videoCount)") 
        }
        SegmentedButton(selected = selectedTab == 1, onClick = { onTabSelected(1) }, shape = SegmentedButtonDefaults.itemShape(1, 3)) { 
            Text("Audio ($audioCount)") 
        }
        SegmentedButton(selected = selectedTab == 2, onClick = { onTabSelected(2) }, shape = SegmentedButtonDefaults.itemShape(2, 3)) { 
            Text("Video Only ($videoOnlyCount)") 
        }
    }
}

@Composable
fun StreamItemCard(stream: StreamItem, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stream.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stream.format.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatFileSize(stream.fileSize), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledTonalIconButton(onClick = onDownload, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Download, "Download", modifier = Modifier.size(22.dp))
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(viewModel: MainViewModel) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val progressMap by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedItems.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "header"
            ) { selecting ->
                if (selecting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedItems = emptySet() }) {
                                Icon(Icons.Default.Close, "Clear selection")
                            }
                            Text("${selectedItems.size} selected", fontWeight = FontWeight.Medium)
                        }
                        Row {
                            IconButton(onClick = {
                                shareFiles(context, downloads.filter { it.id in selectedItems })
                                selectedItems = emptySet()
                            }) {
                                Icon(Icons.Default.Share, "Share")
                            }
                            IconButton(onClick = {
                                selectedItems.forEach { viewModel.cancelDownload(it) }
                                selectedItems = emptySet()
                            }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (downloads.isNotEmpty()) {
                                Text("${downloads.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val activeCount = downloads.count { it.status == DownloadStatus.DOWNLOADING }
                        if (activeCount > 0) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("$activeCount active", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }

        if (downloads.isEmpty()) {
            item { EmptyDownloadsCard() }
        }

        items(downloads, key = { it.id }) { item ->
            val isSelected = item.id in selectedItems
            
            DownloadItemCard(
                item = item,
                progress = progressMap[item.id],
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedItems = selectedItems + item.id
                },
                onClick = {
                    if (isSelectionMode) {
                        selectedItems = if (isSelected) selectedItems - item.id else selectedItems + item.id
                    } else if (item.status == DownloadStatus.COMPLETED) {
                        openFile(context, item)
                    }
                },
                onPause = { viewModel.pauseDownload(item.id) },
                onResume = { viewModel.resumeDownload(item) },
                onCancel = { viewModel.cancelDownload(item.id) }
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun EmptyDownloadsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CloudDownload, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            Spacer(Modifier.height(20.dp))
            Text("No downloads yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text("Extract a video and tap download", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadItemCard(
    item: DownloadItem,
    progress: DownloadProgress?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val percentage = progress?.percentage ?: if (item.totalSize > 0) ((item.downloadedSize * 100) / item.totalSize).toInt() else 0
    val downloadedSize = progress?.downloadedBytes ?: item.downloadedSize
    val animatedProgress by animateFloatAsState(targetValue = percentage / 100f, animationSpec = tween(200), label = "progress")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onLongPress, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                           else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(width = 2.dp) else null
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, checkmarkColor = Color.White))
                    }
                } else if (item.status == DownloadStatus.COMPLETED) {
                    Box(
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayCircle, "Play", modifier = Modifier.size(32.dp), tint = Color.White)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.quality, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (item.status != DownloadStatus.COMPLETED) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        color = when (item.status) {
                            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${formatFileSize(downloadedSize)} / ${formatFileSize(item.totalSize)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$percentage%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(item.status)
                    if (!isSelectionMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            when (item.status) {
                                DownloadStatus.DOWNLOADING -> {
                                    FilledTonalIconButton(onClick = onPause, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Pause, "Pause", Modifier.size(16.dp)) }
                                }
                                DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                                    FilledTonalIconButton(onClick = onResume, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.PlayArrow, "Resume", Modifier.size(16.dp)) }
                                }
                                else -> {}
                            }
                            if (item.status != DownloadStatus.COMPLETED) {
                                FilledTonalIconButton(onClick = onCancel, modifier = Modifier.size(30.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                    Icon(Icons.Default.Close, "Cancel", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: DownloadStatus) {
    val (text, containerColor, contentColor) = when (status) {
        DownloadStatus.PENDING -> Triple("Pending", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        DownloadStatus.DOWNLOADING -> Triple("Downloading", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        DownloadStatus.PAUSED -> Triple("Paused", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        DownloadStatus.COMPLETED -> Triple("Completed", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        DownloadStatus.FAILED -> Triple("Failed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        DownloadStatus.CANCELLED -> Triple("Cancelled", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Surface(shape = RoundedCornerShape(6.dp), color = containerColor) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}

private fun openFile(context: Context, item: DownloadItem) {
    try {
        val file = File(item.filePath)
        if (!file.exists()) { Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show(); return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mimeType = when {
            item.fileName.endsWith(".mp4", true) || item.fileName.endsWith(".webm", true) -> "video/*"
            item.fileName.endsWith(".mp3", true) || item.fileName.endsWith(".m4a", true) || item.fileName.endsWith(".opus", true) -> "audio/*"
            else -> "*/*"
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Open with"))
    } catch (e: Exception) { Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show() }
}

private fun shareFiles(context: Context, items: List<DownloadItem>) {
    try {
        val uris = items.mapNotNull { item ->
            val file = File(item.filePath)
            if (file.exists()) FileProvider.getUriForFile(context, "${context.packageName}.provider", file) else null
        }
        if (uris.isEmpty()) { Toast.makeText(context, "No files to share", Toast.LENGTH_SHORT).show(); return }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share files"))
    } catch (e: Exception) { Toast.makeText(context, "Cannot share files", Toast.LENGTH_SHORT).show() }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

private fun formatViewCount(count: Long): String = when {
    count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> "$count"
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown"
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    // Full screen dialog
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("About", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Download,
                                    null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Larvel Downloader",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Version 1.0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Download videos from 1000+ websites",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Download Location
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Download Location", fontWeight = FontWeight.Medium)
                            Text(
                                "Downloads/LarvelDownloader",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Extractors Section
            item {
                Text(
                    "Extractors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF0000).copy(alpha = 0.1f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.PlayCircle, null, tint = Color(0xFFFF0000))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("NewPipe Extractor", fontWeight = FontWeight.Medium)
                                Text("YouTube, SoundCloud, Bandcamp, PeerTube", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("yt-dlp", fontWeight = FontWeight.Medium)
                                Text("1000+ websites supported", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            
            // Supported Sites Section
            item {
                Text(
                    "Supported Websites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            // Site Categories
            val categories = com.example.larveldownloader.extractor.YtDlpExtractor.SITE_CATEGORIES
            categories.forEach { (category, sites) ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                category,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                sites.joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Note about DNS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Note",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "If you're using an ad-blocking DNS (like AdGuard), some sites may not work. Temporarily disable it if you experience issues.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
        }
    }
}
