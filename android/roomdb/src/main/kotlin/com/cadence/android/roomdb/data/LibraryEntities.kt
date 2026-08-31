package com.cadence.android.roomdb.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room backing row for a saved/favorited track (maps to SavedTrack).
 * The cached display columns render the Library fully offline; they are
 * metadata caches, not identity — domain identity stays with TrackRef.
 */
@Entity(
    tableName = "saved_tracks",
    indices = [
        Index(value = ["provider_id", "native_id"], unique = true),
        Index(value = ["saved_at"]),
    ],
)
data class SavedTrackEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    @ColumnInfo(name = "native_id")
    val nativeId: String,
    @ColumnInfo(name = "saved_at")
    val savedAt: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String? = null,
)

/**
 * Room backing row for a history entry (maps to PlayHistoryEntry).
 * One row per genuine successful playback start.
 */
@Entity(
    tableName = "play_history",
    indices = [
        Index(value = ["played_at"]),
    ],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    @ColumnInfo(name = "native_id")
    val nativeId: String,
    @ColumnInfo(name = "played_at")
    val playedAt: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String? = null,
)
