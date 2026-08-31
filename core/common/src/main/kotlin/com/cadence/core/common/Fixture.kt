package com.cadence.core.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Fixture schema version — bumping is an ADR-level event (spike spec section 9). */
public const val FIXTURE_SCHEMA_VERSION: Int = 1

@Serializable
public data class FixtureTransport(
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap(),
)

/** Structured error carried by error fixtures so adapters map deterministically. */
@Serializable
public data class FixtureError(
    val type: String,
    val retryAfterSeconds: Int? = null,
    val detail: String? = null,
)

/**
 * Versioned fixture envelope. Exactly one of [body] (parsed JSON) or
 * [bodyRaw] (captured malformed bytes as string) must be present.
 * All timestamps inside fixtures are fixed strings; mappers receive injected clocks.
 */
@Serializable
public data class FixtureEnvelope(
    val schema: Int,
    val scenario: String,
    val capturedAt: String? = null,
    val transport: FixtureTransport = FixtureTransport(),
    val body: JsonElement? = null,
    @SerialName("bodyRaw") val bodyRaw: String? = null,
    val error: FixtureError? = null,
)

public class FixtureFormatException(message: String) : IllegalArgumentException(message)

public object FixtureJson {
    public val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }
}

/**
 * Deterministic classpath-based fixture loader. Fixtures are committed test
 * resources; no network access ever occurs in CI (ADR-0002 / ADR-0008).
 */
public object FixtureLoader {

    public fun read(baseResourceDir: String, relativePath: String): FixtureEnvelope {
        require(!relativePath.contains("..")) { "fixture path traversal forbidden" }
        val normalized = relativePath.trimStart('/')
        val resource = if (baseResourceDir.endsWith("/")) "$baseResourceDir$normalized"
        else "$baseResourceDir/$normalized"

        val stream = checkNotNull(FixtureLoader::class.java.classLoader.getResourceAsStream(resource)) {
            "Fixture not found on test classpath: $resource"
        }
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return decode(text)
    }

    public fun decode(text: String): FixtureEnvelope {
        val envelope = try {
            FixtureJson.json.decodeFromString(serializer(), text)
        } catch (e: Exception) {
            throw FixtureFormatException("fixture envelope unparsable: ${e.message}")
        }
        if (envelope.schema != FIXTURE_SCHEMA_VERSION) {
            throw FixtureFormatException("fixture schema ${envelope.schema} != $FIXTURE_SCHEMA_VERSION")
        }
        val hasBody = envelope.body != null
        val hasRaw = !envelope.bodyRaw.isNullOrEmpty()
        if (hasBody && hasRaw) {
            throw FixtureFormatException("fixture must contain at most one of body/bodyRaw")
        }
        return envelope
    }

    private fun serializer() = FixtureEnvelope.serializer()
}
