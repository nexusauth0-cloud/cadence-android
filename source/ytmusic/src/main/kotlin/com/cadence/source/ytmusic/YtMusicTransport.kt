package com.cadence.source.ytmusic

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader
import java.util.concurrent.TimeUnit

/**
 * Seam that supplies the NewPipe [Downloader] the provider runs against.
 *
 * - Production: build with [okHttp] — real network via OkHttp.
 * - Tests: construct a fixture-replay [Downloader] (see test sources) and pass
 *   it as `YtMusicTransport { fixtureDownloader }`, so the REAL extractor
 *   parsing runs hermetically in CI with zero network.
 *
 * This isolates the only non-deterministic hop (the remote HTTP round trip)
 * while keeping the entire extraction + mapping pipeline faithful.
 */
fun interface YtMusicTransport {
    fun downloader(): Downloader

    companion object {
        /** Real network transport over OkHttp with bounded timeouts (no retries). */
        fun okHttp(
            connectTimeoutMs: Long = 10_000,
            readTimeoutMs: Long = 20_000,
        ): YtMusicTransport = YtMusicTransport {
            val client = OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(30_000, TimeUnit.MILLISECONDS)
                .build()
            YtMusicDownloader(client)
        }
    }
}
