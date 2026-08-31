package com.cadence.source.ytmusic

import com.cadence.core.model.TrackRef
import com.cadence.source.api.SourceError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Deterministic unit tests of [YtMusicMapper]'s pure mapping logic that does not
 * require constructing NewPipe objects (the rest of the pipeline is covered
 * hermetically by [YtMusicProviderTest]).
 */
class YtMusicMapperTest {

    @Test
    fun `videoIdFromUrl extracts native id from v query param`() {
        assertEquals(
            "JPcP_I8wNeQ",
            YtMusicMapper.videoIdFromUrl("https://music.youtube.com/watch?v=JPcP_I8wNeQ"),
        )
        assertEquals(
            "j7HXK399r3g",
            YtMusicMapper.videoIdFromUrl("https://www.youtube.com/watch?v=j7HXK399r3g&list=xyz"),
        )
        assertEquals(
            "AbC12345",
            YtMusicMapper.videoIdFromUrl("https://youtu.be/x?a=1&v=AbC12345"),
        )
    }

    @Test
    fun `videoIdFromUrl tolerates blanks, undersized and missing ids`() {
        assertNull(YtMusicMapper.videoIdFromUrl(null))
        assertNull(YtMusicMapper.videoIdFromUrl(""))
        assertNull(YtMusicMapper.videoIdFromUrl("https://music.youtube.com/watch?v=abc")) // <6 chars
        assertNull(YtMusicMapper.videoIdFromUrl("https://music.youtube.com/watch"))
    }

    @Test
    fun `resolvedSource with no playable streams yields ResolutionFailed`() {
        val ref = TrackRef(YtMusicMapper.ID, "JPcP_I8wNeQ")
        assertFailsWith<SourceError.ResolutionFailed> {
            YtMusicMapper.resolvedSource(
                ref = ref,
                title = "Alpha Anthem",
                artist = "Beta Band",
                audioStreams = emptyList(),
            )
        }
    }
}
