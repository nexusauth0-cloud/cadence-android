package com.cadence.source.ytmusic

import com.cadence.source.api.AuthScope
import com.cadence.source.api.SourceError
import com.cadence.source.api.UnavailableReason
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import java.io.IOException
import org.schabi.newpipe.extractor.exceptions.ExtractionException

/**
 * Maps the NewPipe Extractor's exception taxonomy onto the typed SPI [SourceError]
 * family (ADR-0002 §4). Only [SourceError] instances cross the provider boundary;
 * NewPipe/HTTP exception types never escape this module.
 *
 * NOTE: many NewPipe exceptions subclass [ContentNotAvailableException], so the
 * most specific types MUST be matched before it.
 */
internal object YtMusicErrorMapping {

    fun mapSuspending(t: Throwable, operation: String): SourceError =
        when (t) {
            is SourceError -> t
            is PaidContentException ->
                SourceError.AuthRequired(AuthScope.PLAYBACK)
            is AgeRestrictedContentException ->
                SourceError.Unavailable(UnavailableReason.AGE_RESTRICTED)
            is PrivateContentException ->
                SourceError.Unavailable(UnavailableReason.PRIVATE)
            is GeographicRestrictionException,
            is UnsupportedContentInCountryException ->
                SourceError.RegionLocked()
            // YoutubeMusicPremiumContentException is a subclass of
            // ContentNotAvailableException; distinguished structurally.
            is ContentNotAvailableException ->
                if (t.javaClass.simpleName == "YoutubeMusicPremiumContentException") {
                    SourceError.AuthRequired(AuthScope.PLAYBACK)
                } else {
                    SourceError.Unavailable(UnavailableReason.REMOVED)
                }
            is ReCaptchaException ->
                SourceError.AuthRequired(AuthScope.PLAYBACK)
            is ParsingException ->
                SourceError.InvalidResponse(context = operation, snippet = redact(t.message))
            is ExtractionException ->
                SourceError.ResolutionFailed(t)
            is IOException ->
                SourceError.NetworkUnreachable(t)
            else -> SourceError.ResolutionFailed(t)
        }

    private fun redact(message: String?): String? {
        val text = message ?: return null
        return text.take(200)
    }
}
