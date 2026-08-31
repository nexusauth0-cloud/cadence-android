package com.cadence.android.worker

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Opens a byte stream from a resolved source's playbackUri (section 7).
 *
 * For HTTP(S) URIs, uses OkHttp with connection/read timeouts.
 * For local file URIs, reads directly.
 *
 * The caller (DownloadExecutor) is responsible for reading chunks, reporting
 * progress, and writing to DownloadStorage.
 */
open class HttpByteSource(
    private val client: OkHttpClient = defaultClient,
) {
    companion object {
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Open a stream for reading bytes from [uri].
     * The caller must close the returned InputStream when done.
     *
     * @param uri the playbackUri from ResolvedSource (http/https/file).
     * @param headers optional non-secret HTTP request headers.
     * @return InputStream for reading, plus the total size if known.
     * @throws IOException on connection or stream errors.
     */
    @Throws(IOException::class)
    open fun open(uri: String, headers: Map<String, String> = emptyMap()): StreamInfo {
        return when {
            uri.startsWith("file://") || (!uri.startsWith("http") && uri.startsWith("/")) -> {
                openLocal(uri)
            }
            else -> openHttp(uri, headers)
        }
    }

    /**
     * Check if a URI refers to a local file.
     */
    fun isLocal(uri: String): Boolean =
        uri.startsWith("file://") || uri.startsWith("/") && !uri.startsWith("http")

    private fun openLocal(uri: String): StreamInfo {
        val path = when {
            uri.startsWith("file://") -> uri.removePrefix("file://")
            else -> uri
        }
        val file = java.io.File(path)
        if (!file.exists()) throw IOException("Local file not found: $path")
        return StreamInfo(
            stream = file.inputStream(),
            totalBytes = file.length(),
        )
    }

    private fun openHttp(uri: String, headers: Map<String, String>): StreamInfo {
        val builder = Request.Builder().url(uri)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }

        val response = client.newCall(builder.build()).execute()

        if (!response.isSuccessful) {
            response.body?.close()
            throw IOException("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw IOException("Empty response body")
        val totalBytes = body.contentLength().takeIf { it >= 0 }

        return StreamInfo(
            stream = body.byteStream(),
            totalBytes = totalBytes,
            onClose = { response.close() },
        )
    }
}

/**
 * Result of opening a stream: the InputStream plus known size.
 * [onClose] must be called after the stream is consumed to release resources.
 */
data class StreamInfo(
    val stream: InputStream,
    val totalBytes: Long?,
    val onClose: (() -> Unit)? = null,
)
