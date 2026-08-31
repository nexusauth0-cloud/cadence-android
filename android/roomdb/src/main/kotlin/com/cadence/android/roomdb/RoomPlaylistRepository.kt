package com.cadence.android.roomdb

import android.content.Context
import androidx.room.withTransaction
import com.cadence.android.roomdb.data.CadenceDatabase
import com.cadence.android.roomdb.data.PlaylistDao
import com.cadence.android.roomdb.data.PlaylistEntity
import com.cadence.android.roomdb.data.PlaylistTrackEntity
import com.cadence.core.common.inBatches
import com.cadence.core.database.AddedReason
import com.cadence.core.database.DuplicateCheckResult
import com.cadence.core.database.PlaylistId
import com.cadence.core.database.LibraryTrackMeta
import com.cadence.core.database.PlaylistMetadata
import com.cadence.core.database.PlaylistRepository
import com.cadence.core.database.PlaylistTrack
import com.cadence.core.model.TrackRef
import java.util.UUID
import kotlinx.coroutines.withContext

/**
 * Room-backed [PlaylistRepository] (ADR-0003).
 *
 * All collection-parameter DAO calls route through [inBatches] (ADR-0005).
 * The DAO itself contains no `IN (:ids)` clauses — duplicate checking loads a
 * playlist's full position map once (bounded by playlist size) instead of
 * variable-limit IN queries (ADR-0005 §"prefer set-based alternatives").
 */
class RoomPlaylistRepository private constructor(
    private val database: CadenceDatabase,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val clock: java.time.Clock,
) : PlaylistRepository {

    private val dao: PlaylistDao = database.playlistDao()

    companion object {

        /** Open (or create) the on-disk database and back a repository with it. */
        fun open(context: Context): RoomPlaylistRepository =
            RoomPlaylistRepository(
                database = CadenceDatabase.create(context),
                dispatcher = kotlinx.coroutines.Dispatchers.IO,
                clock = java.time.Clock.systemUTC(),
            )

        /** Test/advanced constructor: explicit database handle. */
        internal fun forTesting(
            database: CadenceDatabase,
            dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
            clock: java.time.Clock = java.time.Clock.systemUTC(),
        ): RoomPlaylistRepository = RoomPlaylistRepository(database, dispatcher, clock)
    }

    override suspend fun create(title: String, description: String): PlaylistId =
        withContext(dispatcher) {
            val id = PlaylistId(UUID.randomUUID().toString().take(4))
            val now = clock.millis()
            dao.insertPlaylist(
                PlaylistEntity(
                    id = id.value,
                    title = title,
                    description = description,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            id
        }

    override suspend fun getMetadata(id: PlaylistId): PlaylistMetadata? =
        withContext(dispatcher) {
            val entity = dao.getPlaylist(id.value) ?: return@withContext null
            mapMetadata(entity, dao.countTracks(id.value))
        }

    override suspend fun updateMetadata(id: PlaylistId, title: String, description: String) {
        withContext(dispatcher) {
            val affected = dao.updatePlaylist(id.value, title, description, clock.millis())
            if (affected == 0) throw IllegalArgumentException("playlist $id not found")
        }
    }

    override suspend fun delete(id: PlaylistId): Boolean =
        withContext(dispatcher) {
            database.withTransaction { dao.deletePlaylist(id.value) > 0 }
        }

    override suspend fun getTracks(id: PlaylistId): List<PlaylistTrack> =
        withContext(dispatcher) {
            database.withTransaction {
                requirePlaylistExists(dao, id)
                dao.listTracks(id.value).map { mapTrack(it) }
            }
        }

    override suspend fun addTrack(id: PlaylistId, ref: TrackRef, reason: AddedReason) {
        addTracks(id, listOf(ref), reason)
    }

    override suspend fun addTracks(id: PlaylistId, refs: List<TrackRef>, reason: AddedReason) {
        addTracksDetailed(id, refs, emptyMap(), reason)
    }

    override suspend fun addTracksDetailed(
        id: PlaylistId,
        refs: List<TrackRef>,
        details: Map<TrackRef, LibraryTrackMeta>,
        reason: AddedReason,
    ) {
        withContext(dispatcher) {
            database.withTransaction {
                val playlist = dao.getPlaylist(id.value)
                    ?: throw IllegalArgumentException("playlist $id not found")
                var nextPosition = dao.lastPosition(id.value) + 1
                refs.chunked(com.cadence.core.common.BatchConfig.BATCH_SIZE).forEach { batch ->
                    dao.insertTracks(
                        batch.map { ref ->
                            val meta = details[ref]
                            PlaylistTrackEntity(
                                playlistId = id.value,
                                position = nextPosition++,
                                providerId = ref.providerId.value,
                                nativeId = ref.nativeId,
                                addedReason = reason.name,
                                title = meta?.title,
                                artist = meta?.artist,
                                durationMs = meta?.durationMs,
                                artworkUrl = meta?.artworkUrl,
                            )
                        },
                    )
                }
                dao.updatePlaylist(id.value, playlist.title, playlist.description, clock.millis())
            }
        }
    }

    override suspend fun removeTrack(id: PlaylistId, ref: TrackRef): Int =
        withContext(dispatcher) {
            database.withTransaction {
                requirePlaylistExists(dao, id)
                val removed = dao.deleteTrackByRef(id.value, ref.providerId.value, ref.nativeId)
                if (removed > 0) reindex(dao, id.value)
                removed
            }
        }

    override suspend fun removeAtPositions(id: PlaylistId, positions: List<Int>): Int =
        withContext(dispatcher) {
            database.withTransaction {
                requirePlaylistExists(dao, id)
                var removed = 0
                positions.sortedDescending()
                    .chunked(com.cadence.core.common.BatchConfig.BATCH_SIZE)
                    .forEach { batch ->
                        removed += dao.deleteTracksAtPositions(id.value, batch)
                    }
                reindex(dao, id.value)
                removed
            }
        }

    override suspend fun reorder(id: PlaylistId, fromPosition: Int, toPosition: Int): Boolean =
        withContext(dispatcher) {
            database.withTransaction {
                requirePlaylistExists(dao, id)
                val sorted = dao.listTracks(id.value).sortedBy { it.position }
                val last = sorted.size - 1
                if (fromPosition < 0 || fromPosition > last || toPosition < 0 || toPosition > last) {
                    return@withTransaction false
                }
                val item = sorted[fromPosition]
                val mutable = sorted.toMutableList()
                mutable.removeAt(fromPosition)
                mutable.add(toPosition, item)
                val normalized = mutable.mapIndexed { i, t -> t.copy(position = i) }
                dao.replaceTrackList(id.value, normalized)
                true
            }
        }

    override suspend fun replaceTracks(id: PlaylistId, tracks: List<PlaylistTrack>) {
        withContext(dispatcher) {
            database.withTransaction {
                requirePlaylistExists(dao, id)
                val now = clock.millis()
                dao.updatePlaylist(id.value, dao.getPlaylist(id.value)!!.title, dao.getPlaylist(id.value)!!.description, now)
                dao.replaceTrackList(
                    id.value,
                    tracks.mapIndexed { i, t ->
                        PlaylistTrackEntity(
                            playlistId = id.value,
                            position = i,
                            providerId = t.ref.providerId.value,
                            nativeId = t.ref.nativeId,
                            addedReason = t.addedReason.name,
                            title = t.title,
                            artist = t.artist,
                            durationMs = t.durationMs,
                            artworkUrl = t.artworkUrl,
                        )
                    },
                )
            }
        }
    }

    override suspend fun checkDuplicates(id: PlaylistId, refs: List<TrackRef>): List<DuplicateCheckResult> =
        withContext(dispatcher) {
            val positionsByRef = dao.allTrackPositions(id.value)
                .groupBy { it.providerId to it.nativeId }
            refs.map { ref ->
                DuplicateCheckResult(
                    ref = ref,
                    existingPositions = positionsByRef[ref.providerId.value to ref.nativeId]
                        ?.map { it.position }
                        ?: emptyList(),
                )
            }
        }

    override suspend fun trackCount(id: PlaylistId): Int =
        withContext(dispatcher) { dao.countTracks(id.value) }

    override suspend fun listAll(): List<PlaylistMetadata> =
        withContext(dispatcher) {
            dao.listPlaylists().map { entity -> mapMetadata(entity, dao.countTracks(entity.id)) }
        }

    private fun mapMetadata(entity: PlaylistEntity, trackCount: Int): PlaylistMetadata =
        PlaylistMetadata(
            id = PlaylistId(entity.id),
            title = entity.title,
            description = entity.description,
            trackCount = trackCount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    private fun mapTrack(entity: PlaylistTrackEntity): PlaylistTrack =
        PlaylistTrack(
            ref = TrackRef(
                providerId = com.cadence.core.model.ProviderId(entity.providerId),
                nativeId = entity.nativeId,
            ),
            position = entity.position,
            addedReason = AddedReason.valueOf(entity.addedReason),
            title = entity.title,
            artist = entity.artist,
            durationMs = entity.durationMs,
            artworkUrl = entity.artworkUrl,
        )

    private suspend fun requirePlaylistExists(dao: PlaylistDao, id: PlaylistId) {
        if (dao.getPlaylist(id.value) == null) {
            throw IllegalArgumentException("playlist $id not found")
        }
    }

    private suspend fun reindex(dao: PlaylistDao, playlistId: String) {
        val current = dao.listTracks(playlistId)
        val normalized = current.sortedBy { it.position }.mapIndexed { i, t -> t.copy(position = i) }
        dao.replaceTrackList(playlistId, normalized)
    }
}