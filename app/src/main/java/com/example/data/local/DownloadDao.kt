package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadEntity): Long

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("UPDATE downloads SET partialFilePath = :partialFilePath WHERE id = :id")
    suspend fun updatePartialFilePath(id: Long, partialFilePath: String?)

    @Query(
        "UPDATE downloads SET status = :status, bytesDownloaded = :bytesDownloaded, " +
            "totalBytes = :totalBytes, speedBytesPerSec = :speedBytesPerSec, " +
            "errorMessage = NULL WHERE id = :id"
    )
    suspend fun updateProgress(
        id: Long,
        status: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        speedBytesPerSec: Long
    )

    @Query(
        "UPDATE downloads SET downloadUrl = :downloadUrl, mimeType = :mimeType, " +
            "qualityLabel = :qualityLabel, referer = :referer, userAgent = :userAgent, " +
            "errorMessage = NULL WHERE id = :id"
    )
    suspend fun updateResolvedSource(
        id: Long,
        downloadUrl: String,
        mimeType: String,
        qualityLabel: String,
        referer: String?,
        userAgent: String?
    )

    @Query(
        "UPDATE downloads SET status = :status, localUri = :localUri, partialFilePath = NULL, " +
            "bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes, " +
            "speedBytesPerSec = 0, errorMessage = NULL WHERE id = :id"
    )
    suspend fun markCompleted(
        id: Long,
        status: String,
        localUri: String,
        bytesDownloaded: Long,
        totalBytes: Long
    )

    @Query(
        "UPDATE downloads SET status = :status, bytesDownloaded = :bytesDownloaded, " +
            "totalBytes = :totalBytes, speedBytesPerSec = 0, " +
            "errorMessage = :errorMessage WHERE id = :id"
    )
    suspend fun markStopped(
        id: Long,
        status: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        errorMessage: String?
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearHistory()
}
