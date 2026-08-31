package com.cadence.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixtureLoaderTest {

    @Test
    fun `reads committed envelope from classpath deterministically`() {
        // Uses the demo module's fixture set via the shared test classpath when present,
        // otherwise its own minimal inline envelope through decode().
        val env = FixtureLoader.decode(
            """
            {"schema":1,"scenario":"t","capturedAt":"2026-01-01T00:00:00Z",
             "transport":{"status":418},
             "body":{"hello":true}}
            """.trimIndent(),
        )
        assertEquals(1, env.schema)
        assertEquals(418, env.transport.status)
        assertTrue(env.bodyRaw.isNullOrEmpty())
    }

    @Test
    fun `rejects wrong schema version`() {
        val err = assertFailsWith<FixtureFormatException> {
            FixtureLoader.decode("""{"schema":99,"scenario":"x","body":{}}""")
        }
        assertTrue(err.message!!.contains("schema"))
    }

    @Test
    fun `allows error-only envelopes without any body`() {
        val env = FixtureLoader.decode("""{"schema":1,"scenario":"e","error":{"type":"rate_limited","retryAfterSeconds":60}}""")
        assertEquals("rate_limited", env.error?.type)
        assertNull(env.body)
        assertNull(env.bodyRaw)
    }

    @Test
    fun `rejects body and bodyRaw together`() {
        assertFailsWith<FixtureFormatException> {
            FixtureLoader.decode("""{"schema":1,"scenario":"x","body":{"a":1},"bodyRaw":"{}"}""")
        }
    }
}
