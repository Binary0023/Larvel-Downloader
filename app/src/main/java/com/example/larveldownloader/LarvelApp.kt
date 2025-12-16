package com.example.larveldownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.example.larveldownloader.extractor.DownloaderImpl
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe

class LarvelApp : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        const val DOWNLOAD_CHANNEL_ID = "download_channel"
        private const val TAG = "LarvelApp"
        
        var ytDlpInitialized = false
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize NewPipe Extractor
        NewPipe.init(DownloaderImpl.getInstance())
        
        // Initialize yt-dlp library (async to not block app start)
        applicationScope.launch {
            initYoutubeDL()
        }
        
        // Create notification channel
        createNotificationChannel()
    }
    
    private fun initYoutubeDL() {
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            ytDlpInitialized = true
            Log.d(TAG, "yt-dlp initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize yt-dlp", e)
            ytDlpInitialized = false
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
