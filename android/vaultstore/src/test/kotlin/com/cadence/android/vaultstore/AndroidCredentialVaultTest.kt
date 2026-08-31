package com.cadence.android.vaultstore

import com.cadence.core.datastore.CredentialScope
import com.cadence.core.datastore.CredentialState
import com.cadence.core.datastore.CredentialVault
import com.cadence.core.datastore.CredentialVaultException
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AndroidCredentialVaultTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var vault: CredentialVault
    private lateinit var testClock: TestClock

    @BeforeEach
    fun setUp() {
        testClock = TestClock(nowMs = 1_000L)
        vault = AndroidCredentialVault(
            rootDir = File(tempDir, "vault"),
            keyProvider = TestVaultKeyProvider(),
            clock = testClock,
        )
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun putThenReadRoundTrips() = runTest {
        val secret = "top-secret-token".toByteArray()
        vault.put(CredentialScope.ACCOUNT, "session.token", secret)

        val result = vault.read(CredentialScope.ACCOUNT, "session.token")
        assertTrue(result!!.contentEquals(secret))
    }

    @Test
    fun scopesAreIsolated() = runTest {
        vault.put(CredentialScope.ACCOUNT, "key", "account-value".toByteArray())
        vault.put(CredentialScope.PLAYBACK, "key", "playback-value".toByteArray())

        assertTrue(vault.read(CredentialScope.ACCOUNT, "key")!!.contentEquals("account-value".toByteArray()))
        assertTrue(vault.read(CredentialScope.PLAYBACK, "key")!!.contentEquals("playback-value".toByteArray()))
    }

    @Test
    fun clearScopeOnlyClearsThatScope() = runTest {
        vault.put(CredentialScope.ACCOUNT, "a", "1".toByteArray())
        vault.put(CredentialScope.SYNC, "a", "2".toByteArray())

        vault.clearScope(CredentialScope.ACCOUNT)

        assertNull(vault.read(CredentialScope.ACCOUNT, "a"))
        assertTrue(vault.read(CredentialScope.SYNC, "a") != null)
    }

    @Test
    fun deleteReturnsWhetherExisted() = runTest {
        vault.put(CredentialScope.SYNC, "k", "v".toByteArray())
        assertTrue(vault.delete(CredentialScope.SYNC, "k"))
        assertFalse(vault.delete(CredentialScope.SYNC, "k"))
        assertNull(vault.read(CredentialScope.SYNC, "k"))
    }

    @Test
    fun clearAllRemovesEverything() = runTest {
        vault.put(CredentialScope.SYNC, "one", "1".toByteArray())
        vault.put(CredentialScope.ACCOUNT, "two", "2".toByteArray())

        vault.clearAll()

        assertNull(vault.read(CredentialScope.SYNC, "one"))
        assertNull(vault.read(CredentialScope.ACCOUNT, "two"))
    }

    @Test
    fun ttlExpiryMakesReadNull() = runTest {
        vault.put(CredentialScope.SYNC, "ttl", "v".toByteArray(), ttl = 10.minutes)
        assertTrue(vault.read(CredentialScope.SYNC, "ttl") != null)

        testClock.advance(11.minutes.inWholeMilliseconds)
        assertNull(vault.read(CredentialScope.SYNC, "ttl"))
    }

    @Test
    fun stateReflectsPresenceAndAge() = runTest {
        vault.put(CredentialScope.SYNC, "st", "v".toByteArray())
        val state = vault.state(CredentialScope.SYNC, "st")
        assertTrue(state is CredentialState.Present)
        assertTrue((state as CredentialState.Present).ageMs >= 0)

        val absent = vault.state(CredentialScope.SYNC, "missing")
        assertEquals(CredentialState.Absent, absent)
    }

    @Test
    fun emptyValueIsRejected() = runTest {
        val thrown = runCatching { vault.put(CredentialScope.SYNC, "empty", ByteArray(0)) }
            .exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
        assertTrue(thrown!!.message!!.contains("must not be empty"))
    }

    @Test
    fun corruptedRecordThrowsLoudly() = runTest {
        vault.put(CredentialScope.SYNC, "corrupt", "v".toByteArray())
        val file = vaultFiles().first()

        // Overwrite ciphertext field (position 6). Header stays valid but the
        // encrypted payload is now garbage → GCM auth must fail loudly.
        val damaged = file.readText().split("|").let { parts ->
            val fixed = parts.toMutableList()
            fixed[6] = "AAAAbW9yZQ"
            fixed.joinToString("|")
        }
        file.writeText(damaged)

        val thrown = runCatching { vault.read(CredentialScope.SYNC, "corrupt") }
            .exceptionOrNull()
        assertTrue(thrown is CredentialVaultException.Corrupted)
        assertTrue(thrown!!.message!!.contains("cannot decrypt"))
    }

    private fun vaultFiles(): List<File> =
        File(tempDir, "vault").listFiles()!!
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles()!!.filter { it.isFile } }
}

private class TestClock(nowMs: Long) : Clock() {
    private var now: Long = nowMs
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    fun advance(ms: Long) {
        now += ms
    }
}