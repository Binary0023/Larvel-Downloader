package com.example.larveldownloader.extractor

import android.content.Context
import com.example.larveldownloader.model.VideoInfo
import com.example.larveldownloader.model.toAudioStreamInfo
import com.example.larveldownloader.model.toVideoStreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo

class VideoExtractor(private val context: Context? = null) {
    
    private val ytDlpExtractor: YtDlpExtractor? by lazy {
        context?.let { YtDlpExtractor(it) }
    }
    
    suspend fun extractVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        // Use yt-dlp for everything - it's faster and handles more sites
        // Including YouTube for better format support (MP4 instead of just webm)
        if (ytDlpExtractor != null && ytDlpExtractor!!.isInstalled()) {
            return@withContext ytDlpExtractor!!.extractVideoInfo(url)
        }
        
        // Fallback to NewPipe if yt-dlp not available
        try {
            val service = NewPipe.getServiceByUrl(url)
            val extractor: StreamExtractor = service.getStreamExtractor(url)
            extractor.fetchPage()
            
            val streamInfo = StreamInfo.getInfo(extractor)
            
            val videoStreams = streamInfo.videoStreams
                .filter { !it.isVideoOnly && it.content != null }
                .map { it.toVideoStreamInfo() }
                .distinctBy { "${it.resolution}_${it.format}" }
                .sortedByDescending { 
                    it.resolution.replace("p", "").toIntOrNull() ?: 0 
                }
            
            val audioStreams = streamInfo.audioStreams
                .filter { it.content != null }
                .map { it.toAudioStreamInfo() }
                .distinctBy { "${it.bitrate}_${it.format}" }
                .sortedByDescending { it.bitrate }
            
            val videoOnlyStreams = streamInfo.videoStreams
                .filter { it.isVideoOnly && it.content != null }
                .map { it.toVideoStreamInfo() }
                .distinctBy { "${it.resolution}_${it.format}" }
                .sortedByDescending { 
                    it.resolution.replace("p", "").toIntOrNull() ?: 0 
                }
            
            val videoInfo = VideoInfo(
                id = streamInfo.id,
                title = streamInfo.name,
                description = streamInfo.description?.content ?: "",
                uploaderName = streamInfo.uploaderName ?: "Unknown",
                uploaderUrl = streamInfo.uploaderUrl ?: "",
                thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url ?: "",
                duration = streamInfo.duration,
                viewCount = streamInfo.viewCount,
                uploadDate = streamInfo.uploadDate?.offsetDateTime()?.toString() ?: "Unknown",
                category = streamInfo.category ?: "Unknown",
                tags = streamInfo.tags ?: emptyList(),
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                videoOnlyStreams = videoOnlyStreams
            )
            
            Result.success(videoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun isValidUrl(url: String): Boolean {
        return true // yt-dlp supports almost everything
    }
    
    /**
     * Get the extractor type that will be used for this URL
     */
    fun getExtractorType(url: String): ExtractorType {
        return if (ytDlpExtractor?.isInstalled() == true) {
            ExtractorType.YT_DLP
        } else {
            ExtractorType.NEWPIPE
        }
    }
    
    enum class ExtractorType {
        NEWPIPE,
        YT_DLP
    }
}
