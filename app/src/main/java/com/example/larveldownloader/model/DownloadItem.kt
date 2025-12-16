package com.example.larveldownloader.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val downloadUrl: String, // Format ID for yt-dlp or direct URL
    val sourceUrl: String = "", // Original video page URL for yt-dlp
    val fileName: String,
    val filePath: String,
    val totalSize: Long,
    val downloadedSize: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val streamType: StreamType,
    val quality: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class StreamType {
    VIDEO,
    AUDIO,
    VIDEO_ONLY
}
