package com.cadence.source.ytmusic

import com.cadence.core.model.ResolutionHints
import com.cadence.source.api.SearchQuery
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Authoring-time live capture, NOT a test (never runs in CI, never in the suite).
 *
 * Run via the separate Gradle `liveCapture` JavaExec task:
 *   ./gradlew :source:ytmusic:liveCapture \
 *     -Pytmusic.recordPath=/tmp/opencode/ytmusic_search_alpha.json
 *
 * Executes the REAL YtMusicProvider over the real OkHttp transport, validates
 * search + resolve end-to-end, and records every NewPipe wire exchange to JSON
 * so hermetic fixtures can be authored and replayed in CI.
 */
fun main(args: Array<String>) = runBlocking {
    val recordPath = System.getProperty("ytmusic.recordPath")?.takeIf { it.isNotBlank() }?.let { File(it) }
        ?: error("-Dytmusic.recordPath required")
    val query = System.getProperty("ytmusic.query") ?: "alpha"

    val real = YtMusicTransport.okHttp().downloader()
    val recorder = RecordedWire()
    val provider = YtMusicProvider(YtMusicTransport { RecordingDownloader(real, recorder::capture) })

    try {
        val results = provider.search(SearchQuery(query), cursor = null)
        println("SEARCH hits=${results.items.size}")
        check(results.items.isNotEmpty()) { "live search returned no tracks" }
        results.items.take(6).forEach { println("  - ${it.title} [${it.ref.nativeId}] by ${it.displayArtist()}") }

        val first = results.items.first()
        val resolved = provider.resolve(first.ref, ResolutionHints())
        println("RESOLVE uri=${resolved.redactedUri()} meta=${resolved.metadata}")
        check(resolved.playbackUri.isNotBlank()) { "live resolve returned no uri" }
    } finally {
        try {
            recorder.writeTo(recordPath)
            println("WROTE ${recorder.exchanges().size} exchanges to ${recordPath.absolutePath}")
            for ((i, x) in recorder.exchanges().withIndex()) {
                println("EXCH[$i] ${x.method} ${x.status} ${x.url.take(100)}")
                println("  HEAD: ${x.responseBody.take(160).replace("\n", " ")}")
            }
        } catch (t: Throwable) {
            println("RECORD-FAILURE: ${t}")
        }
    }
}
