package com.cadence.android.vaultstore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cadence.core.datastore.CredentialScope
import com.cadence.core.datastore.CredentialState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device integration test for [AndroidCredentialVault] backed by the real
 * Android Keystore. The JVM unit tests use [TestVaultKeyProvider]; this test
 * proves the production [com.cadence.android.vaultstore.crypto.KeystoreVaultKeyProvider]
 * path works end-to-end on a device (real KeyStore-backed AES-GCM).
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreVaultInstrumentedTest {

    private val context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val vault: AndroidCredentialVault by lazy {
        AndroidCredentialVault(context)
    }

    @Test
    fun roundTripsSecretThroughAndroidKeystore() = runTest {
        val secret = "keystore-backed-token".toByteArray()
        vault.put(CredentialScope.PLAYBACK, "stream.token", secret)

        val result = vault.read(CredentialScope.PLAYBACK, "stream.token")
        assertTrue("read secret should equal written secret", result!!.contentEquals(secret))

        // state is reported Present with a real, non-negative age
        val state = vault.state(CredentialScope.PLAYBACK, "stream.token")
        assertTrue(state is CredentialState.Present)
        assertTrue((state as CredentialState.Present).ageMs >= 0)

        vault.delete(CredentialScope.PLAYBACK, "stream.token")
        assertNull(vault.read(CredentialScope.PLAYBACK, "stream.token"))
    }

    @Test
    fun scopesRemainIsolatedOnDevice() = runTest {
        vault.clearAll()
        vault.put(CredentialScope.ACCOUNT, "a", "1".toByteArray())
        vault.put(CredentialScope.SYNC, "a", "2".toByteArray())

        assertTrue(vault.read(CredentialScope.ACCOUNT, "a")!!.contentEquals("1".toByteArray()))
        assertTrue(vault.read(CredentialScope.SYNC, "a")!!.contentEquals("2".toByteArray()))

        vault.clearScope(CredentialScope.ACCOUNT)
        assertNull(vault.read(CredentialScope.ACCOUNT, "a"))
        assertEquals("2".toByteArray().contentToString(), vault.read(CredentialScope.SYNC, "a")!!.contentToString())
        vault.clearAll()
    }
}
