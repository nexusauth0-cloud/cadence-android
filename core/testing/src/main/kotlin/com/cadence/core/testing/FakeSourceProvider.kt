package com.cadence.core.testing

import com.cadence.core.model.ArtistRef
import com.cadence.core.model.OpaquePayload
import com.cadence.core.model.ProviderId
import com.cadence.core.model.QualityPref
import com.cadence.core.model.ResolvedSource
import com.cadence.core.model.ResolutionHints
import com.cadence.core.model.SourceMetadata
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import com.cadence.source.api.AuthScope
import com.cadence.source.api.Capability
import com.cadence.source.api.PageCursor
import com.cadence.source.api.PagedResult
import com.cadence.source.api.ProviderAuth
import com.cadence.source.api.SearchQuery
import com.cadence.source.api.SourceError
import com.cadence.source.api.SourceProvider
import com.cadence.source.api.UnavailableReason
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private val SCENARIO_PREFIXES = listOf(
    ScenarioKeys.OK,
    ScenarioKeys.UNAVAILABLE,
    ScenarioKeys.RATE_LIMITED,
    ScenarioKeys.AUTH_FAILED,
    ScenarioKeys.MALFORMED,
    ScenarioKeys.CANCELLED_IMMEDIATELY,
    ScenarioKeys.DELAYED,
)

/**
 * Scriptable provider implementing every scenario of spike spec section 10.
 * Deterministic under virtual time: delays run on the caller's dispatcher
 * (runTest's TestDispatcher), expiry uses an injected [Clock] (MutableClock in tests).
 */
class FakeSourceProvider(
    private val clock: Clock,
    override val id: ProviderId = GoldenFixtures.DEMO,
) : SourceProvider {

    private val _authState = MutableStateFlow<ProviderAuth?>(null)
    override val authState: StateFlow<ProviderAuth?> = _authState

    override val capabilities: Set<Capability> = setOf(Capability.SEARCH, Capability.RESOLVE)

    private val searchCalls = AtomicInteger(0)
    private val resolveCalls = Collections.synchronizedMap(mutableMapOf<String, Int>())
    private val rateLimitedSeen = Collections.synchronizedSet(mutableSetOf<String>())
    val sentHints = Collections.synchronizedMap(mutableMapOf<String, ResolutionHints>())
    private val activeWork = AtomicInteger(0)

    val totalSearchCalls: Int get() = searchCalls.get()
    fun resolveCountFor(nativeId: String): Int = resolveCalls[nativeId] ?: 0
    fun hintsSentFor(nativeId: String): ResolutionHints? = sentHints[nativeId]
    fun activeWorkCount(): Int = activeWork.get()

    fun login(subject: String = "test-user") {
        _authState.value = ProviderAuth(subject)
    }

    fun logout() {
        _authState.value = null
    }

    override suspend fun search(query: SearchQuery, cursor: PageCursor?): PagedResult<Track> {
        searchCalls.incrementAndGet()
        return if (cursor == null) {
            PagedResult(items = listOf(canonicalTrack(query.text)), nextCursor = PageCursor("${id.value}:page-2"))
        } else {
            secondPage(query.text)
        }
    }

    /** Deterministic continuation page for a query. */
    fun secondPage(queryText: String): PagedResult<Track> = PagedResult(
        items = listOf(
            Track(
                ref = TrackRef(id, "$queryText-b-side"),
                title = "${queryText.replaceFirstChar { it.uppercase() }} B-Side",
                artists = listOf(ArtistRef(GoldenFixtures.ALPHA_ARTIST)),
                durationMs = 180_000,
            ),
        ),
    )

    /** Canonical track produced by [search]; 'alpha' yields the shared golden object. */
    fun canonicalTrack(text: String): Track =
        if (text == "alpha") {
            GoldenFixtures.alphaTrack()
        } else {
            Track(
                ref = TrackRef(id, "$text-track"),
                title = text.replaceFirstChar { it.uppercase() },
                artists = listOf(ArtistRef("Fixture Artist")),
                durationMs = 120_000,
            )
        }

    override suspend fun resolve(ref: TrackRef, hints: ResolutionHints): ResolvedSource {
        resolveCalls.merge(ref.nativeId, 1, Int::plus)
        sentHints[ref.nativeId] = hints
        activeWork.incrementAndGet()
        try {
            return resolveScripted(ref.nativeId, hints)
        } finally {
            activeWork.decrementAndGet()
        }
    }

    private suspend fun resolveScripted(nativeId: String, hints: ResolutionHints): ResolvedSource =
        when {
            nativeId == GoldenFixtures.ALPHA_REF.nativeId || nativeId.startsWith(ScenarioKeys.OK) ->
                resolvedOk(strip(nativeId), hints)

            nativeId.startsWith(ScenarioKeys.CANCELLED_IMMEDIATELY) -> awaitCancellation()

            nativeId.startsWith(ScenarioKeys.DELAYED) -> {
                val ms = ScenarioKeys.delayMillis(nativeId)
                    ?: throw SourceError.Unsupported("malformed delayed key '$nativeId'")
                delay(ms)
                resolvedOk(strip(nativeId), hints)
            }

            nativeId.startsWith(ScenarioKeys.UNAVAILABLE) ->
                throw SourceError.Unavailable(UnavailableReason.REMOVED)

            nativeId.startsWith(ScenarioKeys.RATE_LIMITED) -> {
                if (rateLimitedSeen.add(nativeId)) {
                    throw SourceError.RateLimited(kotlin.time.Duration.parse("60s"))
                }
                resolvedOk(strip(nativeId), hints)
            }

            nativeId.startsWith(ScenarioKeys.AUTH_FAILED) -> {
                if (_authState.value == null) throw SourceError.AuthRequired(AuthScope.ACCOUNT)
                resolvedOk(strip(nativeId), hints)
            }

            nativeId.startsWith(ScenarioKeys.MALFORMED) ->
                throw SourceError.InvalidResponse(context = "fake.malformed", snippet = null)

            else -> throw SourceError.Unsupported("no scripted scenario for '$nativeId'")
        }

    private fun resolvedOk(strippedNativeId: String, hints: ResolutionHints): ResolvedSource {
        val label = buildString {
            append("demo-medium")
            if (hints.qualityPref != QualityPref.AUTO) {
                append('-').append(hints.qualityPref.name.lowercase())
            }
        }
        return ResolvedSource(
            ref = TrackRef(id, strippedNativeId),
            playbackUri = "https://fixture.demo/stream/$strippedNativeId?sig=tok",
            resolutionId = resolutionIdFor(TrackRef(id, strippedNativeId)),
            expiresAt = clock.instant().plusSeconds(GoldenFixtures.RESOLUTION_TTL_SECONDS),
            renewal = OpaquePayload(id, "renew:$strippedNativeId".toByteArray()),
            httpHeaders = emptyMap(),
            metadata = defaultMetadata(),
            renditionLabel = label,
        )
    }

    /**
     * Strips the scenario prefix; for DELAYED also strips `<millis>-`.
     * `ok/alpha-track` stays `alpha-track`; `delayed/250-x` becomes `x`.
     */
    private fun strip(nativeId: String): String {
        val stripped = SCENARIO_PREFIXES.firstOrNull { nativeId.startsWith(it) }
            ?.let { nativeId.removePrefix(it) }
            ?: nativeId
        return if (nativeId.startsWith(ScenarioKeys.DELAYED)) stripped.substringAfter('-') else stripped
    }

    companion object {
        const val TTL_SECONDS = GoldenFixtures.RESOLUTION_TTL_SECONDS

        fun resolutionIdFor(ref: TrackRef): String =
            UUID.nameUUIDFromBytes("${ref.providerId.value}/${ref.nativeId}".toByteArray()).toString()

        fun defaultMetadata(): SourceMetadata = SourceMetadata(
            container = "mp4",
            codecs = listOf("mp4a.40.2"),
            bitrateKbps = 192,
            sampleRateHz = 44_100,
            loudnessDb = -7.2,
            perceptualLoudnessDb = -6.8,
            sizeBytes = 5_400_000,
        )
    }
}
