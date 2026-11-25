package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaPlatform

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sourceUrl: String,
    val downloadUrl: String,
    val platform: String,
    val title: String,
    val author: String,
    val qualityLabel: String,
    val mimeType: String,
    val fileName: String,
    val thumbnailUrl: String?,
    val localUri: String?,
    val partialFilePath: String? = null,
    val status: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = 0L,
    val durationSeconds: Int? = null,
    val isAudioOnly: Boolean = false,
    val referer: String? = null,
    val userAgent: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun platformEnum(): MediaPlatform =
        runCatching { MediaPlatform.valueOf(platform) }.getOrDefault(MediaPlatform.OTHER)

    fun statusEnum(): DownloadStatus =
        runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED)
}
