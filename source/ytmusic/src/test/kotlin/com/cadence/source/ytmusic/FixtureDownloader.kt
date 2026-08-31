package com.cadence.source.ytmusic

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

/**
 * Hermetic NewPipe [Downloader] that replays recorded wire responses keyed by
 * the request URL. Used so the REAL NewPipe extraction + mapping pipeline runs
 * in CI with zero network (ADR-0002 fixture-first).
 *
 * [router] returns null for an unmapped request; then this downloader throws an
 * [IOException] (mapped to NetworkUnreachable) so unmapped traffic is caught as
 * a test failure rather than silently passing.
 */
class FixtureDownloader(
    private val router: (Request) -> Response?,
) : Downloader() {

    override fun execute(request: Request): Response {
        return router(request)
            ?: throw IOException("FixtureDownloader: no fixture for ${request.httpMethod()} ${request.url()}")
    }
}

/**
 * Passthrough recorder: delegates to a real [Downloader] and records every
 * (request, response) so fixtures can be authored from real extraction runs.
 */
class RecordingDownloader(
    private val delegate: Downloader,
    private val sink: (Request, Response) -> Unit,
) : Downloader() {

    override fun execute(request: Request): Response {
        val response = delegate.execute(request)
        sink(request, response)
        return response
    }

    override fun toString(): String = "RecordingDownloader($delegate)"
}
