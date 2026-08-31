package com.cadence.tests.e2e

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Layering guard executed from INSIDE the composition module: the E2E sources
 * must reference only domain/model/api/testing types. Provider implementations,
 * transport stacks and Android must be absent even as imports.
 */
class LayeringGuardianTest {

    private val rootDir: String = System.getProperty("cadence.root")
        ?: error("cadence.root system property not set (see tests/e2e build.gradle.kts)")

    private val bannedFragments = listOf(
        "com.cadence.source.demo",
        "com.cadence.source.ytmusic",
        "com.metrolist",
        "androidx.media3",
        "import android.",
        "okhttp3.",
        "io.ktor.",
    )

    @Test
    fun `e2e sources stay on ports and fakes only`() {
        val dir = File(rootDir, "tests/e2e/src")
        assertTrue(dir.isDirectory, "e2e source dir missing at $dir")
        val offenders = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .withIndex()
                    .filter { (_, line) -> line.trimStart().startsWith("import ") || line.trimStart().startsWith("package ")}
                    .filter { (_, line) -> bannedFragments.any { line.contains(it) } }
                    .map { (idx, line) -> "${file.relativeTo(File(rootDir))}:$idx+1 $line" }
            }
            .toList()
        assertTrue(offenders.isEmpty(), "forbidden references in e2e module:\n${offenders.joinToString("\n")}")
    }
}
