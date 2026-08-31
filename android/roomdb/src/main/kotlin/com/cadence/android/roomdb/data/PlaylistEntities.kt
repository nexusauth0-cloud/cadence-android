package com.cadence.android.roomdb.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room backing row for a playlist (maps to PlaylistMetadata). */
@Entity(
    tableName = "playlists",
    indices = [Index(value = ["updated_at"])],
)
data class PlaylistEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/** Room backing row for one track position within a playlist. */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "position"],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["provider_id", "native_id"]),
    ],
)
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    @ColumnInfo(name = "native_id")
    val nativeId: String,
    @ColumnInfo(name = "added_reason")
    val addedReason: String,
    @ColumnInfo(name = "title")
    val title: String? = null,
    @ColumnInfo(name = "artist")
    val artist: String? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String? = null,
) {
    /** Composite identity derived from providerId+nativeId for dedup checks. */
    fun refKey(): String = "$providerId\u0000$nativeId"
}