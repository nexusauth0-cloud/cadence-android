package com.cadence.core.database

import com.cadence.core.model.TrackRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * In-memory [PlaylistRepository] for JVM tests.
 * Not suitable for production — no persistence, no Room, no transactions.
 */
class InMemoryPlaylistRepository : PlaylistRepository {

    private data class PlaylistEntry(
        var metadata: PlaylistMetadata,
        val tracks: MutableList<PlaylistTrack> = mutableListOf(),
    )

    private val mutex = Mutex()
    private val playlists = mutableMapOf<PlaylistId, PlaylistEntry>()

    override suspend fun create(title: String, description: String): PlaylistId =
        mutex.withLock {
            val id = PlaylistId(UUID.randomUUID().toString().take(12))
            val now = System.currentTimeMillis()
            playlists[id] = PlaylistEntry(
                metadata = PlaylistMetadata(id = id, title = title, description = description, createdAt = now, updatedAt = now)
            )
            id
        }

    override suspend fun getMetadata(id: PlaylistId): PlaylistMetadata? =
        mutex.withLock { playlists[id]?.metadata }

    override suspend fun updateMetadata(id: PlaylistId, title: String, description: String) =
        mutex.withLock {
            val entry = playlists[id] ?: throw IllegalArgumentException("playlist $id not found")
            entry.metadata = entry.metadata.copy(
                title = title,
                description = description,
                updatedAt = System.currentTimeMillis(),
            )
        }

    override suspend fun delete(id: PlaylistId): Boolean =
        mutex.withLock { playlists.remove(id) != null }

    override suspend fun getTracks(id: PlaylistId): List<PlaylistTrack> =
        mutex.withLock {
            playlists[id]?.tracks?.toList()
                ?: throw IllegalArgumentException("playlist $id not found")
        }

    override suspend fun addTrack(id: PlaylistId, ref: TrackRef, reason: AddedReason) =
        mutex.withLock {
            val entry = playlists[id] ?: throw IllegalArgumentException("playlist $id not found")
            val pos = entry.tracks.size
            entry.tracks.add(PlaylistTrack(ref = ref, position = pos, addedReason = reason))
            entry.metadata = entry.metadata.copy(
                trackCount = entry.tracks.size,
                updatedAt = System.currentTimeMillis(),
            )
        }

    override suspend fun addTracks(id: PlaylistId, refs: List<TrackRef>, reason: AddedReason) =
        mutex.withLock {
            val entry = playlists[id] ?: throw IllegalArgumentException("playlist $id not found")
            refs.forEach { ref ->
                entry.tracks.add(PlaylistTrack(ref = ref, position = entry.tracks.size, addedReason = reason))
            }
            entry.metadata = entry.metadata.copy(
                trackCount = entry.tracks.size,
                updatedAt = System.currentTimeMillis(),
            )
        }

    override suspend fun addTracksDetailed(
        id: PlaylistId,
        refs: List<TrackRef>,
        details: Map<TrackRef, LibraryTrackMeta>,
        reason: AddedReason,
    ) = mutex.withLock {
        val entry = playlists[id] ?: throw IllegalArgumentException("playlist $id not found")
        refs.forEach { ref ->
            val meta = details[ref]
            entry.tracks.add(
                PlaylistTrack(
                    ref = ref,
                    position = entry.tracks.size,
                    addedReason = reason,
                    title = meta?.title,
                    artist = meta?.artist,
                    durationMs = meta?.durationMs,
                    artworkUrl = meta?.artworkUrl,
                ),
            )
        }
        entry.metadata = entry.metadata.copy(
            trackCount = entry.tracks.size,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun removeTrack(id: PlaylistId, ref: TrackRef): Int =
        mutex.withLock {
            val entry = playlists[id] ?: throw IllegalArgumentException("playlist $id not found")
            val removed = entry.tracks.removeAll { it.ref == ref }
            if (removed) {
                reindex(entry)
                entry.metadata = entry.metadata.copy(
                    trackCount = entry.tracks.size,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            if (removed) 1 else 0
        }

    override suspend fun removeAtPositions(id: PlaylistId, positions: List<Int>): Int =
        mutex.withLock {
            val entry = playlists[id] ?: throw IllegalArgumentException("playlist $id not found")
            val sorted = positions.sortedDescending()
            var count = 0
            for (pos in sorted) {
                if (pos in entry.tracks.indices) {
                    entry.tracks.removeAt(pos)
                    count++
                }
            }
            if (count > 0) {
                reindex(entry)
                entry.metadata = entry.metadata.copy(
                    trackCount = entry.tracks.size,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            count
        }

    override suspend fun reorder(id: PlaylistId, fromPosition: Int, toPosition: Int): Boolean =
        mutex.withLock {
            val entry = playlists[id] ?: throw IllegalArgumentException("playlist $id not found")
            if (fromPosition !in entry.tracks.indices || toPosition !in entry.tracks.indices) return@withLock false
            val item = entry.tracks.removeAt(fromPosition)
            entry.tracks.add(toPosition, item)
            reindex(entry)
            entry.metadata = entry.metadata.copy(updatedAt = System.currentTimeMillis())
            true
        }

    override suspend fun replaceTracks(id: PlaylistId, tracks: List<PlaylistTrack>) =
        mutex.withLock {
            val entry = playlists[id] ?: throw IllegalArgumentException("playlist $id not found")
            entry.tracks.clear()
            entry.tracks.addAll(tracks.mapIndexed { i, t -> t.copy(position = i) })
            entry.metadata = entry.metadata.copy(
                trackCount = entry.tracks.size,
                updatedAt = System.currentTimeMillis(),
            )
        }

    override suspend fun checkDuplicates(id: PlaylistId, refs: List<TrackRef>): List<DuplicateCheckResult> =
        mutex.withLock {
            val entry = playlists[id] ?: throw IllegalArgumentException("playlist $id not found")
            refs.map { ref ->
                DuplicateCheckResult(
                    ref = ref,
                    existingPositions = entry.tracks
                        .filter { it.ref == ref }
                        .map { it.position },
                )
            }
        }

    override suspend fun trackCount(id: PlaylistId): Int =
        mutex.withLock { playlists[id]?.tracks?.size ?: 0 }

    override suspend fun listAll(): List<PlaylistMetadata> =
        mutex.withLock { playlists.values.map { it.metadata } }

    private fun reindex(entry: PlaylistEntry) {
        val reindexed = entry.tracks.mapIndexed { i, t -> t.copy(position = i) }
        entry.tracks.clear()
        entry.tracks.addAll(reindexed)
    }
}
