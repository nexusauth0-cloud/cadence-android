package com.cadence.core.database

import com.cadence.core.model.ArtistRef
import com.cadence.core.model.Artwork
import com.cadence.core.model.ArtworkVariant
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef

/**
 * A track the user has explicitly saved/favorited into their local library.
 *
 * [ref] is the canonical stable identity (Track remains the domain song; this is
 * a persistence/ownership record that REFERENCES [TrackRef], never a second
 * definition of track identity). The cached display fields (title/artist/artwork)
 * are stored so the Library renders fully offline with no network — the local
 * database is the product (ADR-0003). They are metadata caches, not identity.
 */
data class SavedTrack(
    val ref: TrackRef,
    val savedAt: Long,
    val cachedTitle: String,
    val cachedArtist: String? = null,
    val cachedDurationMs: Long? = null,
    val artworkUrl: String? = null,
) {
    /** Reconstruct a displayable, playable [Track] from cache — no network. */
    fun toTrack(): Track = Track(
        ref = ref,
        title = cachedTitle,
        artists = cachedArtist?.let { listOf(ArtistRef(it)) } ?: emptyList(),
        durationMs = cachedDurationMs,
        artwork = artworkUrl?.let { Artwork(listOf(ArtworkVariant(it))) },
    )
}

/**
 * One successful playback start recorded to local history.
 *
 * Recorded ONLY on genuine successful playback starts/completions — failed
 * resolution and cancelled playback are never counted. [playedAt] is the
 * timestamp of the playback start.
 */
data class PlayHistoryEntry(
    val ref: TrackRef,
    val playedAt: Long,
    val cachedTitle: String,
    val cachedArtist: String? = null,
    val cachedDurationMs: Long? = null,
    val artworkUrl: String? = null,
) {
    /** Reconstruct a displayable, playable [Track] from cache — no network. */
    fun toTrack(): Track = Track(
        ref = ref,
        title = cachedTitle,
        artists = cachedArtist?.let { listOf(ArtistRef(it)) } ?: emptyList(),
        durationMs = cachedDurationMs,
        artwork = artworkUrl?.let { Artwork(listOf(ArtworkVariant(it))) },
    )
}
