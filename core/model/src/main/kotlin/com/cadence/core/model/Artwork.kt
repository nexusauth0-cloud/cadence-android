package com.cadence.core.model

/** Candidate image at a particular size. URLs may embed signed tokens and are therefore log-sensitive. */
data class ArtworkVariant(
    val url: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
)

/**
 * Artwork candidates; the image stack picks the nearest size. At least one variant required.
 */
data class Artwork(val variants: List<ArtworkVariant>) {
    init {
        require(variants.isNotEmpty()) { "Artwork requires at least one variant" }
    }
}
