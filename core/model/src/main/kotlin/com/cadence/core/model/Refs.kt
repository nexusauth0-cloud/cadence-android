package com.cadence.core.model

/** Kind of a navigable entity a provider can point at. */
enum class EntityKind { ARTIST, ALBUM, PLAYLIST }

/**
 * Globally unambiguous pointer to a playable entity.
 *
 * The pair [providerId] + [nativeId] IS the stable domain identity of a track
 * (spike spec section 4.2). `nativeId` is opaque outside the owning provider.
 */
data class TrackRef(
    val providerId: ProviderId,
    val nativeId: String,
) {
    init {
        require(nativeId.isNotBlank()) { "TrackRef.nativeId must not be blank" }
    }
}

/** Pointer to a non-track entity for navigation purposes. */
data class EntityRef(
    val providerId: ProviderId,
    val kind: EntityKind,
    val nativeId: String,
)

/**
 * An artist attribution. Search results frequently lack resolvable artist ids,
 * so [name] is always present and [ref] is optional.
 */
data class ArtistRef(
    val name: String,
    val ref: EntityRef? = null,
)
