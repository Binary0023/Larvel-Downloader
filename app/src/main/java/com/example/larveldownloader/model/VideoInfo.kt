package com.example.larveldownloader.model

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

data class VideoInfo(
    val id: String,
    val title: String,
    val description: String,
    val uploaderName: String,
    val uploaderUrl: String,
    val thumbnailUrl: String,
    val duration: Long,
    val viewCount: Long,
    val uploadDate: String,
    val category: String,
    val tags: List<String>,
    val videoStreams: List<VideoStreamInfo>,
    val audioStreams: List<AudioStreamInfo>,
    val videoOnlyStreams: List<VideoStreamInfo>
)

data class VideoStreamInfo(
    val url: String,
    val format: String,
    val resolution: String,
    val quality: String,
    val codec: String?,
    val bitrate: Int,
    val isVideoOnly: Boolean,
    val fileSize: Long
) {
    val displayName: String
        get() = if (isVideoOnly) {
            "$resolution ($format) - Video Only"
        } else {
            "$resolution ($format)"
        }
}

data class AudioStreamInfo(
    val url: String,
    val format: String,
    val bitrate: Int,
    val codec: String?,
    val fileSize: Long
) {
    val displayName: String
        get() = "${bitrate}kbps ($format)"
}

fun VideoStream.toVideoStreamInfo(): VideoStreamInfo {
    return VideoStreamInfo(
        url = content ?: "",
        format = format?.name ?: "Unknown",
        resolution = resolution ?: "Unknown",
        quality = quality ?: "Unknown",
        codec = codec,
        bitrate = bitrate,
        isVideoOnly = isVideoOnly,
        fileSize = itagItem?.contentLength ?: -1L
    )
}

fun AudioStream.toAudioStreamInfo(): AudioStreamInfo {
    return AudioStreamInfo(
        url = content ?: "",
        format = format?.name ?: "Unknown",
        bitrate = averageBitrate,
        codec = codec,
        fileSize = itagItem?.contentLength ?: -1L
    )
}
