package com.cadence.source.demo

import com.cadence.core.common.FixtureEnvelope

/**
 * Transport seam of the demo adapter. Production would implement this over an
 * HTTP client; the spike implements it over committed fixtures, keeping the
 * adapter pipeline fully hermetic (ADR-0002 fixture-first rule).
 */
fun interface DemoTransport {
    suspend fun fetch(path: String): FixtureEnvelope
}
