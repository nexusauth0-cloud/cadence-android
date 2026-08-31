package com.cadence.android.vaultstore.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Supplies the AES key that wraps vault entries (ADR-0004 §1: Keystore-wrapped
 * AES-GCM). Default implementation uses the Android Keystore. Tests inject a
 * JVM key to avoid the AndroidKeyStore provider.
 */
interface VaultKeyProvider {
    /** Returns an existing key or creates it. Throws on failure. */
    fun getOrCreateKey(): SecretKey
}

/**
 * Android Keystore-backed AES-256 key. The key never leaves the keystore
 * hardware/software boundary — Cipher operates on it by alias.
 */
class KeystoreVaultKeyProvider(
    private val alias: String = KEY_ALIAS,
    private val keyStoreProvider: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) },
) : VaultKeyProvider {

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cadence_credential_vault_v1"
        private const val KEY_SIZE_BITS = 256
    }

    override fun getOrCreateKey(): SecretKey {
        val existing = keyStoreProvider.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}