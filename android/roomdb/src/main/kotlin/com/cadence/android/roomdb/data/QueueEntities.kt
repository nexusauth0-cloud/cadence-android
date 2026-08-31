package com.cadence.android.roomdb.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room backing row for the durable queue session (ADR-0003). A singleton
 * (id=1): there is exactly one active queue per process. `kind`,
 * `continuation_serialized` and `current_index` capture the durable *intent*
 * (e.g. that a run came from a playlist) so restore reconstructs behaviour
 * rather than downgrading to a static list.
 */
@Entity(tableName = "queue_session")
data class QueueSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long = 1L,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "continuation_serialized")
    val continuationSerialized: String? = null,
    @ColumnInfo(name = "current_index")
    val currentIndex: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Room backing row for one queue item (ADR-0003). Cached display columns let
 * the queue render offline without re-resolution. The (session_id, position)
 * pair is the natural key; reorder replaces rows transactionally.
 *
 * `added_reason` distinguishes user rows from a suggested tail so "clear
 * suggested" is a single indexed delete (ADR-0003 rule 6).
 */
@Entity(
    tableName = "queue_item",
    primaryKeys = ["session_id", "position"],
)
data class QueueItemEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: Long = 1L,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    @ColumnInfo(name = "native_id")
    val nativeId: String,
    @ColumnInfo(name = "title")
    val title: String? = null,
    @ColumnInfo(name = "artist")
    val artist: String? = null,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String? = null,
    @ColumnInfo(name = "added_reason")
    val addedReason: String = "SEED",
)

@Dao
interface QueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: QueueSessionEntity)

    @Query("SELECT * FROM queue_session WHERE id = 1")
    suspend fun getSession(): QueueSessionEntity?

    @Query("SELECT * FROM queue_item WHERE session_id = 1 ORDER BY position ASC")
    suspend fun items(): List<QueueItemEntity>

    @Query("DELETE FROM queue_item WHERE session_id = 1")
    suspend fun deleteItems()

    @Query("DELETE FROM queue_session WHERE id = 1")
    suspend fun deleteSession()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<QueueItemEntity>)

    @Transaction
    suspend fun replaceAll(session: QueueSessionEntity, items: List<QueueItemEntity>) {
        deleteItems()
        deleteSession()
        upsertSession(session)
        insertItems(items)
    }
}
