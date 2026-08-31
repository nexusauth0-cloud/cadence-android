package com.cadence.source.ytmusic

import com.cadence.source.api.SourceError
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

/**
 * Production network transport for the YT Music provider: an OkHttp-backed
 * implementation of NewPipe's [Downloader], executing extractor requests over
 * the real network with bounded timeouts.
 *
 * Ownership/confinement (ADR-0002, LICENSE_DECISION.md cond.5): this HTTP
 * client lives only in :source:ytmusic. Core/domain never depend on it, and no
 * provider/HTTP type leaks beyond this module.
 */
internal class YtMusicDownloader(
    private val client: OkHttpClient,
) : Downloader() {

    override fun execute(request: Request): Response {
        check(request.httpMethod().uppercase() in setOf("GET", "POST", "HEAD"))
        return when (request.httpMethod().uppercase()) {
            "POST" -> doPost(request)
            "HEAD" -> doHead(request)
            else -> doGet(request)
        }
    }

    private fun doGet(request: Request): Response {
        val call = client.newCall(okHttpRequest(request).get().build())
        return call.execute().toNewPipeResponse()
    }

    private fun doPost(request: Request): Response {
        val bytes: ByteArray = request.dataToSend() ?: ByteArray(0)
        val body = bytes.toRequestBody("application/json; charset=utf-8".toMediaType())
        val call = client.newCall(okHttpRequest(request).post(body).build())
        return call.execute().toNewPipeResponse()
    }

    private fun doHead(request: Request): Response {
        val call = client.newCall(okHttpRequest(request).head().build())
        return call.execute().toNewPipeResponse()
    }

    private fun okHttpRequest(request: Request): okhttp3.Request.Builder {
        val builder = okhttp3.Request.Builder().url(request.url())
        for ((name, values) in request.headers()) {
            values.forEach { builder.header(name, it) }
        }
        return builder
    }

    private fun okhttp3.Response.toNewPipeResponse(): Response {
        val bodyText = body?.string() ?: ""
        val headers = headers.toMultimap()
        return Response(
            code,
            message ?: "",
            headers,
            bodyText,
            request.url.toString(),
        )
    }

    override fun toString(): String = "YtMusicDownloader(okhttp)"
}
