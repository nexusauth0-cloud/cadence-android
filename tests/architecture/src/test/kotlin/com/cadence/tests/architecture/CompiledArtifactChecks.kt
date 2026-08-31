package com.cadence.tests.architecture

import java.net.URLClassLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spike spec section 13, check #8 (bytecode/reflection level — strong gate).
 * Runs against the COMPILED artifacts of production modules, not source text.
 */
class CompiledArtifactChecks {

    @Test
    fun `rule D - every Throwable in source-api extends the sealed SourceError family`() {
        val loader = URLClassLoader(
            ArchEnvironment.artifacts().map { it.toURI().toURL() }.toTypedArray(),
            ClassLoader.getPlatformClassLoader(),
        )
        val apiClasses = ArchEnvironment.artifacts()
            .flatMap { ArchEnvironment.classBytesFrom(it) }
            .map { (name, _) -> name }
            .filter { it.startsWith("com/cadence/source/api/") && it.endsWith(".class") }
            .map { it.removeSuffix(".class").replace('/', '.') }
            .distinct()

        val sourceErrorClass = Class.forName("com.cadence.source.api.SourceError", false, loader)
        assertEquals(RuntimeException::class.java, sourceErrorClass.superclass)
        assertTrue(apiClasses.isNotEmpty(), "source:api classes missing from scanned artifacts")

        var throwables = 0
        for (className in apiClasses) {
            // Nested SourceError subtypes compile to SourceError$Xxx — they MUST
            // be audited, so no blanket $ filter here.
            val clazz = try {
                Class.forName(className, false, loader)
            } catch (_: Throwable) {
                continue // exotic synthetic entries that reference absent partners
            }
            if (!Throwable::class.java.isAssignableFrom(clazz)) continue
            throwables++
            assertTrue(
                sourceErrorClass.isAssignableFrom(clazz),
                "$className is a Throwable but outside the sealed SourceError family",
            )
        }
        assertTrue(throwables >= 10, "expected the full SourceError family, found only $throwables")
    }

    @Test
    fun `rule E - no runBlocking or Thread-sleep references in compiled production code`() {
        val offenders = mutableListOf<String>()
        for (artifact in ArchEnvironment.artifacts()) {
            for ((entryName, bytes) in ArchEnvironment.classBytesFrom(artifact)) {
                if (ArchEnvironment.containsAscii(bytes, "runBlocking")) {
                    offenders += "${artifact.name}:$entryName references runBlocking"
                }
                if (ArchEnvironment.containsAscii(bytes, "java/lang/Thread") &&
                    ArchEnvironment.containsAscii(bytes, "sleep")
                ) {
                    offenders += "${artifact.name}:$entryName references Thread.sleep"
                }
            }
        }
        assertEquals(true, offenders.isEmpty(), "blocking constructs in production artifacts: $offenders")
    }

    @Test
    fun `rule F - file size budget holds at 800 lines per Kotlin file`() {
        val offenders = ArchEnvironment.allKtFilesUnderRoot()
            .map { it to it.readLines().size }
            .filter { (_, lines) -> lines > 800 }
            .map { (file, lines) -> "${file.relativeTo(ArchEnvironment.rootDir)}: $lines lines" }
        assertTrue(offenders.isEmpty(), "file-size budget exceeded:\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `rule K - domain artifacts contain no Media3 Room or Keystore references`() {
        val domainModules = listOf("core:model", "core:domain", "source:api")
        val offenders = mutableListOf<String>()
        for (artifact in ArchEnvironment.artifacts()) {
            val moduleName = artifact.name
            if (domainModules.none { moduleName.contains(it.replace(":", "-")) || moduleName.contains(it.substringAfter(":")) }) continue
            for ((entryName, bytes) in ArchEnvironment.classBytesFrom(artifact)) {
                for (forbidden in listOf("media3", "Room", "Keystore", "androidx.room", "javax.crypto")) {
                    if (ArchEnvironment.containsAscii(bytes, forbidden)) {
                        offenders += "$moduleName:$entryName references $forbidden"
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "domain artifacts contain forbidden references: $offenders")
    }

    @Test
    fun `sanity - artifacts actually resolved`() {
        val files = ArchEnvironment.artifacts()
        assertTrue(files.size >= 10, "expected >=10 production artifacts, got ${files.size}: ${files.map { it.name }}")
    }
}
