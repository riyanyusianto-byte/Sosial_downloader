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

    @Query(
        "UPDATE downloads SET status = :status, bytesDownloaded = :bytesDownloaded, " +
            "totalBytes = :totalBytes, speedBytesPerSec = :speedBytesPerSec WHERE id = :id"
    )
    suspend fun updateProgress(
        id: Long,
        status: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        speedBytesPerSec: Long
    )

    @Query(
        "UPDATE downloads SET status = :status, localUri = :localUri, " +
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
        "UPDATE downloads SET status = :status, speedBytesPerSec = 0, " +
            "errorMessage = :errorMessage WHERE id = :id"
    )
    suspend fun markFailedOrCancelled(
        id: Long,
        status: String,
        errorMessage: String?
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearHistory()
}
