package com.cadence.android.vaultstore

import com.cadence.android.vaultstore.crypto.VaultKeyProvider
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * JVM-only key provider so AndroidCredentialVault is fully testable on the
 * local JVM without an AndroidKeystore. Holds a single in-memory AES key.
 */
class TestVaultKeyProvider : VaultKeyProvider {

    private val key: SecretKey by lazy {
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }

    override fun getOrCreateKey(): SecretKey = key
}