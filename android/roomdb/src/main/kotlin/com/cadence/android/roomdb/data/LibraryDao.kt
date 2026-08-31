package com.cadence.android.roomdb.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LibraryDao {

    @Query("SELECT * FROM saved_tracks ORDER BY saved_at DESC")
    fun observeSavedTracks(): kotlinx.coroutines.flow.Flow<List<SavedTrackEntity>>

    @Query(
        "SELECT COUNT(*) FROM saved_tracks WHERE provider_id = :providerId AND native_id = :nativeId",
    )
    fun observeSavedCount(providerId: String, nativeId: String): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_tracks")
    suspend fun savedCount(): Int

    @Query(
        "SELECT COUNT(*) FROM saved_tracks WHERE provider_id = :providerId AND native_id = :nativeId",
    )
    suspend fun isSaved(providerId: String, nativeId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSaved(track: SavedTrackEntity): Long

    @Query(
        "DELETE FROM saved_tracks WHERE provider_id = :providerId AND native_id = :nativeId",
    )
    suspend fun deleteSaved(providerId: String, nativeId: String): Int

    @Query("SELECT * FROM play_history ORDER BY played_at DESC")
    fun observeHistory(): kotlinx.coroutines.flow.Flow<List<HistoryEntity>>

    @Query("SELECT COUNT(*) FROM play_history")
    suspend fun historyCount(): Int

    @Insert
    suspend fun insertHistory(entity: HistoryEntity): Long

    @Query("DELETE FROM play_history")
    suspend fun clearHistory(): Int
}
