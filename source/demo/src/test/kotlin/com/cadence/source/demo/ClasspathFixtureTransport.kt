package com.cadence.source.demo

import com.cadence.core.common.FixtureEnvelope
import com.cadence.core.common.FixtureLoader

/**
 * Hermetic transport for demo-adapter tests: reads committed fixtures from the
 * module's own test resources. An optional override map reroutes canonical
 * adapter paths to specific scenario fixtures (e.g., error cases), keeping the
 * provider's path logic dumb and its tests explicit.
 */
class ClasspathFixtureTransport(
    private val overrides: Map<String, String> = emptyMap(),
) : DemoTransport {

    override suspend fun fetch(path: String): FixtureEnvelope {
        val bare = path.removeSuffix(".json")
        val actual = overrides[bare] ?: bare
        return FixtureLoader.read(BASE, "$actual.json")
    }

    companion object {
        const val BASE = "fixtures/demo"
    }
}
