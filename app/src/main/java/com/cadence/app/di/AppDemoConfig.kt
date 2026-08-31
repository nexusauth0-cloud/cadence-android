package com.cadence.app.di

import com.cadence.source.api.ProviderScope
import com.cadence.source.api.SourceProvider
import com.cadence.source.demo.DemoProvider
import java.time.Clock

/**
 * Reusable, explicitly-named DEMO provider configuration for the composition
 * root. Used by the [AppGraph] default configuration AND by hermetic tests that
 * build a DIFFERENT [AppGraph] with the demo (fake) provider instead of the real
 * YT Music provider.
 *
 * This is NOT environment detection: callers opt in by name. Tests import
 * [AppDemoConfig.providers] + [AppDemoConfig.scope] explicitly and never rely on
 * the production default. [AppGraph] itself has no notion of "test".
 */
object AppDemoConfig {

    fun providers(clock: Clock = Clock.systemUTC()): List<SourceProvider> =
        listOf(
            DemoProvider(
                transport = MemoryFixtureTransport(
                    search = listOf(DemoCatalog.SEARCH_SLUG to DemoCatalog.searchPage1),
                    resolve = DemoCatalog.resolveByNativeId,
                ),
                clock = clock,
            ),
        )

    /** Single-source demo scope, the explicit opposite of the production default. */
    fun scope(): ProviderScope = ProviderScope.Single(DemoProvider.ID)
}
