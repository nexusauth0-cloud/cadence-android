package com.cadence.app

import com.cadence.app.di.AppDemoConfig
import com.cadence.app.di.AppGraph
import com.cadence.core.model.ProviderId
import com.cadence.source.api.ProviderScope
import com.cadence.source.demo.DemoProvider
import com.cadence.source.ytmusic.YtMusicProvider
import com.cadence.source.ytmusic.YtMusicTransport
import com.cadence.core.testing.PlayerControllerFake
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Composition-root provider selection (ADR-0002 single-source default).
 * Production [AppGraph] defaults register the real YT Music provider plus the
 * demo provider and pin the default search scope to YT Music; the test config
 * ([AppDemoConfig]) registers only the hermetic demo provider pinned to demo.
 * These assertions are hermetic: constructing either graph performs NO network
 * (NewPipe init is local; the OkHttp client is lazily built, never used).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGraphProviderConfigTest {

    private val demoId: ProviderId = DemoProvider.ID
    private val ytMusicId: ProviderId = YtMusicProvider.ID

    @Test
    fun `production default graph selects YT Music as single default source`() {
        val graph = AppGraph(playerController = PlayerControllerFake())

        assertEquals(setOf(demoId, ytMusicId), graph.registeredProviders)
        assertEquals(ProviderScope.Single(ytMusicId), graph.searchDefaultScope)
    }

    @Test
    fun `demo graph selects demo as single default source`() {
        val graph = AppGraph(
            playerController = PlayerControllerFake(),
            providers = AppDemoConfig.providers(),
            defaultScope = AppDemoConfig.scope(),
        )

        assertEquals(setOf(demoId), graph.registeredProviders)
        assertEquals(ProviderScope.Single(demoId), graph.searchDefaultScope)
    }

    @Test
    fun `registry holds multiple providers with distinct ids`() {
        // Mirror of a production-style graph: both providers coexist.
        val demo = AppDemoConfig.providers().single()
        val combined = AppGraph(
            playerController = PlayerControllerFake(),
            providers = AppDemoConfig.providers() + YtMusicProvider(YtMusicTransport.okHttp()),
            defaultScope = ProviderScope.Single(ytMusicId),
        )
        assertEquals(setOf(demoId, ytMusicId), combined.registeredProviders)
        assertEquals(2, combined.registeredProviders.size)
    }
}
