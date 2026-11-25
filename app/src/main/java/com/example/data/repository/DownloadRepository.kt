package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.ExtractedMediaInfo
import com.example.data.model.VideoFormatOption
import com.example.data.remote.MediaDownloader
import com.example.data.remote.MediaExtractorService
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val extractorService: MediaExtractorService = MediaExtractorService(),
    private val mediaDownloader: MediaDownloader = MediaDownloader(context)
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    val downloadsFlow: Flow<List<DownloadEntity>> = downloadDao.observeAllDownloads()

    suspend fun analyzeLink(url: String): Result<ExtractedMediaInfo> {
        return extractorService.extractMedia(url)
    }

    suspend fun startDownload(
        mediaInfo: ExtractedMediaInfo,
        format: VideoFormatOption
    ): Long {
        val sanitizedTitle = mediaInfo.title
            .replace(Regex("""[\\/:*?"<>|\n\r]"""), " ")
            .trim()
            .take(45)
            .ifBlank { "LinkSave_${mediaInfo.platform.name.lowercase()}" }
        val fileName = "${sanitizedTitle}_${System.currentTimeMillis() % 10000}.${format.extension}"

        val entity = DownloadEntity(
            sourceUrl = mediaInfo.originalUrl,
            downloadUrl = format.downloadUrl,
            platform = mediaInfo.platform.name,
            title = mediaInfo.title,
            author = mediaInfo.author,
            qualityLabel = format.qualityBadge,
            mimeType = format.mimeType,
            fileName = fileName,
            thumbnailUrl = mediaInfo.thumbnailUrl,
            localUri = null,
            status = DownloadStatus.DOWNLOADING.name,
            bytesDownloaded = 0L,
            totalBytes = format.fileSizeBytes ?: -1L,
            durationSeconds = mediaInfo.durationSeconds,
            isAudioOnly = format.isAudioOnly,
            referer = format.referer,
            userAgent = format.userAgent
        )

        val rowId = downloadDao.insertDownload(entity)
        launchDownloadJob(rowId, format)
        return rowId
    }

    fun retryDownload(item: DownloadEntity) {
        repoScope.launch {
            downloadDao.updateProgress(
                id = item.id,
                status = DownloadStatus.DOWNLOADING.name,
                bytesDownloaded = 0L,
                totalBytes = item.totalBytes,
                speedBytesPerSec = 0L
            )
            val format = VideoFormatOption(
                id = "retry_${item.id}",
                label = item.qualityLabel,
                qualityBadge = item.qualityLabel,
                mimeType = item.mimeType,
                extension = item.fileName.substringAfterLast(".", "mp4"),
                downloadUrl = item.downloadUrl,
                fileSizeBytes = item.totalBytes.takeIf { it > 0 },
                isAudioOnly = item.isAudioOnly,
                referer = item.referer,
                userAgent = item.userAgent
            )
            launchDownloadJob(item.id, format, customFileName = item.fileName)
        }
    }

    private fun launchDownloadJob(
        rowId: Long,
        format: VideoFormatOption,
        customFileName: String? = null
    ) {
        activeJobs[rowId]?.cancel()
        val job = repoScope.launch {
            val entity = downloadDao.getById(rowId) ?: return@launch
            val fileName = customFileName ?: entity.fileName
            try {
                val result = mediaDownloader.downloadToDevice(
                    downloadUrl = format.downloadUrl,
                    fileName = fileName,
                    mimeType = format.mimeType,
                    isAudioOnly = format.isAudioOnly,
                    referer = format.referer,
                    userAgent = format.userAgent
                ) { bytesDownloaded, totalBytes, speed ->
                    downloadDao.updateProgress(
                        id = rowId,
                        status = DownloadStatus.DOWNLOADING.name,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        speedBytesPerSec = speed
                    )
                }

                downloadDao.markCompleted(
                    id = rowId,
                    status = DownloadStatus.COMPLETED.name,
                    localUri = result.uri.toString(),
                    bytesDownloaded = result.bytesWritten,
                    totalBytes = result.totalBytes
                )
            } catch (_: CancellationException) {
                downloadDao.markFailedOrCancelled(
                    id = rowId,
                    status = DownloadStatus.CANCELLED.name,
                    errorMessage = "Unduhan dibatalkan."
                )
            } catch (t: Throwable) {
                downloadDao.markFailedOrCancelled(
                    id = rowId,
                    status = DownloadStatus.FAILED.name,
                    errorMessage = t.localizedMessage ?: "Gagal mengunduh file media."
                )
            } finally {
                activeJobs.remove(rowId)
            }
        }
        activeJobs[rowId] = job
    }

    fun cancelDownload(id: Long) {
        activeJobs[id]?.cancel()
        repoScope.launch {
            downloadDao.markFailedOrCancelled(
                id = id,
                status = DownloadStatus.CANCELLED.name,
                errorMessage = "Unduhan dibatalkan."
            )
        }
    }

    suspend fun deleteDownload(item: DownloadEntity) {
        activeJobs[item.id]?.cancel()
        item.localUri?.let { uriStr ->
            runCatching {
                context.contentResolver.delete(Uri.parse(uriStr), null, null)
            }
        }
        downloadDao.deleteById(item.id)
    }

    suspend fun clearCompletedHistory() {
        downloadDao.clearHistory()
    }
}
