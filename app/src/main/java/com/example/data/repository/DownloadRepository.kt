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
import com.example.data.remote.ResumableHttpException
import java.io.File
import java.net.UnknownHostException
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
    private val pauseRequested = ConcurrentHashMap<Long, Boolean>()

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
            partialFilePath = null,
            status = DownloadStatus.DOWNLOADING.name,
            bytesDownloaded = 0L,
            totalBytes = format.fileSizeBytes ?: -1L,
            durationSeconds = mediaInfo.durationSeconds,
            isAudioOnly = format.isAudioOnly,
            referer = format.referer,
            userAgent = format.userAgent
        )

        val rowId = downloadDao.insertDownload(entity)
        val partialFile = mediaDownloader.createPartialFile(rowId, fileName)
        downloadDao.updatePartialFilePath(rowId, partialFile.absolutePath)

        launchDownloadJob(rowId = rowId, format = format, partialFile = partialFile)
        return rowId
    }

    fun pauseDownload(id: Long) {
        val job = activeJobs[id]
        if (job != null && job.isActive) {
            pauseRequested[id] = true
            job.cancel()
        } else {
            repoScope.launch {
                val item = downloadDao.getById(id) ?: return@launch
                val saved = item.partialFilePath?.let { File(it).takeIf { f -> f.exists() }?.length() }
                    ?: item.bytesDownloaded
                downloadDao.markStopped(
                    id = id,
                    status = DownloadStatus.PAUSED.name,
                    bytesDownloaded = saved,
                    totalBytes = item.totalBytes,
                    errorMessage = null
                )
            }
        }
    }

    fun cancelDownload(id: Long) {
        val job = activeJobs[id]
        if (job != null && job.isActive) {
            pauseRequested[id] = false
            job.cancel()
        } else {
            repoScope.launch {
                val item = downloadDao.getById(id) ?: return@launch
                item.partialFilePath?.let { runCatching { File(it).delete() } }
                downloadDao.markStopped(
                    id = id,
                    status = DownloadStatus.CANCELLED.name,
                    bytesDownloaded = 0L,
                    totalBytes = item.totalBytes,
                    errorMessage = "Unduhan dibatalkan."
                )
            }
        }
    }

    fun resumeOrRetryDownload(item: DownloadEntity) {
        repoScope.launch {
            val partialFile = item.partialFilePath
                ?.let { File(it) }
                ?: mediaDownloader.createPartialFile(item.id, item.fileName)
            if (item.partialFilePath == null) {
                downloadDao.updatePartialFilePath(item.id, partialFile.absolutePath)
            }

            val savedBytes = if (partialFile.exists()) partialFile.length() else 0L
            downloadDao.updateProgress(
                id = item.id,
                status = DownloadStatus.DOWNLOADING.name,
                bytesDownloaded = savedBytes,
                totalBytes = item.totalBytes,
                speedBytesPerSec = 0L
            )

            var activeFormat = VideoFormatOption(
                id = "resume_${item.id}",
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

            // If the saved URL host is unresolvable (e.g. dead proxy host) or previously failed with 403/410,
            // refresh the stream URL from sourceUrl automatically while keeping savedBytes intact.
            val shouldRefreshSource = !extractorService.isHostResolvable(item.downloadUrl) ||
                item.errorMessage?.contains("HTTP 403", ignoreCase = true) == true ||
                item.errorMessage?.contains("HTTP 410", ignoreCase = true) == true ||
                item.errorMessage?.contains("Unable to resolve host", ignoreCase = true) == true

            if (shouldRefreshSource) {
                val refreshed = refreshFormatFromSource(item)
                if (refreshed != null) {
                    activeFormat = refreshed
                    downloadDao.updateResolvedSource(
                        id = item.id,
                        downloadUrl = refreshed.downloadUrl,
                        mimeType = refreshed.mimeType,
                        qualityLabel = refreshed.qualityBadge,
                        referer = refreshed.referer,
                        userAgent = refreshed.userAgent
                    )
                }
            }

            launchDownloadJob(rowId = item.id, format = activeFormat, partialFile = partialFile)
        }
    }

    private suspend fun refreshFormatFromSource(item: DownloadEntity): VideoFormatOption? {
        val extracted = extractorService.extractMedia(item.sourceUrl).getOrNull() ?: return null
        val formats = extracted.formats
        if (formats.isEmpty()) return null
        return formats.firstOrNull {
            it.qualityBadge.equals(item.qualityLabel, ignoreCase = true) &&
                it.isAudioOnly == item.isAudioOnly
        } ?: formats.firstOrNull { it.isAudioOnly == item.isAudioOnly }
            ?: formats.firstOrNull()
    }

    private fun launchDownloadJob(
        rowId: Long,
        format: VideoFormatOption,
        partialFile: File
    ) {
        pauseRequested.remove(rowId)
        activeJobs[rowId]?.cancel()
        val job = repoScope.launch {
            val entity = downloadDao.getById(rowId) ?: return@launch
            var latestBytes = if (partialFile.exists()) partialFile.length() else entity.bytesDownloaded
            var latestTotal = entity.totalBytes

            try {
                val result = runDownloadWithAutoRefresh(
                    rowId = rowId,
                    entity = entity,
                    initialFormat = format,
                    partialFile = partialFile
                ) { bytesDownloaded, totalBytes, speed ->
                    latestBytes = bytesDownloaded
                    if (totalBytes > 0L) latestTotal = totalBytes
                    downloadDao.updateProgress(
                        id = rowId,
                        status = DownloadStatus.DOWNLOADING.name,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = latestTotal,
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
                val isPause = pauseRequested.remove(rowId) == true
                val currentSaved = if (partialFile.exists()) partialFile.length() else latestBytes
                if (!isPause) {
                    runCatching { partialFile.delete() }
                }
                downloadDao.markStopped(
                    id = rowId,
                    status = if (isPause) DownloadStatus.PAUSED.name else DownloadStatus.CANCELLED.name,
                    bytesDownloaded = if (isPause) currentSaved else 0L,
                    totalBytes = latestTotal,
                    errorMessage = if (isPause) null else "Unduhan dibatalkan."
                )
            } catch (t: Throwable) {
                val currentSaved = if (partialFile.exists()) partialFile.length() else latestBytes
                val friendlyError = when {
                    t is UnknownHostException ->
                        "Koneksi terputus atau server tidak ditemukan. Ketuk Lanjutkan saat jaringan kembali stabil."
                    else -> t.localizedMessage ?: "Gagal mengunduh media. Ketuk Lanjutkan untuk meneruskan."
                }
                downloadDao.markStopped(
                    id = rowId,
                    status = DownloadStatus.FAILED.name,
                    bytesDownloaded = currentSaved,
                    totalBytes = latestTotal,
                    errorMessage = friendlyError
                )
            } finally {
                activeJobs.remove(rowId)
                pauseRequested.remove(rowId)
            }
        }
        activeJobs[rowId] = job
    }

    private suspend fun runDownloadWithAutoRefresh(
        rowId: Long,
        entity: DownloadEntity,
        initialFormat: VideoFormatOption,
        partialFile: File,
        onProgress: suspend (Long, Long, Long) -> Unit
    ): MediaDownloader.DownloadOutput {
        return try {
            mediaDownloader.downloadResumableToDevice(
                downloadUrl = initialFormat.downloadUrl,
                partialFile = partialFile,
                fileName = entity.fileName,
                mimeType = initialFormat.mimeType,
                isAudioOnly = initialFormat.isAudioOnly,
                referer = initialFormat.referer,
                userAgent = initialFormat.userAgent,
                onProgress = onProgress
            )
        } catch (err: Throwable) {
            val shouldRefresh = (err is ResumableHttpException && err.code in listOf(403, 404, 410, 416)) ||
                err is UnknownHostException
            if (!shouldRefresh) throw err

            val refreshed = refreshFormatFromSource(entity) ?: throw err
            downloadDao.updateResolvedSource(
                id = rowId,
                downloadUrl = refreshed.downloadUrl,
                mimeType = refreshed.mimeType,
                qualityLabel = refreshed.qualityBadge,
                referer = refreshed.referer,
                userAgent = refreshed.userAgent
            )
            mediaDownloader.downloadResumableToDevice(
                downloadUrl = refreshed.downloadUrl,
                partialFile = partialFile,
                fileName = entity.fileName,
                mimeType = refreshed.mimeType,
                isAudioOnly = refreshed.isAudioOnly,
                referer = refreshed.referer,
                userAgent = refreshed.userAgent,
                onProgress = onProgress
            )
        }
    }

    suspend fun deleteDownload(item: DownloadEntity) {
        pauseRequested.remove(item.id)
        activeJobs[item.id]?.cancel()
        item.partialFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
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
