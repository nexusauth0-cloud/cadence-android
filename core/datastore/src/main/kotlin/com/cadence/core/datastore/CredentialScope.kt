package com.cadence.core.datastore

/**
 * Security scope for credential isolation (ADR-0004 §2).
 * Each scope represents an independent credential namespace — credentials
 * in one scope are invisible to another.
 */
enum class CredentialScope {
    ACCOUNT,
    PLAYBACK,
    SYNC,
}
