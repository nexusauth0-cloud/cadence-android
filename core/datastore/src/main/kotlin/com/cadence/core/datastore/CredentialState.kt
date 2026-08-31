package com.cadence.core.datastore

import kotlin.time.Duration

/**
 * Observable credential state (ADR-0004 §6).
 * No credential material is exposed — only presence/absence/age.
 */
sealed interface CredentialState {
    /** No credential stored for this scope+key. */
    data object Absent : CredentialState

    /** Credential exists; [ageMs] is approximate time since last write. */
    data class Present(val ageMs: Long) : CredentialState

    /** Vault is locked or unavailable; operations must degrade loudly. */
    data class Unavailable(val reason: String) : CredentialState
}
