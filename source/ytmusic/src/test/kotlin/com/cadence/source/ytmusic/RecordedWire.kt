package com.cadence.source.ytmusic

import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.File
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val RecordedWireJson: Json = Json { ignoreUnknownKeys = true }

@Serializable
data class RecordedExchangeDto(
    val method: String,
    val url: String,
    @SerialName("requestBodyB64") val requestBodyB64: String? = null,
    val status: Int,
    @SerialName("responseBody") val responseBody: String = "",
    val headers: Map<String, List<String>> = emptyMap(),
)

/** A captured wire exchange. */
data class CapturedExchange(
    val method: String,
    val url: String,
    val requestBodyB64: String?,
    val status: Int,
    val responseBody: String,
    val headers: Map<String, List<String>>,
)

/**
 * Records every request/response the real NewPipe extraction issues so fixtures
 * can be authored from a genuine live run, then replayed hermetically in CI.
 */
internal class RecordedWire {
    private val exchanges = ArrayList<CapturedExchange>()

    fun capture(request: Request, response: Response) {
        exchanges += CapturedExchange(
            method = request.httpMethod(),
            url = request.url(),
            requestBodyB64 = request.dataToSend()?.let { Base64.getEncoder().encodeToString(it) },
            status = response.responseCode(),
            responseBody = response.responseBody() ?: "",
            headers = response.responseHeaders() ?: emptyMap(),
        )
    }

    fun exchanges(): List<CapturedExchange> = exchanges

    fun writeTo(file: File) {
        val sb = StringBuilder("[\n")
        for ((i, e) in exchanges.withIndex()) {
            sb.append("  {\n")
            sb.append("    \"method\": \"${e.method}\",\n")
            sb.append("    \"url\": \"${e.url.replace("\\", "\\\\").replace("\"", "\\\"")}\",\n")
            sb.append("    \"requestBodyB64\": ${e.requestBodyB64?.let { "\"$it\"" } ?: "null"},\n")
            sb.append("    \"status\": ${e.status},\n")
            sb.append("    \"responseBody\": ${jsonString(e.responseBody)},\n")
            sb.append("    \"headers\": ${jsonHeaders(e.headers)}\n")
            sb.append("  }")
            if (i < exchanges.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")
        file.absoluteFile.parentFile?.mkdirs()
        file.writeText(sb.toString())
    }

    /** Parses a recorded wire file back into exchanges. */
    fun loadFrom(file: File): List<CapturedExchange> {
        val dtos = RecordedWireJson.decodeFromString<List<RecordedExchangeDto>>(file.readText())
        return dtos.map { dto ->
            CapturedExchange(
                method = dto.method,
                url = dto.url,
                requestBodyB64 = dto.requestBodyB64,
                status = dto.status,
                responseBody = dto.responseBody,
                headers = dto.headers,
            )
        }
    }

    private fun jsonString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

    private fun jsonHeaders(headers: Map<String, List<String>>): String {
        if (headers.isEmpty()) return "{}"
        val entries = headers.entries.joinToString(", ") { (k, v) ->
            "${jsonString(k)}: [${v.joinToString(", ") { jsonString(it) }}]"
        }
        return "{ $entries }"
    }
}
