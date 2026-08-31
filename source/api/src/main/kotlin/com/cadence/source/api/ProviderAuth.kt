package com.cadence.source.api

import com.cadence.core.model.ProviderId

/** Static declaration of what a provider can do (spike spec section 5). */
enum class Capability { SEARCH, TRACK_DETAIL, RELATED, RADIO, RESOLVE, ACCOUNT_SYNC }

/**
 * Minimal, non-secret description of an authenticated principal. Null in
 * [SourceProvider.authState] means the provider operates anonymously.
 * Credentials/tokens themselves NEVER live here — they stay behind ADR-0004's
 * credential vault inside provider implementations.
 */
data class ProviderAuth(val subject: String) {
    init {
        require(subject.isNotBlank())
    }
}
