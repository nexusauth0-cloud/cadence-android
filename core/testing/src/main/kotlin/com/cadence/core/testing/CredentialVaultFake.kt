package com.cadence.core.testing

import com.cadence.core.datastore.CredentialScope
import com.cadence.core.datastore.CredentialState
import com.cadence.core.datastore.CredentialVault
import com.cadence.core.datastore.CredentialVaultException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * Fake [CredentialVault] for domain/data tests. Records all interactions.
 */
class CredentialVaultFake : CredentialVault {

    private val mutex = Mutex()
    private val store = mutableMapOf<Pair<CredentialScope, String>, ByteArray>()

    val putCalls = mutableListOf<Triple<CredentialScope, String, ByteArray>>()
    val deleteCalls = mutableListOf<Pair<CredentialScope, String>>()
    val clearScopeCalls = mutableListOf<CredentialScope>()
    var clearAllCalls = 0; private set

    /** If set, [read] throws this instead of returning. */
    var readError: CredentialVaultException? = null

    override suspend fun put(
        scope: CredentialScope,
        key: String,
        value: ByteArray,
        ttl: Duration?,
    ) = mutex.withLock {
        putCalls.add(Triple(scope, key, value.copyOf()))
        store[scope to key] = value.copyOf()
    }

    override suspend fun read(scope: CredentialScope, key: String): ByteArray? =
        mutex.withLock {
            readError?.let { throw it }
            store[scope to key]?.copyOf()
        }

    override suspend fun delete(scope: CredentialScope, key: String): Boolean =
        mutex.withLock {
            deleteCalls.add(scope to key)
            store.remove(scope to key) != null
        }

    override suspend fun clearScope(scope: CredentialScope): Unit = mutex.withLock {
        clearScopeCalls.add(scope)
        store.keys.removeAll { it.first == scope }
    }

    override suspend fun clearAll(): Unit = mutex.withLock {
        clearAllCalls++
        store.clear()
    }

    override suspend fun state(scope: CredentialScope, key: String): CredentialState =
        mutex.withLock {
            if (store.containsKey(scope to key)) CredentialState.Present(0)
            else CredentialState.Absent
        }
}
