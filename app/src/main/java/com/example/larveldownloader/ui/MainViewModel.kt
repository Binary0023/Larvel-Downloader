package com.example.larveldownloader.ui

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.larveldownloader.data.AppDatabase
import com.example.larveldownloader.download.DownloadManager
import com.example.larveldownloader.download.DownloadProgress
import com.example.larveldownloader.extractor.VideoExtractor
import com.example.larveldownloader.model.DownloadItem
import com.example.larveldownloader.model.DownloadStatus
import com.example.larveldownloader.model.StreamType
import com.example.larveldownloader.model.VideoInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(private val context: Context) : ViewModel() {
    
    private val extractor = VideoExtractor(context)
    private val downloadManager = DownloadManager.getInstance(context)
    private val database = AppDatabase.getInstance(context)
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _currentVideoInfo = MutableStateFlow<VideoInfo?>(null)
    val currentVideoInfo: StateFlow<VideoInfo?> = _currentVideoInfo.asStateFlow()
    
    val downloads = database.downloadDao().getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val downloadProgress: StateFlow<Map<Long, DownloadProgress>> = downloadManager.downloadProgress
    
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()
    
    private val _navigateToDownloads = MutableStateFlow(false)
    val navigateToDownloads: StateFlow<Boolean> = _navigateToDownloads.asStateFlow()
    
    init {
        // Clean up any stuck downloads from previous session
        viewModelScope.launch {
            downloadManager.cleanupStuckDownloads()
        }
    }
    
    fun setUrlAndExtract(url: String) {
        _urlInput.value = url
        currentSourceUrl = url
        extractVideo(url)
    }
    
    fun onNavigatedToDownloads() {
        _navigateToDownloads.value = false
    }
    
    // Store current URL for downloads
    private var currentSourceUrl: String = ""
    
    fun extractVideo(url: String) {
        viewModelScope.launch {
            _uiState.value = UiState(isLoading = true)
            _urlInput.value = url // Make sure URL is stored
            currentSourceUrl = url // Also store in dedicated variable
            
            extractor.extractVideoInfo(url).fold(
                onSuccess = { info ->
                    _currentVideoInfo.value = info
                    _uiState.value = UiState(videoInfo = info)
                },
                onFailure = { error ->
                    _uiState.value = UiState(error = error.message ?: "Failed to extract video")
                }
            )
        }
    }
    
    fun startDownload(info: VideoInfo, formatId: String, quality: String, format: String, fileSize: Long) {
        viewModelScope.launch {
            // Use mp4 extension for merged videos
            val ext = if (formatId.contains("+") || formatId.startsWith("best")) "mp4" else format
            val fileName = sanitizeFileName("${info.title}_$quality.$ext")
            val filePath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "LarvelDownloader/$fileName"
            ).absolutePath
            
            // Use stored source URL - this is critical for yt-dlp
            val sourceUrl = currentSourceUrl.ifEmpty { _urlInput.value }
            
            val item = DownloadItem(
                videoId = info.id,
                title = info.title,
                thumbnailUrl = info.thumbnailUrl,
                downloadUrl = formatId, // Format ID for yt-dlp
                sourceUrl = sourceUrl, // Original video page URL
                fileName = fileName,
                filePath = filePath,
                totalSize = fileSize,
                streamType = StreamType.VIDEO,
                quality = quality
            )
            
            val id = downloadManager.addDownload(item)
            downloadManager.startDownload(item.copy(id = id))
        }
    }

    fun startAudioDownload(info: VideoInfo, formatId: String, quality: String, format: String, fileSize: Long) {
        viewModelScope.launch {
            val fileName = sanitizeFileName("${info.title}_$quality.mp3")
            val filePath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "LarvelDownloader/$fileName"
            ).absolutePath
            
            val sourceUrl = currentSourceUrl.ifEmpty { _urlInput.value }
            
            val item = DownloadItem(
                videoId = info.id,
                title = info.title,
                thumbnailUrl = info.thumbnailUrl,
                downloadUrl = formatId,
                sourceUrl = sourceUrl,
                fileName = fileName,
                filePath = filePath,
                totalSize = fileSize,
                streamType = StreamType.AUDIO,
                quality = quality
            )
            
            val id = downloadManager.addDownload(item)
            downloadManager.startDownload(item.copy(id = id))
        }
    }
    
    fun startVideoOnlyDownload(info: VideoInfo, formatId: String, quality: String, format: String, fileSize: Long) {
        viewModelScope.launch {
            val fileName = sanitizeFileName("${info.title}_${quality}_video.$format")
            val filePath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "LarvelDownloader/$fileName"
            ).absolutePath
            
            val sourceUrl = currentSourceUrl.ifEmpty { _urlInput.value }
            
            val item = DownloadItem(
                videoId = info.id,
                title = info.title,
                thumbnailUrl = info.thumbnailUrl,
                downloadUrl = formatId,
                sourceUrl = sourceUrl,
                fileName = fileName,
                filePath = filePath,
                totalSize = fileSize,
                streamType = StreamType.VIDEO_ONLY,
                quality = quality
            )
            
            val id = downloadManager.addDownload(item)
            downloadManager.startDownload(item.copy(id = id))
        }
    }
    
    fun pauseDownload(id: Long) {
        downloadManager.pauseDownload(id)
    }
    
    fun resumeDownload(item: DownloadItem) {
        downloadManager.resumeDownload(item)
    }
    
    fun cancelDownload(id: Long) {
        downloadManager.cancelDownload(id)
    }
    
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(200)
    }
    
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(context) as T
        }
    }
}

data class UiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val videoInfo: VideoInfo? = null
)
