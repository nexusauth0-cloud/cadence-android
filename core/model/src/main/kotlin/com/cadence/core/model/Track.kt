package com.cadence.core.model

/** Content flags surfaced by providers. Unknown provider flags must never reach this set. */
enum class TrackFlag { EXPLICIT, VIDEO }

/**
 * The domain song. Pure value: no stream URLs, no format details, no provider types.
 *
 * Identity semantics: equality/hashCode are defined ONLY by [ref] — two fetches
 * of the same song from the same provider are the same Track even if artwork or
 * metadata drifted between fetches (spike spec section 4.7).
 */
class Track(
    val ref: TrackRef,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val durationMs: Long? = null,
    val album: EntityRef? = null,
    val artwork: Artwork? = null,
    val flags: Set<TrackFlag> = emptySet(),
    val year: Int? = null,
) {
    init {
        require(title.isNotBlank()) { "Track.title must not be blank" }
    }

    override fun equals(other: Any?): Boolean = other is Track && other.ref == ref
    override fun hashCode(): Int = ref.hashCode()
    override fun toString(): String =
        "Track(ref=$ref, title=$title, artists=${artists.map { it.name }}, durationMs=$durationMs, flags=$flags)"

    fun displayArtist(): String = artists.firstOrNull()?.name ?: "Unknown artist"
}
