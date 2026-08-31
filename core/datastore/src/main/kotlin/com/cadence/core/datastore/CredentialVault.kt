package com.cadence.core.datastore

import kotlin.time.Duration

/**
 * Credential boundary port (ADR-0004 §1–§3).
 *
 * All tokens/cookies/session keys flow through this interface.
 * Domain and core layers never know the storage mechanism (Keystore,
 * EncryptedSharedPreferences, file, database — all opaque to callers).
 *
 * Security invariants enforced by architecture:
 * - Secrets never appear in logs (SourceLog redaction + arch test).
 * - Exceptions never embed credential material.
 * - Credentials are not serialized accidentally (opaque types).
 * - Backup behavior excludes vault contents (ADR-0004 §3).
 * - Cleartext storage is a build-breaking arch test violation.
 * - Caller-owned cancellation; vault operations are suspend functions.
 *
 * The real implementation backs this with Android Keystore + AES-GCM.
 * JVM tests use [InMemoryCredentialVault].
 */
interface CredentialVault {

    /**
     * Store a credential. Overwrites any existing value for the same
     * scope+key combination. The value is treated as an opaque blob —
     * the vault never interprets its contents.
     *
     * @param scope Security namespace for isolation.
     * @param key Unique identifier within the scope.
     * @param value The credential bytes. Must not be empty.
     * @param ttl Optional time-to-live; after which the credential is
     *             considered expired. Null means no expiry.
     */
    suspend fun put(
        scope: CredentialScope,
        key: String,
        value: ByteArray,
        ttl: Duration? = null,
    )

    /**
     * Read a stored credential.
     * @return The credential bytes, or null if absent/expired/locked.
     * @throws CredentialVaultException if the vault is corrupted or
     *         the decryption key is wrong (ADR-0004: degrade loudly).
     */
    suspend fun read(scope: CredentialScope, key: String): ByteArray?

    /**
     * Delete a specific credential.
     * @return true if the credential existed, false otherwise.
     */
    suspend fun delete(scope: CredentialScope, key: String): Boolean

    /**
     * Delete ALL credentials for a given scope.
     * Used on logout (ADR-0004 §2: in-memory cache cleared on lock/logout).
     */
    suspend fun clearScope(scope: CredentialScope)

    /**
     * Delete ALL credentials across all scopes.
     * Used on factory reset or explicit "forget everything".
     */
    suspend fun clearAll()

    /**
     * Observable state for a credential — presence check without reading value.
     * Used by auth-gated features to show sign-in prompts.
     */
    suspend fun state(scope: CredentialScope, key: String): CredentialState
}

/**
 * Vault failure type. Carries NO credential material — only the reason.
 * ADR-0004 §6: vault must degrade loudly (disable account features +
 * user-visible state), never silently fall back to plaintext.
 */
sealed class CredentialVaultException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    /** Vault locked or keystore unavailable. */
    class Locked(reason: String) : CredentialVaultException("vault locked: $reason")

    /** Decryption failed — wrong key, corrupted data, or version mismatch. */
    class Corrupted(reason: String) : CredentialVaultException("vault corrupted: $reason")

    /** Storage I/O failure (disk full, file system error). */
    class StorageFailure(cause: Throwable) : CredentialVaultException("vault storage failure", cause)

    /** Crypto primitive unavailable (algorithm not supported on this device). */
    class CryptoUnavailable(reason: String) : CredentialVaultException("crypto unavailable: $reason")
}
