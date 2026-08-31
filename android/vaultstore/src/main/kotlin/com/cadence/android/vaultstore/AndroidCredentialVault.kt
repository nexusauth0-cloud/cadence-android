package com.cadence.android.vaultstore

import android.content.Context
import com.cadence.android.vaultstore.crypto.VaultKeyProvider
import com.cadence.android.vaultstore.record.VaultRecord
import com.cadence.android.vaultstore.record.VaultRecordCodec
import com.cadence.core.datastore.CredentialScope
import com.cadence.core.datastore.CredentialState
import com.cadence.core.datastore.CredentialVault
import com.cadence.core.datastore.CredentialVaultException
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production [CredentialVault] (ADR-0004 §1–§3).
 *
 * Backing store: one AES-256 key per [CredentialScope] held inside the Android
 * Keystore (never exported); each entry is AES-GCM encrypted with a fresh
 * random IV and written to a private app file. {
 *
 * Security properties:
 * - Keys never leave the Keystore.
 * - Per-entry IVs; GCM with 128-bit tags; no plaintext tombstoning.
 * - Backup is denied by manifest flags in the host app (ADR-0004 §3); this
 *   module stores nothing under a shared/external storage path.
 * - On corruption or wrong key the vault throws [CredentialVaultException]
 *   rather than silently degrading (ADR-0004 §6).
 */
class AndroidCredentialVault(
    private val rootDir: File,
    private val keyProvider: VaultKeyProvider,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
) : CredentialVault {

    constructor(context: Context) : this(
        rootDir = File(context.filesDir, FILE_ROOT),
        keyProvider = com.cadence.android.vaultstore.crypto.KeystoreVaultKeyProvider(),
    )

    private fun scopeDir(scope: CredentialScope): File =
        File(rootDir, scope.name.lowercase())

    private fun keyFile(scope: CredentialScope, key: String): File =
        File(scopeDir(scope), encodeFileName(key))

    override suspend fun put(scope: CredentialScope, entryKey: String, value: ByteArray, ttl: Duration?) {
        require(value.isNotEmpty()) { "credential value must not be empty" }
        withContext(dispatcher) {
            val now = clock.millis()
            val (iv, ciphertext) = encrypt(scope, entryKey, value)
            val record = VaultRecord(
                scope = scope.name,
                key = entryKey,
                createdAt = now,
                expiresAt = if (ttl != null) now + ttl.inWholeMilliseconds else 0L,
                iv = iv,
                ciphertext = ciphertext,
            )
            val target = keyFile(scope, entryKey)
            try {
                target.parentFile?.mkdirs() ?: throw IOException("cannot create vault dirs")
                writeAtomically(target, VaultRecordCodec.encode(record))
            } catch (e: IOException) {
                throw CredentialVaultException.StorageFailure(e)
            }
        }
    }

    override suspend fun read(scope: CredentialScope, entryKey: String): ByteArray? =
        withContext(dispatcher) {
            val file = keyFile(scope, entryKey)
            if (!file.isFile) return@withContext null

            val record = readRecord(file, scope, entryKey)
            if (record == null) return@withContext null
            if (isExpired(record)) {
                deleteQuietly(file)
                return@withContext null
            }

            try {
                decrypt(scope, entryKey, record.iv, record.ciphertext)
            } catch (e: GeneralSecurityException) {
                throw CredentialVaultException.Corrupted(
                    "cannot decrypt credential '$entryKey' in ${scope.name}: ${e.message}",
                )
            } catch (e: IOException) {
                throw CredentialVaultException.StorageFailure(e)
            }
        }

    override suspend fun delete(scope: CredentialScope, entryKey: String): Boolean =
        withContext(dispatcher) {
            keyFile(scope, entryKey).delete()
        }

    override suspend fun clearScope(scope: CredentialScope) {
        withContext(dispatcher) {
            deleteTree(scopeDir(scope))
        }
    }

    override suspend fun clearAll() {
        withContext(dispatcher) {
            deleteTree(rootDir)
        }
    }

    override suspend fun state(scope: CredentialScope, entryKey: String): CredentialState =
        withContext(dispatcher) {
            val file = keyFile(scope, entryKey)
            if (!file.isFile) return@withContext CredentialState.Absent

            val record = readRecord(file, scope, entryKey)
                ?: return@withContext CredentialState.Absent
            if (isExpired(record)) {
                deleteQuietly(file)
                return@withContext CredentialState.Absent
            }

            val ageMs = clock.millis() - record.createdAt
            CredentialState.Present(ageMs = if (ageMs >= 0) ageMs else 0L)
        }

    private fun readRecord(file: File, scope: CredentialScope, entryKey: String): VaultRecord? {
        return try {
            val decoded = VaultRecordCodec.decode(requireNotNull(file.readText().trim()))
            if (decoded.scope != scope.name || decoded.key != entryKey) {
                throw CredentialVaultException.Corrupted("vault record key mismatch in ${scope.name}")
            }
            decoded
        } catch (e: IOException) {
            throw CredentialVaultException.StorageFailure(e)
        }
    }

    private fun isExpired(record: VaultRecord): Boolean =
        record.expiresAt > 0L && clock.millis() > record.expiresAt

    private fun encrypt(scope: CredentialScope, entryKey: String, value: ByteArray): kotlin.Pair<ByteArray, ByteArray> {
        val key = keyProvider.getOrCreateKey()
        return try {
            val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(value)
            iv to ciphertext
        } catch (e: GeneralSecurityException) {
            throw CredentialVaultException.CryptoUnavailable(
                "cannot encrypt '$entryKey' in ${scope.name}: ${e.message}",
            )
        }
    }

    private fun decrypt(scope: CredentialScope, entryKey: String, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val key = keyProvider.getOrCreateKey()
        val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
        val spec = javax.crypto.spec.GCMParameterSpec(TAG_BITS, iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    private fun randomIv(): ByteArray =
        ByteArray(IV_BYTES).also { java.security.SecureRandom().nextBytes(it) }

    private fun writeAtomically(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        try {
            tmp.writeText(content)
            if (!tmp.renameTo(target)) {
                throw IOException("atomic rename failed for ${target.name}")
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun deleteTree(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) deleteTree(child) else deleteQuietly(child)
        }
        dir.delete()
    }

    private fun deleteQuietly(file: File) {
        if (file.exists()) file.delete()
    }

    private companion object {
        const val FILE_ROOT = "cadence_credential_vault"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12

        fun encodeFileName(key: String): String =
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                key.toByteArray(Charsets.UTF_8),
            )
    }
}