package com.cadence.core.datastore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryCredentialVaultTest {

    private val vault = InMemoryCredentialVault()

    @Test
    fun `round trip stores and retrieves credential`() = runTest {
        val secret = "super-secret-token".toByteArray()
        vault.put(CredentialScope.ACCOUNT, "cookie", secret)
        val retrieved = vault.read(CredentialScope.ACCOUNT, "cookie")
        assertTrue(secret.contentEquals(retrieved!!))
    }

    @Test
    fun `overwrite replaces existing value`() = runTest {
        vault.put(CredentialScope.ACCOUNT, "key", "v1".toByteArray())
        vault.put(CredentialScope.ACCOUNT, "key", "v2".toByteArray())
        val retrieved = vault.read(CredentialScope.ACCOUNT, "key")
        assertEquals("v2", retrieved?.decodeToString())
    }

    @Test
    fun `delete removes credential`() = runTest {
        vault.put(CredentialScope.ACCOUNT, "key", "val".toByteArray())
        assertTrue(vault.delete(CredentialScope.ACCOUNT, "key"))
        assertNull(vault.read(CredentialScope.ACCOUNT, "key"))
    }

    @Test
    fun `delete returns false for missing key`() = runTest {
        assertFalse(vault.delete(CredentialScope.ACCOUNT, "nonexistent"))
    }

    @Test
    fun `read returns null for missing credential`() = runTest {
        assertNull(vault.read(CredentialScope.ACCOUNT, "missing"))
    }

    @Test
    fun `scope isolation prevents cross-scope reads`() = runTest {
        vault.put(CredentialScope.ACCOUNT, "key", "account-val".toByteArray())
        vault.put(CredentialScope.PLAYBACK, "key", "playback-val".toByteArray())
        assertEquals("account-val", vault.read(CredentialScope.ACCOUNT, "key")?.decodeToString())
        assertEquals("playback-val", vault.read(CredentialScope.PLAYBACK, "key")?.decodeToString())
    }

    @Test
    fun `clearScope removes only that scope`() = runTest {
        vault.put(CredentialScope.ACCOUNT, "a", "1".toByteArray())
        vault.put(CredentialScope.PLAYBACK, "p", "2".toByteArray())
        vault.clearScope(CredentialScope.ACCOUNT)
        assertNull(vault.read(CredentialScope.ACCOUNT, "a"))
        assertEquals("2", vault.read(CredentialScope.PLAYBACK, "p")?.decodeToString())
    }

    @Test
    fun `clearAll removes everything`() = runTest {
        vault.put(CredentialScope.ACCOUNT, "a", "1".toByteArray())
        vault.put(CredentialScope.PLAYBACK, "p", "2".toByteArray())
        vault.clearAll()
        assertNull(vault.read(CredentialScope.ACCOUNT, "a"))
        assertNull(vault.read(CredentialScope.PLAYBACK, "p"))
        assertEquals(0, vault.size())
    }

    @Test
    fun `state returns Absent for missing credential`() = runTest {
        assertEquals(CredentialState.Absent, vault.state(CredentialScope.ACCOUNT, "nope"))
    }

    @Test
    fun `state returns Present for existing credential`() = runTest {
        vault.put(CredentialScope.ACCOUNT, "k", "v".toByteArray())
        val s = vault.state(CredentialScope.ACCOUNT, "k")
        assertTrue(s is CredentialState.Present)
    }

    @Test
    fun `read does not leak value into exceptions`() = runTest {
        val secret = "my-secret-key-abc123".toByteArray()
        vault.put(CredentialScope.ACCOUNT, "tok", secret)
        try {
            vault.read(CredentialScope.ACCOUNT, "nonexistent")
        } catch (e: Exception) {
            assertFalse(e.message?.contains("my-secret-key") == true,
                "exception must not contain credential material")
        }
    }

    @Test
    fun `read returns defensive copy`() = runTest {
        val original = "secret".toByteArray()
        vault.put(CredentialScope.ACCOUNT, "k", original)
        val copy1 = vault.read(CredentialScope.ACCOUNT, "k")!!
        val copy2 = vault.read(CredentialScope.ACCOUNT, "k")!!
        assertFalse(copy1 === copy2, "each read must return independent copy")
        assertTrue(copy1.contentEquals(copy2))
    }
}
