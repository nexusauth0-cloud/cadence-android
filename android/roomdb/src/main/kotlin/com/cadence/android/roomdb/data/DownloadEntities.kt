package com.cadence.android.roomdb.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.cadence.core.database.DownloadFailureReason
import com.cadence.core.database.DownloadId
import com.cadence.core.database.DownloadMetadata
import com.cadence.core.database.DownloadState
import kotlinx.coroutines.flow.Flow

/**
 * Room backing row for a download entry (maps to DownloadEntry).
 * Persists durable download state across process death / app restart (section 4/20).
 * The cached display columns follow the CACHE COLUMNS pattern (ADR-0003).
 */
@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["provider_id", "native_id"], unique = true),
        Index(value = ["state"]),
        Index(value = ["created_at"]),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    @ColumnInfo(name = "native_id")
    val nativeId: String,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "progress_bytes")
    val progressBytes: Long = 0L,
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long? = null,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long? = null,
    @ColumnInfo(name = "local_uri")
    val localUri: String? = null,
    @ColumnInfo(name = "error_reason")
    val errorReason: String? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: DownloadEntity): Long

    @Update
    suspend fun update(entry: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE provider_id = :providerId AND native_id = :nativeId LIMIT 1")
    suspend fun getByRef(providerId: String, nativeId: String): DownloadEntity?

    @Query("SELECT * FROM downloads ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observe(id: Long): Flow<DownloadEntity?>

    @Query("SELECT COUNT(*) FROM downloads WHERE state = :state")
    suspend fun countByState(state: String): Int

    @Query("SELECT * FROM downloads WHERE provider_id = :providerId AND native_id = :nativeId AND state != 'COMPLETED' LIMIT 1")
    suspend fun getActiveByRef(providerId: String, nativeId: String): DownloadEntity?

    @Query("SELECT COUNT(*) FROM downloads WHERE state = 'COMPLETED'")
    suspend fun completedCount(): Int

    @Query("SELECT * FROM downloads WHERE state = 'COMPLETED'")
    fun observeCompleted(): Flow<List<DownloadEntity>>

    @Query("SELECT SUM(file_size_bytes) FROM downloads WHERE state = 'COMPLETED' AND file_size_bytes IS NOT NULL")
    suspend fun completedTotalBytes(): Long?
}
