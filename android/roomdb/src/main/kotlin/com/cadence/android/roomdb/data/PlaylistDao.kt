package com.cadence.android.roomdb.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY updated_at DESC")
    suspend fun listPlaylists(): List<PlaylistEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query(
        "UPDATE playlists SET title = :title, description = :description, " +
            "updated_at = :updatedAt WHERE id = :id",
    )
    suspend fun updatePlaylist(id: String, title: String, description: String, updatedAt: Long): Int

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun countTracks(playlistId: String): Int

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position ASC")
    suspend fun listTracks(playlistId: String): List<PlaylistTrackEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun lastPosition(playlistId: String): Int

    @Insert
    suspend fun insertTrack(track: PlaylistTrackEntity): Long

    @Insert
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>): LongArray

    @Query(
        "DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND " +
            "provider_id = :providerId AND native_id = :nativeId",
    )
    suspend fun deleteTrackByRef(playlistId: String, providerId: String, nativeId: String): Int

    @Query(
        "DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND " +
            "position IN (:positions)",
    )
    suspend fun deleteTracksAtPositions(playlistId: String, positions: List<Int>): Int

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun deleteAllTracks(playlistId: String): Int

    /**
     * Which of the given positions actually exist in the playlist.
     * Bounded `IN` query — callers MUST route through `inBatches` (ADR-0005)
     * because [positions] may exceed the 999 SQLite variable ceiling.
     */
    @Query(
        "SELECT position FROM playlist_tracks WHERE playlist_id = :playlistId AND position IN (:positions)",
    )
    suspend fun positionsIn(playlistId: String, positions: List<Int>): List<Int>

    /** All positions+refs for the duplicate check. Returns empty when none. */
    @Query(
        "SELECT position, provider_id AS providerId, native_id AS nativeId " +
            "FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position ASC",
    )
    suspend fun allTrackPositions(playlistId: String): List<PlaylistTrackPositionRow>

    @Transaction
    suspend fun replaceTrackList(playlistId: String, tracks: List<PlaylistTrackEntity>) {
        deleteAllTracks(playlistId)
        insertTracks(tracks)
    }
}

/** Position+ref row for duplicate-position lookups (ADR-0005). */
data class PlaylistTrackPositionRow(
    val position: Int,
    val providerId: String,
    val nativeId: String,
)