package com.example.data.model

enum class MediaPlatform(val displayName: String) {
    YOUTUBE("YouTube"),
    TIKTOK("TikTok"),
    INSTAGRAM("Instagram"),
    OTHER("Tautan Langsung");

    companion object {
        fun detectFromUrl(rawUrl: String): MediaPlatform {
            val lower = rawUrl.lowercase()
            return when {
                lower.contains("youtube.com") ||
                    lower.contains("youtu.be") ||
                    lower.contains("youtube-nocookie.com") -> YOUTUBE
                lower.contains("tiktok.com") ||
                    lower.contains("douyin.com") -> TIKTOK
                lower.contains("instagram.com") ||
                    lower.contains("instagr.am") -> INSTAGRAM
                else -> OTHER
            }
        }

        fun extractFirstUrl(text: String): String? {
            val regex = Regex("""https?://[^\s<>"'()]+""", RegexOption.IGNORE_CASE)
            return regex.find(text.trim())?.value?.trimEnd('.', ',', ';', '!', '?')
        }
    }
}

data class VideoFormatOption(
    val id: String,
    val label: String,
    val qualityBadge: String,
    val mimeType: String,
    val extension: String,
    val downloadUrl: String,
    val fileSizeBytes: Long? = null,
    val isAudioOnly: Boolean = false,
    val referer: String? = null,
    val userAgent: String? = null
)

data class ExtractedMediaInfo(
    val originalUrl: String,
    val platform: MediaPlatform,
    val title: String,
    val author: String,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val formats: List<VideoFormatOption>
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

sealed interface ExtractionUiState {
    data object Idle : ExtractionUiState
    data class Loading(val platform: MediaPlatform) : ExtractionUiState
    data class Success(val media: ExtractedMediaInfo) : ExtractionUiState
    data class Error(
        val message: String,
        val platform: MediaPlatform,
        val requiresYouTubeAuth: Boolean = false
    ) : ExtractionUiState
}
