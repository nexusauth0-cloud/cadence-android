package com.cadence.tests.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 4B app-shell boundary enforcement. The `:app` module is a NEW top-level
 * UI layer; it consumes the existing modules but must not let provider or
 * adapter implementation details leak into screens/view models. Provider
 * assembly is confined to the composition root (`com.cadence.app.di`).
 */
class AppBoundaryChecks {

    private val appSrc: File = ArchEnvironment.uiModules.getValue("app")

    private val compositionRootSegment = "com${File.separator}cadence${File.separator}app${File.separator}di"

    @Test
    fun `rule N - app main is free of Metrolist and test infrastructure`() {
        val offenders = scan(appSrc, listOf("com.metrolist", "com.cadence.core.testing"))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule O - media3 stays confined to android player module`() {
        val offenders = scan(appSrc, listOf("import androidx.media3"))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule P - provider implementations only live in the composition root`() {
        val offenders = scan(appSrc, listOf("com.cadence.source.demo", "com.cadence.source.ytmusic"))
            .filterNot { it.contains(compositionRootSegment) }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule Q - data adapters only live in the composition root`() {
        // Package-prefix match (trailing dot) so `com.cadence.core.database.*`
        // models are NOT mistaken for `com.cadence.core.data` adapters.
        val offenders = scan(appSrc, listOf("com.cadence.core.data."))
            .filterNot { it.contains(compositionRootSegment) }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule R - UI never imports Room, DAOs, or entity implementations`() {
        // ViewModels/screens must stay decoupled from the Room persistence
        // layer: no androidx.room types (which cover @Entity/@Dao/@Database)
        // beyond the composition root.
        val offenders = scan(appSrc, listOf("import androidx.room."))
            .filterNot { it.contains(compositionRootSegment) }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule S - repository interfaces only live in the composition root`() {
        val offenders = scan(appSrc, listOf(
            "com.cadence.core.database.PlaylistRepository",
            "com.cadence.core.database.LibraryRepository",
        )).filterNot { it.contains(compositionRootSegment) }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule T - CredentialVault never leaks into the UI`() {
        val offenders = scan(appSrc, listOf("CredentialVault"))
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule U - no concrete player wires into the UI beyond composition root`() {
        // ExoPlayer/media3 stays confined to the android:player module and the
        // composition root; screens/ViewModels must consume PlayerController only.
        val offenders = scan(appSrc, listOf("import androidx.media3."))
            .filterNot { it.contains(compositionRootSegment) }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    // ── Phase 5C download/offline boundaries (section 22) ──────────────

    /**
     * The process composition root comprises the `di` package and the
     * Application class (CadenceApplication.kt) — the ONLY places Android-only
     * download wiring may live.
     */
    private fun isCompositionRoot(rel: String): Boolean {
        val normalized = rel.replace(File.separatorChar, '/')
        return normalized.contains("com/cadence/app/di/") ||
            normalized.endsWith("CadenceApplication.kt")
    }

    @Test
    fun `rule V - UI never imports the download worker module`() {
        // Screens/ViewModels depend on DownloadOperationsPort + use cases in
        // core:domain, never on the WorkManager-backed worker implementation.
        val offenders = scan(appSrc, listOf("com.cadence.android.worker"))
            .filterNot { isCompositionRoot(it.substringBefore(':')) }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule W - UI never touches filesystem paths or cache tiers`() {
        // The truthful offline surface must remain filesystem-agnostic: no raw
        // File IO and no cache-tier internals leak into screens/view models.
        val offenders = scan(appSrc, listOf(
            "java.io.File",
            "com.cadence.android.diskcache",
            "com.cadence.core.cache.",
        )).filterNot { isCompositionRoot(it.substringBefore(':')) }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule X - UI never imports OkHttp`() {
        // Networking is confined to source providers and the worker byte-source;
        // it must never appear in the app UI layer.
        val offenders = scan(appSrc, listOf("okhttp3."))
            .filterNot { isCompositionRoot(it.substringBefore(':')) }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    @Test
    fun `rule Y - download internals never leak past the composition root`() {
        // DownloadWorker/DownloadStorage/DownloadExecutor are storage/execution
        // internals in the android:worker module — the UI depends on core:domain
        // ports/types only. Scanning fully-qualified worker imports avoids false
        // positives on domain types like DownloadStorageReport/DownloadBuffer.
        val offenders = scan(appSrc, listOf(
            "com.cadence.android.worker.DownloadWorker",
            "com.cadence.android.worker.DownloadStorage",
            "com.cadence.android.worker.DownloadExecutor",
            "com.cadence.android.worker.HttpByteSource",
            "com.cadence.android.worker.DownloadService",
        )).filterNot { isCompositionRoot(it.substringBefore(':')) }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    /**
     * Phase 5D §5: screens/components must never import the `core:media` layer
     * (which wraps Media3/ExoPlayer). Playback reaches the UI ONLY through the
     * presentation contract ([com.cadence.app.viewmodel.PlayerUiState]) — the
     * `viewmodel` package is the single adapter boundary that depends on
     * core:media, and is excluded from this scan.
     */
    @Test
    fun `rule Z - screens never import the player module`() {
        val offenders = scan(appSrc, listOf("import com.cadence.core.media."))
            .filterNot { it.contains("app${File.separator}viewmodel") }
            .filterNot { it.contains(compositionRootSegment) }
            .filterNot { it.contains("CadenceApplication") }
        assertTrue(offenders.isEmpty(), msg(offenders))
    }

    private fun scan(dir: File, fragments: List<String>): List<String> =
        if (!dir.isDirectory) emptyList()
        else dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .withIndex()
                    .filter { (_, line) -> line.trimStart().startsWith("import ") }
                    .filter { (_, line) -> fragments.any { line.contains(it) } }
                    .map { (idx, line) -> "${relative(file)}:${idx + 1} -> ${line.trim()}" }
            }
            .toList()

    private fun relative(file: File): String = file.relativeTo(ArchEnvironment.rootDir).path

    private fun msg(offenders: List<String>) =
        "violations found:\n${offenders.joinToString("\n")}"
}
