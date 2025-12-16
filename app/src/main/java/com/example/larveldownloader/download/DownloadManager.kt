package com.example.larveldownloader.download

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.larveldownloader.LarvelApp
import com.example.larveldownloader.data.AppDatabase
import com.example.larveldownloader.extractor.YtDlpExtractor
import com.example.larveldownloader.model.DownloadItem
import com.example.larveldownloader.model.DownloadStatus
import com.example.larveldownloader.model.StreamType
import com.example.larveldownloader.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val database = AppDatabase.getInstance(context)
    private val downloadDao = database.downloadDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ytDlpExtractor = YtDlpExtractor(context)
    
    private val activeDownloads = ConcurrentHashMap<Long, Job>()
    private val _downloadProgress = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<Long, DownloadProgress>> = _downloadProgress
    
    companion object {
        private const val TAG = "DownloadManager"
        private const val MAX_PARALLEL_DOWNLOADS = 3
        
        @Volatile
        private var instance: DownloadManager? = null
        
        fun getInstance(context: Context): DownloadManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    fun startDownload(item: DownloadItem) {
        if (activeDownloads.size >= MAX_PARALLEL_DOWNLOADS) {
            scope.launch {
                downloadDao.updateStatus(item.id, DownloadStatus.PENDING)
            }
            return
        }
        
        val job = scope.launch {
            performYtDlpDownload(item)
        }
        activeDownloads[item.id] = job
    }
    
    /**
     * Download using yt-dlp - handles FFmpeg merging automatically
     */
    private suspend fun performYtDlpDownload(item: DownloadItem) {
        try {
            downloadDao.updateStatus(item.id, DownloadStatus.DOWNLOADING)
            showDownloadNotification(item.id.toInt(), item.title, 0)
            
            val downloadDir = getDownloadDirectory()
            val outputTemplate = File(downloadDir, item.fileName).absolutePath
            
            // Update file path in database
            downloadDao.update(item.copy(filePath = outputTemplate))
            
            val sourceUrl = item.sourceUrl
            val formatId = item.downloadUrl
            
            // Validate we have a source URL
            if (sourceUrl.isEmpty()) {
                Log.e(TAG, "Source URL is empty! Cannot download.")
                downloadDao.updateStatus(item.id, DownloadStatus.FAILED)
                showFailedNotification(item.id.toInt(), item.title)
                return
            }
            
            Log.d(TAG, "Starting yt-dlp download:")
            Log.d(TAG, "  Source URL: $sourceUrl")
            Log.d(TAG, "  Format ID: $formatId")
            Log.d(TAG, "  Output: $outputTemplate")
            
            val result = when (item.streamType) {
                StreamType.AUDIO -> {
                    ytDlpExtractor.downloadAudio(sourceUrl, formatId, outputTemplate) { progress, eta, line ->
                        val percentage = progress.toInt().coerceIn(0, 100)
                        updateProgress(item.id, DownloadProgress(percentage.toLong(), 100L, 0L))
                        scope.launch { downloadDao.updateProgress(item.id, percentage.toLong()) }
                        if (percentage % 5 == 0) {
                            showDownloadNotification(item.id.toInt(), item.title, percentage)
                        }
                    }
                }
                else -> {
                    ytDlpExtractor.downloadVideo(sourceUrl, formatId, outputTemplate) { progress, eta, line ->
                        val percentage = progress.toInt().coerceIn(0, 100)
                        updateProgress(item.id, DownloadProgress(percentage.toLong(), 100L, 0L))
                        scope.launch { downloadDao.updateProgress(item.id, percentage.toLong()) }
                        if (percentage % 5 == 0) {
                            showDownloadNotification(item.id.toInt(), item.title, percentage)
                        }
                    }
                }
            }
            
            result.fold(
                onSuccess = { file ->
                    val fileSize = file.length()
                    downloadDao.updateProgressAndStatus(item.id, fileSize, DownloadStatus.COMPLETED)
                    downloadDao.update(item.copy(filePath = file.absolutePath, downloadedSize = fileSize, status = DownloadStatus.COMPLETED))
                    updateProgress(item.id, DownloadProgress(fileSize, fileSize, 0L))
                    
                    // Scan file to make it visible
                    scanFile(file)
                    
                    // Show completion notification
                    showCompletedNotification(item.id.toInt(), item.title, file.absolutePath)
                    Log.d(TAG, "Download completed: ${file.absolutePath}")
                },
                onFailure = { error ->
                    Log.e(TAG, "Download failed", error)
                    downloadDao.updateStatus(item.id, DownloadStatus.FAILED)
                    showFailedNotification(item.id.toInt(), item.title)
                }
            )
        } catch (e: CancellationException) {
            downloadDao.updateStatus(item.id, DownloadStatus.PAUSED)
            cancelNotification(item.id.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            downloadDao.updateStatus(item.id, DownloadStatus.FAILED)
            showFailedNotification(item.id.toInt(), item.title)
        } finally {
            activeDownloads.remove(item.id)
            checkPendingDownloads()
        }
    }
    
    private fun checkPendingDownloads() {
        if (activeDownloads.size < MAX_PARALLEL_DOWNLOADS) {
            scope.launch {
                // Use first() to get single value instead of collect which never terminates
                try {
                    downloadDao.getDownloadsByStatus(DownloadStatus.PENDING)
                        .first()
                        .firstOrNull()
                        ?.let { startDownload(it) }
                } catch (e: Exception) {
                    // Ignore - no pending downloads
                }
            }
        }
    }
    
    fun pauseDownload(id: Long) {
        activeDownloads[id]?.cancel()
        activeDownloads.remove(id)
        scope.launch {
            downloadDao.updateStatus(id, DownloadStatus.PAUSED)
        }
    }
    
    fun resumeDownload(item: DownloadItem) {
        startDownload(item)
    }
    
    fun cancelDownload(id: Long) {
        activeDownloads[id]?.cancel()
        activeDownloads.remove(id)
        scope.launch {
            downloadDao.getDownloadById(id)?.let { item ->
                val file = File(item.filePath)
                if (file.exists()) file.delete()
                downloadDao.delete(item)
            }
        }
    }
    
    private fun updateProgress(id: Long, progress: DownloadProgress) {
        val currentMap = _downloadProgress.value.toMutableMap()
        currentMap[id] = progress
        _downloadProgress.value = currentMap
    }
    
    private fun getDownloadDirectory(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "LarvelDownloader"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    private fun scanFile(file: File) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null
        ) { _, _ -> }
    }
    
    private fun isAudioFile(fileName: String): Boolean {
        val ext = fileName.lowercase()
        return ext.endsWith(".mp3") || ext.endsWith(".m4a") || ext.endsWith(".opus") || 
               ext.endsWith(".ogg") || ext.endsWith(".aac") || ext.endsWith(".webm")
    }
    
    suspend fun addDownload(item: DownloadItem): Long {
        return downloadDao.insert(item)
    }
    
    /**
     * Clean up stuck downloads on app start
     * Resets any downloads that were "DOWNLOADING" but app was killed
     */
    suspend fun cleanupStuckDownloads() {
        try {
            val stuckDownloads = downloadDao.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
            stuckDownloads.forEach { item ->
                // If not in active downloads, it was interrupted
                if (!activeDownloads.containsKey(item.id)) {
                    downloadDao.updateStatus(item.id, DownloadStatus.PAUSED)
                    cancelNotification(item.id.toInt())
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        activeDownloads.keys.forEach { id ->
            cancelNotification(id.toInt())
        }
    }
    
    private fun showDownloadNotification(id: Int, title: String, progress: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(context, LarvelApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Downloading... $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        
        notificationManager.notify(id, notification)
    }
    
    private fun showCompletedNotification(id: Int, title: String, filePath: String? = null) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_downloads", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, id, openIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(context, LarvelApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Download Complete ✓")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        // Add Open action button
        builder.addAction(
            android.R.drawable.ic_media_play,
            "Open",
            openPendingIntent
        )
        
        // Try to add direct file open action if we have the path
        if (filePath != null) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.provider", file
                    )
                    val mimeType = when {
                        filePath.endsWith(".mp4", true) || filePath.endsWith(".webm", true) -> "video/*"
                        filePath.endsWith(".mp3", true) || filePath.endsWith(".m4a", true) -> "audio/*"
                        else -> "*/*"
                    }
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val viewPendingIntent = PendingIntent.getActivity(
                        context, id + 10000, viewIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    builder.addAction(
                        android.R.drawable.ic_menu_view,
                        "Play",
                        viewPendingIntent
                    )
                }
            } catch (e: Exception) {
                // Ignore - just don't add the action
            }
        }
        
        notificationManager.notify(id, builder.build())
    }
    
    private fun showFailedNotification(id: Int, title: String) {
        val notification = NotificationCompat.Builder(context, LarvelApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(id, notification)
    }
    
    private fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }
}

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: Long
) {
    val percentage: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
}
