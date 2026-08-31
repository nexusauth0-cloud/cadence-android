package com.cadence.app.di

import com.cadence.core.common.FixtureEnvelope
import com.cadence.core.common.FixtureLoader
import com.cadence.source.demo.DemoTransport

/**
 * Hermetic in-memory [DemoTransport] for the runnable vertical slice. Serves
 * committed fixture-shaped envelopes (never a live network), so the app
 * launches and the Search -> play flow works with zero connectivity or keys.
 * Paths mirror the demo provider's canonical layout (search/happy/<slug>/
 * page-01.json, resolve/happy/<nativeId>.json).
 */
class MemoryFixtureTransport(
    search: List<Pair<String, String>>,
    resolve: Map<String, String>,
) : DemoTransport {

    private val searchBySlug: Map<String, List<String>> =
        search.map { it.first to it.second }.groupBy({ it.first }, { it.second })

    private val resolveByNativeId: Map<String, String> = resolve

    override suspend fun fetch(path: String): FixtureEnvelope {
        val bare = path.removeSuffix(".json")
        val fixtureJson: String? = when {
            bare.startsWith("search/happy/") -> {
                val slug = bare.removePrefix("search/happy/").substringBefore("/")
                searchBySlug[slug]?.firstOrNull()
            }
            bare.startsWith("resolve/happy/") -> {
                resolveByNativeId[bare.removePrefix("resolve/happy/")]
            }
            else -> null
        }
        return FixtureLoader.decode(
            fixtureJson ?: error("demo transport has no fixture for '$path'"),
        )
    }
}
