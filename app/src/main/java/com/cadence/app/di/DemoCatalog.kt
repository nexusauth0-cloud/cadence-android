package com.cadence.app.di

/**
 * Curated demo catalog for the vertical slice. Kept small and deterministic:
 * one search slug returns a handful of tracks, and each track has a matching
 * resolve document so the tap-to-play path completes end to end.
 */
internal object DemoCatalog {

    const val SEARCH_SLUG = "alpha"

    val searchPage1: String = """
        {
          "schema": 1,
          "scenario": "app.search.alpha.page-01",
          "transport": { "status": 200 },
          "body": {
            "results": [
              { "id": "alpha-track",  "title": "Alpha Anthem",  "artist": "Beta Band",  "duration_ms": 210000, "year": 2026, "flags": ["EXPLICIT"], "artwork": [ { "url": "https://fixture.demo/art/alpha.jpg", "w": 640, "h": 640 } ] },
              { "id": "bravo-track",  "title": "Bravo Waltz",   "artist": "Cadence Trio", "duration_ms": 185000, "year": 2025, "flags": [], "artwork": [ { "url": "https://fixture.demo/art/bravo.jpg", "w": 640, "h": 640 } ] },
              { "id": "charlie-track", "title": "Charlie Groove", "artist": "Delta Quartet", "duration_ms": 240000, "year": 2024, "flags": [], "artwork": [ { "url": "https://fixture.demo/art/charlie.jpg", "w": 640, "h": 640 } ] },
              { "id": "delta-track",  "title": "Delta Dawn",    "artist": "Echo Core",    "duration_ms": 198000, "year": 2026, "flags": ["VIDEO"], "artwork": [ { "url": "https://fixture.demo/art/delta.jpg", "w": 640, "h": 640 } ] }
            ],
            "next": 0
          }
        }
    """

    private val resolveTpl: (String, String) -> String = { id, title ->
        """
        {
          "schema": 1,
          "scenario": "app.resolve.$id",
          "transport": { "status": 200 },
          "body": {
            "stream_url": "https://fixture.demo/stream/$id?sig=fixture",
            "expires_in_seconds": 300,
            "headers": { "X-Demo-Mode": "fixture" },
            "renewal_base64": "cmVuZXc6JGlk",
            "rendition_label": "demo-medium",
            "metadata": { "container": "mp4", "codecs": ["mp4a.40.2"], "bitrate_kbps": 192, "sample_rate_hz": 44100, "loudness_db": -7.2, "perceptual_loudness_db": -6.8, "size_bytes": 5400000 }
          }
        }
        """.trimIndent().replace("\$id", id)
    }

    val resolveByNativeId: Map<String, String> = mapOf(
        "alpha-track" to resolveTpl("alpha-track", "Alpha Anthem"),
        "bravo-track" to resolveTpl("bravo-track", "Bravo Waltz"),
        "charlie-track" to resolveTpl("charlie-track", "Charlie Groove"),
        "delta-track" to resolveTpl("delta-track", "Delta Dawn"),
    )
}
