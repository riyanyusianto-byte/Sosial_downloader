package com.example.data.remote

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MediaDownloader(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class DownloadOutput(
        val uri: Uri,
        val bytesWritten: Long,
        val totalBytes: Long
    )

    suspend fun downloadToDevice(
        downloadUrl: String,
        fileName: String,
        mimeType: String,
        isAudioOnly: Boolean,
        referer: String? = null,
        userAgent: String? = null,
        onProgress: suspend (bytesDownloaded: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit
    ): DownloadOutput = withContext(Dispatchers.IO) {
        val effectiveUa = userAgent?.takeIf { it.isNotBlank() }
            ?: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", effectiveUa)
        if (!referer.isNullOrBlank()) {
            requestBuilder.header("Referer", referer)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Gagal mengunduh media (HTTP ${response.code}).")
            }
            val body = response.body
                ?: throw IllegalStateException("Respons server kosong saat mengunduh.")
            val totalBytes = body.contentLength()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadViaMediaStore(
                    body = body,
                    fileName = fileName,
                    mimeType = mimeType,
                    isAudioOnly = isAudioOnly,
                    totalBytes = totalBytes,
                    onProgress = onProgress
                )
            } else {
                downloadViaAppExternalDir(
                    body = body,
                    fileName = fileName,
                    isAudioOnly = isAudioOnly,
                    totalBytes = totalBytes,
                    onProgress = onProgress
                )
            }
        }
    }

    private suspend fun downloadViaMediaStore(
        body: okhttp3.ResponseBody,
        fileName: String,
        mimeType: String,
        isAudioOnly: Boolean,
        totalBytes: Long,
        onProgress: suspend (Long, Long, Long) -> Unit
    ): DownloadOutput = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (isAudioOnly) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val relativeDir = if (isAudioOnly) {
            "${Environment.DIRECTORY_MUSIC}/LinkSave"
        } else {
            "${Environment.DIRECTORY_MOVIES}/LinkSave"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Tidak dapat membuat file di MediaStore.")

        try {
            var bytesCopied = 0L
            resolver.openOutputStream(itemUri)?.use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var lastReportTime = System.currentTimeMillis()
                    var lastReportBytes = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesCopied += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastReportTime
                        if (elapsed >= 180) {
                            val speed = ((bytesCopied - lastReportBytes) * 1000L) / elapsed.coerceAtLeast(1L)
                            onProgress(bytesCopied, totalBytes, speed)
                            lastReportTime = now
                            lastReportBytes = bytesCopied
                        }
                    }
                    output.flush()
                }
            } ?: throw IllegalStateException("Gagal membuka stream penyimpanan.")

            val finalValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(itemUri, finalValues, null, null)

            val resolvedTotal = if (totalBytes > 0) totalBytes else bytesCopied
            onProgress(bytesCopied, resolvedTotal, 0L)
            DownloadOutput(uri = itemUri, bytesWritten = bytesCopied, totalBytes = resolvedTotal)
        } catch (ce: CancellationException) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw ce
        } catch (t: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw t
        }
    }

    private suspend fun downloadViaAppExternalDir(
        body: okhttp3.ResponseBody,
        fileName: String,
        isAudioOnly: Boolean,
        totalBytes: Long,
        onProgress: suspend (Long, Long, Long) -> Unit
    ): DownloadOutput = withContext(Dispatchers.IO) {
        val baseDir = context.getExternalFilesDir(
            if (isAudioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        ) ?: File(context.filesDir, "media")
        val targetDir = File(baseDir, "LinkSave").apply { mkdirs() }
        val targetFile = File(targetDir, fileName)

        try {
            var bytesCopied = 0L
            FileOutputStream(targetFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var lastReportTime = System.currentTimeMillis()
                    var lastReportBytes = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesCopied += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastReportTime
                        if (elapsed >= 180) {
                            val speed = ((bytesCopied - lastReportBytes) * 1000L) / elapsed.coerceAtLeast(1L)
                            onProgress(bytesCopied, totalBytes, speed)
                            lastReportTime = now
                            lastReportBytes = bytesCopied
                        }
                    }
                    output.flush()
                }
            }
            val resolvedTotal = if (totalBytes > 0) totalBytes else bytesCopied
            onProgress(bytesCopied, resolvedTotal, 0L)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )
            DownloadOutput(uri = uri, bytesWritten = bytesCopied, totalBytes = resolvedTotal)
        } catch (ce: CancellationException) {
            runCatching { targetFile.delete() }
            throw ce
        } catch (t: Throwable) {
            runCatching { targetFile.delete() }
            throw t
        }
    }
}
