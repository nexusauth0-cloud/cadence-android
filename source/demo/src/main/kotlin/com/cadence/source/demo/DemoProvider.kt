package com.cadence.source.demo

import com.cadence.core.common.FixtureEnvelope
import com.cadence.core.common.FixtureFormatException
import com.cadence.core.common.SourceLog
import com.cadence.core.model.ProviderId
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import com.cadence.source.api.Capability
import com.cadence.source.api.PageCursor
import com.cadence.source.api.PagedResult
import com.cadence.source.api.ProviderAuth
import com.cadence.source.api.SearchQuery
import com.cadence.source.api.SourceError
import com.cadence.source.api.SourceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Clock

/**
 * Adapter-shaped synthetic provider. Proves the full adapter pipeline
 * (envelope -> wire DTOs -> mapper -> domain models, typed error mapping)
 * against committed fixtures without any real backend or extraction library
 * (spike spec sections 3/9; real-provider integration deferred per Q1/Q2).
 */
class DemoProvider(
    private val transport: DemoTransport,
    private val clock: Clock,
    override val id: ProviderId = DemoMapper.DEMO_ID,
) : SourceProvider {

    override val capabilities: Set<Capability> = setOf(Capability.SEARCH, Capability.RESOLVE)

    private val _authState = MutableStateFlow<ProviderAuth?>(null)
    override val authState: StateFlow<ProviderAuth?> = _authState

    fun login(subject: String) {
        _authState.value = ProviderAuth(subject)
    }

    override suspend fun search(query: SearchQuery, cursor: PageCursor?): PagedResult<Track> {
        val slug = slugify(query.text)
        val page = pageFromCursor(cursor)
        val path = "search/happy/$slug/page-%02d.json".format(page)
        val envelope = fetchGuarded(path)
        throwIfError(envelope, path)
        val doc = parseBody<DemoSearchDoc>(envelope, path)
        return PagedResult(
            items = doc.results.mapNotNull(DemoMapper::track),
            nextCursor = doc.next?.takeIf { it > 0 }?.let { PageCursor("${id.value}:$it") },
        )
    }

    override suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource {
        val nativeId = sanitizeNativeId(ref.nativeId)
        val path = "resolve/happy/$nativeId.json"
        val envelope = fetchGuarded(path)
        throwIfError(envelope, path)
        val doc = parseBody<DemoResolveDoc>(envelope, path)
        return DemoMapper.resolvedSource(nativeId, doc, clock)
    }

    private suspend fun fetchGuarded(path: String): FixtureEnvelope = try {
        transport.fetch(path)
    } catch (e: FixtureFormatException) {
        throw SourceError.InvalidResponse(path, SourceLog.redactSnippet(e.message ?: "unparsable envelope"))
    }

    private fun throwIfError(envelope: FixtureEnvelope, path: String) {
        DemoErrorMapping.errorFor(envelope, path)?.let { throw it }
    }

    private inline fun <reified T> parseBody(envelope: FixtureEnvelope, path: String): T {
        val element = envelope.body ?: throw SourceError.InvalidResponse(
            context = path,
            snippet = SourceLog.redactSnippet(envelope.bodyRaw ?: "empty body"),
        )
        return try {
            DemoJson.decodeFromJsonElement<T>(element)
        } catch (e: SerializationException) {
            throw SourceError.InvalidResponse(path, SourceLog.redactSnippet(element.toString()))
        } catch (e: IllegalArgumentException) {
            throw SourceError.InvalidResponse(path, SourceLog.redactSnippet(element.toString()))
        }
    }

    private fun pageFromCursor(cursor: PageCursor?): Int =
        cursor?.token?.removePrefix("${id.value}:")?.toIntOrNull()?.coerceIn(1, 99) ?: 1

    private fun sanitizeNativeId(nativeId: String): String =
        if (NATIVE_ID.matches(nativeId)) nativeId else throw SourceError.Unsupported("malformed demo nativeId")

    companion object {
        private val NATIVE_ID = Regex("[A-Za-z0-9._-]{1,128}")
        const val MAX_SLUG = 40
        val ID: ProviderId get() = DemoMapper.DEMO_ID

        fun slugify(text: String): String =
            text.lowercase()
                .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
                .joinToString("")
                .replace(Regex("-{2,}"), "-")
                .trim('-')
                .take(MAX_SLUG)
                .ifBlank { "query" }
    }
}
