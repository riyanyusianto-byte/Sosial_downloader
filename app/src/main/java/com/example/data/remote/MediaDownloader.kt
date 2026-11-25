package com.example.data.remote

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ResumableHttpException(val code: Int, message: String) : IllegalStateException(message)

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

    fun createPartialFile(rowId: Long, fileName: String): File {
        val dir = File(context.filesDir, "partial_downloads").apply { mkdirs() }
        val safeName = fileName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        return File(dir, "${rowId}_$safeName.part")
    }

    suspend fun downloadResumableToDevice(
        downloadUrl: String,
        partialFile: File,
        fileName: String,
        mimeType: String,
        isAudioOnly: Boolean,
        referer: String? = null,
        userAgent: String? = null,
        onProgress: suspend (bytesDownloaded: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit
    ): DownloadOutput = withContext(Dispatchers.IO) {
        partialFile.parentFile?.mkdirs()
        val existingBytes = if (partialFile.exists()) partialFile.length() else 0L

        val effectiveUa = userAgent?.takeIf { it.isNotBlank() }
            ?: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", effectiveUa)
        if (!referer.isNullOrBlank()) {
            requestBuilder.header("Referer", referer)
        }
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val code = response.code

            if (code == 416) {
                val rangeTotal = parseTotalFromContentRange(response.header("Content-Range"))
                if (existingBytes > 0L && (rangeTotal == null || existingBytes >= rangeTotal)) {
                    val uri = exportCompletedPartial(partialFile, fileName, mimeType, isAudioOnly)
                    val total = rangeTotal ?: existingBytes
                    onProgress(existingBytes, total, 0L)
                    return@withContext DownloadOutput(uri = uri, bytesWritten = existingBytes, totalBytes = total)
                } else {
                    // Range mismatch: reset partial and throw so caller/retry handles cleanly
                    partialFile.delete()
                    throw ResumableHttpException(code, "Rentang unduhan tidak valid, mencoba ulang dari awal.")
                }
            }

            if (!response.isSuccessful) {
                throw ResumableHttpException(code, "Gagal mengunduh media (HTTP $code).")
            }

            val body = response.body
                ?: throw IllegalStateException("Respons server kosong saat mengunduh.")

            val isPartial206 = (code == 206 && existingBytes > 0L)
            val contentLength = body.contentLength()
            val resolvedTotal = if (isPartial206) {
                parseTotalFromContentRange(response.header("Content-Range"))
                    ?: if (contentLength > 0L) existingBytes + contentLength else -1L
            } else {
                contentLength
            }

            var bytesCopied = if (isPartial206) existingBytes else 0L
            FileOutputStream(partialFile, isPartial206).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var lastReportTime = System.currentTimeMillis()
                    var lastReportBytes = bytesCopied
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
                            onProgress(bytesCopied, resolvedTotal, speed)
                            lastReportTime = now
                            lastReportBytes = bytesCopied
                        }
                    }
                    output.flush()
                }
            }

            val finalTotal = if (resolvedTotal > 0L) resolvedTotal else bytesCopied
            val finalUri = exportCompletedPartial(partialFile, fileName, mimeType, isAudioOnly)
            onProgress(bytesCopied, finalTotal, 0L)
            DownloadOutput(uri = finalUri, bytesWritten = bytesCopied, totalBytes = finalTotal)
        }
    }

    private fun parseTotalFromContentRange(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        // Format: "bytes 100-999/1000" or "bytes */1000"
        val slashPart = contentRange.substringAfter("/", "").trim()
        return slashPart.toLongOrNull()?.takeIf { it > 0L }
    }

    private fun exportCompletedPartial(
        partialFile: File,
        fileName: String,
        mimeType: String,
        isAudioOnly: Boolean
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToMediaStore(partialFile, fileName, mimeType, isAudioOnly)
        } else {
            exportToExternalDir(partialFile, fileName, isAudioOnly)
        }
    }

    private fun exportToMediaStore(
        partialFile: File,
        fileName: String,
        mimeType: String,
        isAudioOnly: Boolean
    ): Uri {
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
            resolver.openOutputStream(itemUri)?.use { output ->
                FileInputStream(partialFile).use { input ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                    output.flush()
                }
            } ?: throw IllegalStateException("Gagal membuka stream penyimpanan.")

            val finalValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(itemUri, finalValues, null, null)
            partialFile.delete()
            return itemUri
        } catch (t: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw t
        }
    }

    private fun exportToExternalDir(
        partialFile: File,
        fileName: String,
        isAudioOnly: Boolean
    ): Uri {
        val baseDir = context.getExternalFilesDir(
            if (isAudioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        ) ?: File(context.filesDir, "media")
        val targetDir = File(baseDir, "LinkSave").apply { mkdirs() }
        val targetFile = File(targetDir, fileName)

        FileInputStream(partialFile).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output, bufferSize = 32 * 1024)
                output.flush()
            }
        }
        partialFile.delete()
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )
    }
}
