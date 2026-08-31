package com.cadence.android.roomdb

import android.content.Context
import com.cadence.android.roomdb.data.CadenceDatabase
import com.cadence.android.roomdb.data.HistoryEntity
import com.cadence.android.roomdb.data.LibraryDao
import com.cadence.android.roomdb.data.SavedTrackEntity
import com.cadence.core.database.LibraryRepository
import com.cadence.core.database.LibraryTrackMeta
import com.cadence.core.database.PlayHistoryEntry
import com.cadence.core.database.SavedTrack
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [LibraryRepository] (saved tracks + history).
 *
 * Display metadata is cached so the Library renders fully offline; saving never
 * implies offline availability (ADR-0007). This repository touches only local
 * Room data — no network, no auth/session material is ever written or logged.
 */
class RoomLibraryRepository private constructor(
    private val database: CadenceDatabase,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val clock: java.time.Clock,
) : LibraryRepository {

    private val dao: LibraryDao = database.libraryDao()

    companion object {
        fun open(context: Context): RoomLibraryRepository =
            RoomLibraryRepository(
                database = CadenceDatabase.create(context),
                dispatcher = kotlinx.coroutines.Dispatchers.IO,
                clock = java.time.Clock.systemUTC(),
            )

        internal fun forTesting(
            database: CadenceDatabase,
            dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
            clock: java.time.Clock = java.time.Clock.systemUTC(),
        ): RoomLibraryRepository = RoomLibraryRepository(database, dispatcher, clock)
    }

    override fun observeSaved(): Flow<List<SavedTrack>> =
        dao.observeSavedTracks().map { rows -> rows.map(::mapSaved) }

    override fun observeIsSaved(ref: TrackRef): Flow<Boolean> =
        dao.observeSavedCount(ref.providerId.value, ref.nativeId)
            .map { count -> count > 0 }

    override suspend fun save(ref: TrackRef, meta: LibraryTrackMeta) {
        withContext(dispatcher) {
            dao.insertSaved(
                SavedTrackEntity(
                    providerId = ref.providerId.value,
                    nativeId = ref.nativeId,
                    savedAt = clock.millis(),
                    title = meta.title,
                    artist = meta.artist,
                    durationMs = meta.durationMs,
                    artworkUrl = meta.artworkUrl,
                ),
            )
        }
    }

    override suspend fun unsave(ref: TrackRef): Boolean =
        withContext(dispatcher) { dao.deleteSaved(ref.providerId.value, ref.nativeId) > 0 }

    override suspend fun savedCount(): Int = withContext(dispatcher) { dao.savedCount() }

    override fun observeHistory(): Flow<List<PlayHistoryEntry>> =
        dao.observeHistory().map { rows -> rows.map(::mapHistory) }

    override suspend fun recordPlay(ref: TrackRef, meta: LibraryTrackMeta) {
        withContext(dispatcher) {
            dao.insertHistory(
                HistoryEntity(
                    providerId = ref.providerId.value,
                    nativeId = ref.nativeId,
                    playedAt = clock.millis(),
                    title = meta.title,
                    artist = meta.artist,
                    durationMs = meta.durationMs,
                    artworkUrl = meta.artworkUrl,
                ),
            )
        }
    }

    override suspend fun clearHistory(): Int = withContext(dispatcher) { dao.clearHistory() }

    override suspend fun historyCount(): Int = withContext(dispatcher) { dao.historyCount() }

    private fun mapSaved(e: SavedTrackEntity): SavedTrack =
        SavedTrack(
            ref = TrackRef(ProviderId(e.providerId), e.nativeId),
            savedAt = e.savedAt,
            cachedTitle = e.title,
            cachedArtist = e.artist,
            cachedDurationMs = e.durationMs,
            artworkUrl = e.artworkUrl,
        )

    private fun mapHistory(e: HistoryEntity): PlayHistoryEntry =
        PlayHistoryEntry(
            ref = TrackRef(ProviderId(e.providerId), e.nativeId),
            playedAt = e.playedAt,
            cachedTitle = e.title,
            cachedArtist = e.artist,
            cachedDurationMs = e.durationMs,
            artworkUrl = e.artworkUrl,
        )
}
