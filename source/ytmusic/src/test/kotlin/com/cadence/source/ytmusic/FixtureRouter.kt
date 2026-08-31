package com.cadence.source.ytmusic

import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.File

/**
 * Builds a URL-substring routing function for [FixtureDownloader] from a set of
 * recorded wire exchanges. Replays the REAL NewPipe extraction pipeline
 * hermetically: a request is answered with the recorded response whose path
 * keyword matches first, in the priority order below.
 *
 * Exchanges recorded for endpoints that a fresh extractor run happens not to
 * re-hit are simply left unused (harmless); every request must match at least
 * one recorded exchange or [FixtureDownloader] throws (surfacing an egregious
 * routing gap as a test failure rather than silent misbehavior).
 */
object FixtureRouter {

    /** Priority-ordered path keywords; first contained in a request URL wins. */
    private val ORDER: List<String> = listOf(
        "music/get_search_suggestions",
        "youtubei/v1/search",
        "reel_item_watch",
        "youtubei/v1/player",
        "youtubei/v1/next",
        "youtubei/v1/visitor_id",
        "sw.js",
        "/results",
    )

    fun fromExchanges(exchanges: List<CapturedExchange>): (Request) -> Response? {
        val rules: List<Pair<String, CapturedExchange>> = ORDER.mapNotNull { keyword ->
            exchanges.firstOrNull { e -> e.url.contains(keyword, ignoreCase = true) }
                ?.let { keyword to it }
        }
        return { request ->
            val url = request.url()
            rules.firstOrNull { (keyword, _) -> url.contains(keyword, ignoreCase = true) }
                ?.let { (_, e) -> toResponse(e) }
        }
    }

    fun load(file: File): (Request) -> Response? =
        fromExchanges(RecordedWire().loadFrom(file))

    private fun toResponse(e: CapturedExchange): Response =
        Response(e.status, reason(e.status), e.headers, e.responseBody, e.url)

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        else -> "HTTP $code"
    }
}
