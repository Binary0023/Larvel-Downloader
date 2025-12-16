package com.example.larveldownloader.extractor

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.larveldownloader.LarvelApp
import com.example.larveldownloader.model.AudioStreamInfo
import com.example.larveldownloader.model.VideoInfo
import com.example.larveldownloader.model.VideoStreamInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * yt-dlp extractor using youtubedl-android library
 * Supports 1000+ sites with FFmpeg for merging video+audio
 */
class YtDlpExtractor(private val context: Context) {

    companion object {
        private const val TAG = "YtDlpExtractor"

        // Categories for display in About screen
        val SITE_CATEGORIES = mapOf(
            "Social Media" to listOf("Instagram", "Facebook", "TikTok", "Twitter/X", "Reddit", "Pinterest", "Snapchat", "LinkedIn", "Tumblr"),
            "Video Platforms" to listOf("YouTube", "Vimeo", "Dailymotion", "Twitch", "Bilibili", "Niconico", "Rutube", "VK", "Rumble", "BitChute", "Odysee"),
            "Music & Audio" to listOf("SoundCloud", "Mixcloud", "Bandcamp", "Audiomack"),
            "News & Media" to listOf("CNN", "BBC", "NY Times", "Washington Post", "The Guardian", "Reuters"),
            "Education" to listOf("TED", "Coursera", "Udemy", "Khan Academy"),
            "Other" to listOf("Imgur", "Gfycat", "Streamable", "Archive.org", "Google Drive", "Dropbox", "MEGA", "And 1000+ more...")
        )

        fun shouldUseYtDlp(url: String): Boolean = true // Use for everything
    }

    fun isInstalled(): Boolean = LarvelApp.ytDlpInitialized

    /**
     * Extract video info - optimized for speed
     */
    suspend fun extractVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            if (!isInstalled()) {
                return@withContext Result.failure(Exception("yt-dlp not initialized. Please restart the app."))
            }

            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--no-check-certificates")
            request.addOption("--socket-timeout", "10")

            val info = YoutubeDL.getInstance().getInfo(request)
            val formats = info.formats ?: emptyList()

            val videoStreams = mutableListOf<VideoStreamInfo>()
            val audioStreams = mutableListOf<AudioStreamInfo>()
            val videoOnlyStreams = mutableListOf<VideoStreamInfo>()

            // Process formats
            for (format in formats) {
                val formatUrl = format.url ?: continue
                val formatId = format.formatId ?: continue
                val ext = format.ext ?: "mp4"
                val filesize = format.fileSize ?: -1L
                val height = format.height ?: 0
                val formatNote = format.formatNote ?: ""
                val vcodec = format.vcodec ?: "none"
                val acodec = format.acodec ?: "none"

                val hasVideo = height > 0 || (vcodec != "none" && !vcodec.contains("none"))
                val hasAudio = acodec != "none" && !acodec.contains("none")

                when {
                    hasVideo && hasAudio -> {
                        val resolution = if (height > 0) "${height}p" else "Unknown"
                        videoStreams.add(
                            VideoStreamInfo(
                                url = formatId, // Store format ID for yt-dlp download
                                format = ext,
                                resolution = resolution,
                                quality = formatNote.ifEmpty { resolution },
                                codec = vcodec.takeIf { it != "none" } ?: "h264",
                                bitrate = 0,
                                isVideoOnly = false,
                                fileSize = filesize
                            )
                        )
                    }
                    hasVideo && !hasAudio -> {
                        val resolution = if (height > 0) "${height}p" else "Unknown"
                        videoOnlyStreams.add(
                            VideoStreamInfo(
                                url = formatId,
                                format = ext,
                                resolution = resolution,
                                quality = formatNote.ifEmpty { resolution },
                                codec = vcodec,
                                bitrate = 0,
                                isVideoOnly = true,
                                fileSize = filesize
                            )
                        )
                    }
                    !hasVideo && hasAudio -> {
                        val bitrate = format.tbr?.toInt() ?: format.abr?.toInt() ?: 128
                        audioStreams.add(
                            AudioStreamInfo(
                                url = formatId,
                                format = ext,
                                bitrate = bitrate,
                                codec = acodec,
                                fileSize = filesize
                            )
                        )
                    }
                }
            }

            // Add "Best" options that use FFmpeg to merge
            if (videoOnlyStreams.isNotEmpty() && audioStreams.isNotEmpty()) {
                // Add merged options (video+audio combined by FFmpeg)
                val bestVideo = videoOnlyStreams.maxByOrNull { 
                    it.resolution.replace("p", "").toIntOrNull() ?: 0 
                }
                if (bestVideo != null) {
                    // Add "Best Quality (Merged)" option
                    videoStreams.add(0, VideoStreamInfo(
                        url = "bestvideo+bestaudio/best",
                        format = "mp4",
                        resolution = bestVideo.resolution,
                        quality = "Best Quality (with audio)",
                        codec = "h264/aac",
                        bitrate = 0,
                        isVideoOnly = false,
                        fileSize = -1L
                    ))
                }
                
                // Add common quality options with merged audio
                listOf(1080, 720, 480, 360).forEach { targetHeight ->
                    val matchingVideo = videoOnlyStreams.find { 
                        it.resolution.replace("p", "").toIntOrNull() == targetHeight 
                    }
                    if (matchingVideo != null) {
                        videoStreams.add(VideoStreamInfo(
                            url = "bestvideo[height<=${targetHeight}]+bestaudio/best[height<=${targetHeight}]",
                            format = "mp4",
                            resolution = "${targetHeight}p",
                            quality = "${targetHeight}p (with audio)",
                            codec = "h264/aac",
                            bitrate = 0,
                            isVideoOnly = false,
                            fileSize = -1L
                        ))
                    }
                }
            }

            // If still no video+audio streams, move video-only to video streams
            if (videoStreams.isEmpty() && videoOnlyStreams.isNotEmpty()) {
                videoStreams.addAll(videoOnlyStreams.map { it.copy(isVideoOnly = false) })
                videoOnlyStreams.clear()
            }

            // Sort
            videoStreams.sortByDescending { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
            videoOnlyStreams.sortByDescending { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
            audioStreams.sortByDescending { it.bitrate }

            val videoInfo = VideoInfo(
                id = info.id ?: "",
                title = info.title ?: "Unknown",
                description = info.description ?: "",
                uploaderName = info.uploader ?: "Unknown",
                uploaderUrl = "",
                thumbnailUrl = info.thumbnail ?: "",
                duration = info.duration.toLong(),
                viewCount = info.viewCount?.toLongOrNull() ?: 0L,
                uploadDate = info.uploadDate ?: "Unknown",
                category = "",
                tags = emptyList(),
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                videoOnlyStreams = videoOnlyStreams
            )

            Result.success(videoInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract video info", e)
            Result.failure(Exception("Failed to extract: ${e.message}"))
        }
    }

    /**
     * Download video using yt-dlp with FFmpeg merging
     * This ensures video has audio!
     */
    suspend fun downloadVideo(
        url: String,
        formatId: String,
        outputPath: String,
        onProgress: (Float, Long, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!isInstalled()) {
                return@withContext Result.failure(Exception("yt-dlp not initialized"))
            }

            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("-o", outputPath)
            
            // Use format selector - this triggers FFmpeg merge for video+audio
            if (formatId.contains("+") || formatId.startsWith("best")) {
                request.addOption("-f", formatId)
                // Merge to mp4 container
                request.addOption("--merge-output-format", "mp4")
                // Recode if needed for compatibility
                request.addOption("--recode-video", "mp4")
            } else {
                request.addOption("-f", formatId)
            }

            // Execute download
            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                onProgress(progress, etaInSeconds, line ?: "")
            }

            val outputFile = File(outputPath)
            if (outputFile.exists()) {
                Result.success(outputFile)
            } else {
                // Check for mp4 version (FFmpeg might have changed extension)
                val mp4File = File(outputPath.replace(Regex("\\.[^.]+$"), ".mp4"))
                if (mp4File.exists()) {
                    Result.success(mp4File)
                } else {
                    Result.failure(Exception("Download completed but file not found"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }

    /**
     * Download audio only
     */
    suspend fun downloadAudio(
        url: String,
        formatId: String,
        outputPath: String,
        onProgress: (Float, Long, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!isInstalled()) {
                return@withContext Result.failure(Exception("yt-dlp not initialized"))
            }

            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("-o", outputPath)
            request.addOption("-f", formatId)
            request.addOption("-x") // Extract audio
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0") // Best quality

            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                onProgress(progress, etaInSeconds, line ?: "")
            }

            // Find the output file (might be .mp3)
            val dir = File(outputPath).parentFile
            val baseName = File(outputPath).nameWithoutExtension
            val mp3File = File(dir, "$baseName.mp3")
            
            when {
                mp3File.exists() -> Result.success(mp3File)
                File(outputPath).exists() -> Result.success(File(outputPath))
                else -> Result.failure(Exception("Download completed but file not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio download failed", e)
            Result.failure(e)
        }
    }

    suspend fun update(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                context,
                YoutubeDL.UpdateChannel.STABLE
            )
            Result.success(status?.name ?: "Updated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update yt-dlp", e)
            Result.failure(e)
        }
    }

    suspend fun getVersion(): String = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().version(context) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun getDownloadDirectory(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "LarvelDownloader"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
