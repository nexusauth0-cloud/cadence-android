package com.cadence.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceLogTest {

    @Test
    fun `uri redaction strips query components`() {
        assertEquals("https://host.example/stream/abc", SourceLog.redactUri("https://host.example/stream/abc?sig=tok&x=1"))
        assertEquals("<blank>", SourceLog.redactUri("  "))
    }

    @Test
    fun `snippet redaction scrubs credential assignments and truncates`() {
        val scrubbed = SourceLog.redactSnippet("""{"token":"abcdef123456","ok":1}""")
        assertTrue(!scrubbed.contains("abcdef"), scrubbed)
        assertTrue(scrubbed.contains("<redacted>"), scrubbed)
        val plain = SourceLog.redactSnippet("api_key=supersecret x")
        assertTrue(!plain.contains("supersecret"), plain)

        val long = "a".repeat(500)
        val cut = SourceLog.redactSnippet(long)
        assertTrue(cut.length <= 201)
        assertTrue(cut.endsWith("…"))
    }

    @Test
    fun `only header names are considered safe`() {
        val names = SourceLog.safeHeaderNames(mapOf("Authorization" to "Bearer secret", "Accept" to "*/*"))
        assertEquals(setOf("Authorization", "Accept"), names)
    }
}
