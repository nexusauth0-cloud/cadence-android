package com.cadence.tests.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cross-module dependency probes (Phase 5B). These scan import statements to
 * assert forbidden direction arrows that compile-time module isolation must also
 * hold (ADR-0001):
 * - the persistence layer (android:roomdb / core:database) must never reach up
 *   into the UI, Media3, or provider implementations;
 * - providers must never reference UI / playlist UI types;
 * - the UI ViewModel/screen layer must not reach repository implementations.
 *
 * The checks are intentionally substring-precise on import lines and complement
 * the binary-class probes in [CompiledArtifactChecks].
 */
class DependencyProbeChecks {

    private val roomDb = ArchEnvironment.androidModules.getValue("android:roomdb")
    private val coreDatabase = ArchEnvironment.productionModules.getValue("core:database")
    private val domain = ArchEnvironment.productionModules.getValue("core:domain")

    /** The UI's non-composition-root layers: ViewModels and Screens. */
    private val ui = ArchEnvironment.uiModules.getValue("app")
    private val uiView: java.io.File = File(ui, "java/com/cadence/app/viewmodel")
    private val uiScreen: java.io.File = File(ui, "java/com/cadence/app/screen")

    private val uiPackage = "com.cadence.app."
    private val media3 = "import androidx.media3."
    private val providerImpls = listOf("com.cadence.source.demo", "com.cadence.source.ytmusic")

    @Test
    fun `persistence layer never depends on UI`() {
        val offenders = scan(listOf(roomDb, coreDatabase), listOf(uiPackage))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `persistence layer never depends on Media3 ExoPlayer`() {
        val offenders = scan(listOf(roomDb, coreDatabase), listOf(media3))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `persistence layer never depends on provider implementations`() {
        val offenders = scan(listOf(roomDb, coreDatabase), providerImpls)
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `providers never depend on UI or playlist UI models`() {
        val providers = listOf(
            ArchEnvironment.productionModules.getValue("source:demo"),
            ArchEnvironment.productionModules.getValue("source:ytmusic"),
        )
        val offenders = scan(providers, listOf(uiPackage))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `domain layer stays free of Room entities`() {
        // Handles are pure; core:database holds the pure ports/models only.
        val offenders = scan(listOf(domain, coreDatabase), listOf("@Entity", "@Dao", "@Database"))
            .filterNot { it.contains(roomDb.path) }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `probe - ViewModels and Screens never import the Room database package`() {
        // The ViewModel/screen layers must reference only ports and pure models;
        // they must never reach the android:roomdb package (the composition root
        // + CadenceApplication legitimately wire it, so only scan non-root layers).
        val offenders = scan(listOf(uiView, uiScreen), listOf("com.cadence.android.roomdb"))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `probe - ViewModels and Screens never import the worker download implementation`() {
        // The UI offline surface must depend only on the core:domain
        // DownloadOperationsPort + use cases; the WorkManager-backed worker
        // module stays confined to the composition root (CadenceApplication).
        val offenders = scan(listOf(uiView, uiScreen), listOf("com.cadence.android.worker"))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `probe - core data never depends on download persistence (ADR-0007 tiers)`() {
        // core:data (SPI adapters) must remain decoupled from the download
        // persistence layer; download state flows only through core:domain/
        // core:database ports.
        val coreData = ArchEnvironment.productionModules.getValue("core:data")
        val offenders = scan(listOf(coreData), listOf("com.cadence.core.database.Download"))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `probe - player module never depends on download implementation`() {
        // PlayerController/PlaybackViewModel load a file:// URI transparently;
        // the player module must not import download internals or the domain
        // offline adapter.
        val player = ArchEnvironment.androidModules.getValue("android:player")
        val offenders = scan(listOf(player), listOf("com.cadence.android.worker", "OfflineFirstResolutionPort"))
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
                            .map { (idx, line) -> "${dir.name}/${file.name}:${idx + 1} -> ${line.trim()}" }
                    }
            }

    private fun msg(offenders: List<String>) =
        "violations found:\n${offenders.joinToString("\n")}"
}
