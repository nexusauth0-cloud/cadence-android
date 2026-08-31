package com.cadence.core.domain.update

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun testUpdate(
    version: String,
    critical: Boolean = false,
    channel: UpdateChannel = UpdateChannel.STABLE,
    storeUrl: String = "https://play.google.com/store/apps/details?id=com.cadence.app",
) = UpdateInfo(
    version = SemanticVersion.parse(version)!!,
    channel = channel,
    releaseNotes = "Notes",
    minSupported = SemanticVersion.parse("1.0.0")!!,
    critical = critical,
    storeUrl = storeUrl,
)

private class StubSource(var result: UpdateInfo? = null, var error: Exception? = null) : UpdateSource {
    override suspend fun check(current: SemanticVersion): UpdateInfo? {
        error?.let { throw it }
        return result
    }
}

class UpdateManagerTest {

    private fun manager(version: String = "1.0.0") = UpdateManager(
        source = StubSource(),
        currentVersion = SemanticVersion.parse(version)!!,
    )

    @Test
    fun `no update available yields NotAvailable`() = runTest {
        val m = UpdateManager(StubSource(result = null), currentVersion = SemanticVersion.parse("1.0.0")!!)
        m.check()
        assertEquals(UpdateState.NotAvailable, m.state.value)
    }

    @Test
    fun `newer stable update yields Available`() = runTest {
        val m = UpdateManager(
            StubSource(result = testUpdate("1.1.0")),
            currentVersion = SemanticVersion.parse("1.0.0")!!,
        )
        m.check()
        val state = m.state.value as? UpdateState.Available
        assertEquals("1.1.0", state?.info?.version.toString())
    }

    @Test
    fun `older or equal update is ignored`() = runTest {
        val m = UpdateManager(
            StubSource(result = testUpdate("0.9.0")),
            currentVersion = SemanticVersion.parse("1.0.0")!!,
        )
        m.check()
        assertEquals(UpdateState.NotAvailable, m.state.value)
    }

    @Test
    fun `beta update is not offered on stable channel`() = runTest {
        val m = UpdateManager(
            StubSource(result = testUpdate("1.1.0", channel = UpdateChannel.BETA)),
            currentVersion = SemanticVersion.parse("1.0.0")!!,
        )
        m.check()
        assertEquals(UpdateState.NotAvailable, m.state.value)
    }

    @Test
    fun `source failure yields Failed`() = runTest {
        val m = UpdateManager(
            StubSource(error = IllegalStateException("offline")),
            currentVersion = SemanticVersion.parse("1.0.0")!!,
        )
        m.check()
        assertEquals(UpdateState.Failed("offline"), m.state.value)
    }

    @Test
    fun `non critical available can be dismissed`() = runTest {
        val m = UpdateManager(
            StubSource(result = testUpdate("1.1.0", critical = false)),
            currentVersion = SemanticVersion.parse("1.0.0")!!,
        )
        m.check()
        assertEquals(UpdateState.Available::class, m.state.value::class)
        m.dismiss()
        assertEquals(UpdateState.Hidden, m.state.value)
    }

    @Test
    fun `critical available cannot be dismissed`() = runTest {
        val m = UpdateManager(
            StubSource(result = testUpdate("1.1.0", critical = true)),
            currentVersion = SemanticVersion.parse("1.0.0")!!,
        )
        m.check()
        m.dismiss()
        assertTrue(m.state.value is UpdateState.Available)
    }

    @Test
    fun `dismissing nothing is a no-op`() {
        val m = manager()
        m.dismiss()
        assertEquals(UpdateState.Hidden, m.state.value)
    }

    @Test
    fun `policy canDefers only non critical`() {
        assertTrue(UpdatePolicy.canDefer(testUpdate("1.1.0", critical = false)))
        assertFalse(UpdatePolicy.canDefer(testUpdate("1.1.0", critical = true)))
    }

    @Test
    fun `store url must be https and allow-listed`() {
        assertTrue(UpdateManager.isSafeStoreUrl("https://play.google.com/store/apps/details?id=x"))
        assertTrue(UpdateManager.isSafeStoreUrl("https://play.google.com/foo"))
        assertFalse(UpdateManager.isSafeStoreUrl("http://play.google.com/foo"))
        assertFalse(UpdateManager.isSafeStoreUrl("https://evil.example.com/store"))
        assertFalse(UpdateManager.isSafeStoreUrl("javascript:alert(1)"))
    }
}
