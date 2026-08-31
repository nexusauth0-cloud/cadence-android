package com.cadence.tests.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Spike spec section 13, checks #1/#2/#3/#7 (source level).
 * Compile-time module boundaries are the primary gate; these source scans are
 * the second line that catches accidental imports before they harden into
 * dependencies.
 */
class SourceBoundaryChecks {

    private val bannedImportsAnywhereInMain = listOf(
        "import android.",
        "import androidx.", // includes androidx.media3 / androidx.room
        "import okhttp3.",
        "import io.ktor.",
        "com.metrolist", // clean-room tripwire: no Metrolist references anywhere
    )

    /** ADR-0001: domain/model purity — these must not see Android, Room, Media3, or Keystore. */
    private val domainBannedImports = listOf(
        "import android.security.",
        "import androidx.room.",
        "import androidx.media3.",
        "import androidx.security.",
        "import javax.crypto.", // Keystore crypto primitives — only in datastore impl
    )

    /** ADR-0002: source API must not depend on concrete extraction libraries. */
    private val extractionLibraryFragments = listOf(
        "org.schabi.newpipe",
        "com.maxrave",
        "moe.rukamori",
        "com.music.innertube",
        "innertube.",
    )

    @Test
    fun `rule A - pure modules import no transport ui or android types`() {
        val pure = listOf(
            ArchEnvironment.productionModules.getValue("core:model"),
            ArchEnvironment.productionModules.getValue("core:domain"),
            ArchEnvironment.productionModules.getValue("source:api"),
        )
        val offenders = scan(pure, bannedImportsAnywhereInMain)
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule B - no production module imports test infrastructure`() {
        val offenders = scan(
            ArchEnvironment.productionModules.values.toList(),
            listOf("com.cadence.core.testing"),
        )
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule C - provider implementations leak nowhere except their own module`() {
        val offenders = mutableListOf<String>()
        for ((module, srcDir) in ArchEnvironment.productionModules) {
            if (!srcDir.isDirectory) continue
            val isProviderImpl = module == "source:demo" || module == "source:ytmusic"
            val banned = if (isProviderImpl) emptyList() else listOf("com.cadence.source.demo", "com.cadence.source.ytmusic")
            offenders += scan(listOf(srcDir), banned)
        }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule G - domain cannot depend on Room Media3 or Android Keystore`() {
        val domainModules = listOf(
            ArchEnvironment.productionModules.getValue("core:model"),
            ArchEnvironment.productionModules.getValue("core:domain"),
            ArchEnvironment.productionModules.getValue("source:api"),
        )
        val offenders = scan(domainModules, domainBannedImports)
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule H - source API cannot depend on concrete extraction libraries`() {
        val apiDir = ArchEnvironment.productionModules.getValue("source:api")
        val offenders = scan(listOf(apiDir), extractionLibraryFragments)
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule I - provider DTOs cannot leak into domain`() {
        val domainModules = listOf(
            ArchEnvironment.productionModules.getValue("core:model"),
            ArchEnvironment.productionModules.getValue("core:domain"),
        )
        // Provider-specific wire types must not appear in domain
        val offenders = scan(domainModules, listOf(
            "DemoWire", "DemoMapper", "ClasspathFixture",
            "YtMusicMapper", "YtMusicTransport", "YtMusicDownloader", "CapturedExchange",
        ))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule J - Room entities cannot leak into domain`() {
        val domainModules = listOf(
            ArchEnvironment.productionModules.getValue("core:model"),
            ArchEnvironment.productionModules.getValue("core:domain"),
            ArchEnvironment.productionModules.getValue("source:api"),
        )
        val offenders = scan(domainModules, listOf(
            "@Entity", "@Dao", "@Database",
        ))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule L - pure modules never reference Android adapter packages`() {
        val pure = listOf(
            ArchEnvironment.productionModules.getValue("core:model"),
            ArchEnvironment.productionModules.getValue("core:domain"),
            ArchEnvironment.productionModules.getValue("source:api"),
        )
        // ADR-0001: domain must never reach down into an Android adapter. The
        // adapters are consumed ONLY through core interfaces (vault, playlist,
        // player, cache), never as imports of the android:* packages.
        val offenders = scan(pure, listOf("com.cadence.android."))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule M - Android adapters stay clean of Metrolist and test infra`() {
        val offenders = scan(
            ArchEnvironment.androidModules.values.toList(),
            listOf(
                "com.metrolist", // clean-room tripwire applies repo-wide
                "com.cadence.core.testing", // adapters never link test infra
            ),
        )
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    private fun scan(dirs: Collection<File>, fragments: List<String>): List<String> =
        dirs.filter { it.isDirectory }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .flatMap { file ->
                        file.readLines()
                            .withIndex()
                            .filter { (_, line) -> line.trimStart().startsWith("import ") }
                            .filter { (_, line) -> fragments.any { line.contains(it) } }
                            .map { (idx, line) -> "${dir.name}/${file.name}:$idx+1 -> ${line.trim()}" }
                    }
            }

    private fun msg(offenders: List<String>) =
        "violations found:\n${offenders.joinToString("\n")}"
}
