package com.cadence.core.datastore

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * In-memory [CredentialVault] for JVM tests.
 *
 * Not suitable for production — no encryption, no backup exclusion, no Keystore.
 * Proves the port contract works before the Android-backed implementation exists.
 */
class InMemoryCredentialVault : CredentialVault {

    private data class Entry(
        val value: ByteArray,
        val createdAt: Long = System.currentTimeMillis(),
        val ttlMs: Long? = null,
    ) {
        fun isExpired(now: Long): Boolean =
            ttlMs != null && (now - createdAt) > ttlMs
    }

    private val mutex = Mutex()
    private val store = mutableMapOf<Pair<CredentialScope, String>, Entry>()

    /** Total number of stored entries across all scopes (test helper). */
    suspend fun size(): Int = mutex.withLock { store.size }

    override suspend fun put(
        scope: CredentialScope,
        key: String,
        value: ByteArray,
        ttl: Duration?,
    ) = mutex.withLock {
        require(value.isNotEmpty()) { "credential value must not be empty" }
        store[scope to key] = Entry(
            value = value.copyOf(),
            ttlMs = ttl?.inWholeMilliseconds,
        )
    }

    override suspend fun read(scope: CredentialScope, key: String): ByteArray? =
        mutex.withLock {
            val entry = store[scope to key] ?: return@withLock null
            if (entry.isExpired(System.currentTimeMillis())) {
                store.remove(scope to key)
                null
            } else {
                entry.value.copyOf()
            }
        }

    override suspend fun delete(scope: CredentialScope, key: String): Boolean =
        mutex.withLock {
            store.remove(scope to key) != null
        }

    override suspend fun clearScope(scope: CredentialScope): Unit = mutex.withLock {
        store.keys.removeAll { it.first == scope }
    }

    override suspend fun clearAll(): Unit = mutex.withLock {
        store.clear()
    }

    override suspend fun state(scope: CredentialScope, key: String): CredentialState =
        mutex.withLock {
            val entry = store[scope to key] ?: return@withLock CredentialState.Absent
            if (entry.isExpired(System.currentTimeMillis())) {
                store.remove(scope to key)
                CredentialState.Absent
            } else {
                val ageMs = System.currentTimeMillis() - entry.createdAt
                CredentialState.Present(ageMs)
            }
        }
}
