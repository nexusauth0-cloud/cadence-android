package com.cadence.source.ytmusic

import com.cadence.source.api.AuthScope
import com.cadence.source.api.SourceError
import com.cadence.source.api.UnavailableReason
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Deterministic unit tests of [YtMusicErrorMapping]: the NewPipe exception
 * taxonomy is mapped onto the typed SPI [SourceError] family with the more
 * specific subclasses winning over the base [ContentNotAvailableException].
 * No network, no extractor — pure mapping.
 */
class YtMusicErrorMappingTest {

    private fun map(t: Throwable) = YtMusicErrorMapping.mapSuspending(t, "test.op")

    @Test
    fun `existing SourceError passes through unchanged`() {
        val rate = SourceError.RateLimited(null)
        assertSame(rate, map(rate))
        val unavail = SourceError.Unavailable(UnavailableReason.UNKNOWN)
        assertSame(unavail, map(unavail))
    }

    @Test
    fun `paid and premium content map to auth-required playback`() {
        assertIs<SourceError.AuthRequired>(map(PaidContentException("paid")))
            .let { assertEquals(AuthScope.PLAYBACK, it.scope) }
        // YoutubeMusicPremiumContentException is distinguished structurally (class name),
        // not by importable Kotlin reference (extends ContentNotAvailableException).
        assertIs<SourceError.AuthRequired>(map(YoutubeMusicPremiumContentException()))
            .let { assertEquals(AuthScope.PLAYBACK, it.scope) }
    }

    @Test
    fun `age-restricted and private content map to unavailable`() {
        assertIs<SourceError.Unavailable>(map(AgeRestrictedContentException("age")))
            .let { assertEquals(UnavailableReason.AGE_RESTRICTED, it.reason) }
        assertIs<SourceError.Unavailable>(map(PrivateContentException("private")))
            .let { assertEquals(UnavailableReason.PRIVATE, it.reason) }
    }

    @Test
    fun `generic unavailable content maps to removed`() {
        assertIs<SourceError.Unavailable>(map(ContentNotAvailableException("removed")))
            .let { assertEquals(UnavailableReason.REMOVED, it.reason) }
    }

    @Test
    fun `geo and country restrictions map to region-locked`() {
        assertIs<SourceError.RegionLocked>(map(GeographicRestrictionException("geo")))
        assertIs<SourceError.RegionLocked>(map(UnsupportedContentInCountryException("unsupported")))
    }

    @Test
    fun `captcha maps to auth-required playback`() {
        assertIs<SourceError.AuthRequired>(map(ReCaptchaException("url", "msg")))
            .let { assertEquals(AuthScope.PLAYBACK, it.scope) }
    }

    @Test
    fun `parsing failure maps to invalid-response with bounded redacted snippet`() {
        val long = "x".repeat(500)
        val err = assertIs<SourceError.InvalidResponse>(map(ParsingException(long)))
        assertEquals("test.op", err.context)
        assertTrue((err.snippet ?: "").length <= 200, "snippet must be bounded/redacted")
        assertEquals(long.take(200), err.snippet)
    }

    @Test
    fun `generic extraction failure maps to resolution-failed`() {
        assertIs<SourceError.ResolutionFailed>(map(ExtractionException("boom")))
    }

    @Test
    fun `io failure maps to network-unreachable`() {
        assertIs<SourceError.NetworkUnreachable>(map(IOException("socket")))
    }

    @Test
    fun `unrecognized throwable maps to resolution-failed`() {
        assertIs<SourceError.ResolutionFailed>(map(IllegalStateException("unexpected")))
    }
}
