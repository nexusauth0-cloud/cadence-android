package com.cadence.android.vaultstore.record

import java.util.Base64

/**
 * On-disk encoding of one vault entry (ADR-0004). The file is a single line of
 * Base64-encoded records so it is not vulnerable to newline ambiguity:
 *
 *    header|scope|key|createdAt|expiresAt|iv|ciphertext
 *
 * No credential material is placed in the file path or the scope/key fields;
 * the key is a user-chosen identifier, never a secret. The ciphertext is the
 * only sensitive field and it is encrypted with AES-GCM via the Keystore key.
 */
internal data class VaultRecord(
    val scope: String,
    val key: String,
    val createdAt: Long,
    val expiresAt: Long, // 0 when no TTL
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

internal object VaultRecordCodec {

    private const val HEADER = "CADENCE-VAULT-1"

    fun encode(record: VaultRecord): String {
        val parts = listOf(
            HEADER,
            record.scope,
            record.key,
            record.createdAt,
            record.expiresAt,
            Base64.getEncoder().encodeToString(record.iv),
            Base64.getEncoder().encodeToString(record.ciphertext),
        )
        return parts.joinToString(separator = "|")
    }

    fun decode(line: String): VaultRecord {
        val parts = line.splitToSequence('|').toList()
        if (parts.size != 7 || parts[0] != HEADER) {
            throw VaultRecordCorruptedException("malformed vault record header")
        }
        return VaultRecord(
            scope = parts[1],
            key = parts[2],
            createdAt = parts[3].toLongOrNull() ?: throw VaultRecordCorruptedException("bad createdAt"),
            expiresAt = parts[4].toLongOrNull() ?: throw VaultRecordCorruptedException("bad expiresAt"),
            iv = Base64.getDecoder().decode(parts[5]),
            ciphertext = Base64.getDecoder().decode(parts[6]),
        )
    }
}

internal class VaultRecordCorruptedException(reason: String) : RuntimeException(reason)