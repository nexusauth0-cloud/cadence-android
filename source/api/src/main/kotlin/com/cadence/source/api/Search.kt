package com.cadence.source.api

import com.cadence.core.model.ProviderId

enum class SearchFilter { TRACK, ALBUM, ARTIST, PLAYLIST }

/** Which providers a query targets. `AllRegistered` fan-out/merge is post-spike. */
sealed interface ProviderScope {
    data class Single(val id: ProviderId) : ProviderScope
    data object AllRegistered : ProviderScope
}

data class SearchQuery(
    val text: String,
    val filter: SearchFilter? = null,
    val scope: ProviderScope = ProviderScope.AllRegistered,
) {
    init {
        require(text.isNotBlank()) { "SearchQuery.text must not be blank" }
        require(text.length <= 512) { "SearchQuery.text too long" }
    }
}

/** Opaque continuation token; provider-encoded, not persistable across app versions. */
@JvmInline
value class PageCursor(val token: String)

data class PagedResult<T>(
    val items: List<T>,
    val nextCursor: PageCursor? = null,
)
