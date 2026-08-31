package com.cadence.core.database

import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef

/**
 * Domain-level playlist identity. Provider-agnostic — playlist creation
 * is a local operation; remote sync is a separate concern.
 */
data class PlaylistId(val value: String) {
    init {
        require(value.isNotBlank()) { "PlaylistId must not be blank" }
    }
}

/**
 * Playlist metadata. The mutable properties (title, description) are
 * replaced wholesale via [PlaylistRepository.updateMetadata] — no partial
 * field mutation (ADR-0003: entity I/O is forbidden; pure data only).
 */
data class PlaylistMetadata(
    val id: PlaylistId,
    val title: String,
    val description: String = "",
    val trackCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * A single entry in a playlist's ordered track list.
 *
 * [addedReason] captures WHY the track is in the playlist — this is the
 * intent information that ADR-0003 says was lost by snapshot serialization.
 * Preserving it enables "clear suggestions" as a single indexed delete.
 */
enum class AddedReason { USER, SEED, RADIO, SUGGESTED }

data class PlaylistTrack(
    val ref: TrackRef,
    val position: Int,
    val addedReason: AddedReason = AddedReason.USER,
    /** Cached display metadata (ADR-0003 CACHE COLUMNS) so playlist detail and
     *  playback render/play offline without re-resolving from the provider. */
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
) {
    /** Reconstruct a displayable, playable [com.cadence.core.model.Track]. */
    fun toTrack(): com.cadence.core.model.Track? {
        val t = title ?: return null
        return com.cadence.core.model.Track(
            ref = ref,
            title = t,
            artists = artist?.let { listOf(com.cadence.core.model.ArtistRef(it)) } ?: emptyList(),
            durationMs = durationMs,
            artwork = artworkUrl?.let { u ->
                com.cadence.core.model.Artwork(listOf(com.cadence.core.model.ArtworkVariant(u)))
            },
        )
    }
}

/**
 * Result of a duplicate check against a playlist.
 * [existingPositions] contains the zero-based positions where the track
 * already appears (empty list = no duplicates).
 */
data class DuplicateCheckResult(
    val ref: TrackRef,
    val existingPositions: List<Int>,
)
