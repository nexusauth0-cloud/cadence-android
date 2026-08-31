package com.cadence.tests.architecture

import java.io.File
import java.util.zip.ZipFile

/** Shared access to project layout and resolved production artifacts. */
object ArchEnvironment {

    val rootDir: File = System.getProperty("cadence.root")?.let(::File)
        ?: error("cadence.root system property not set")

    /** Production module name -> src/main/kotlin dir. */
    val productionModules: Map<String, File> = mapOf(
        "core:model" to File(rootDir, "core/model/src/main/kotlin"),
        "core:common" to File(rootDir, "core/common/src/main/kotlin"),
        "source:api" to File(rootDir, "source/api/src/main/kotlin"),
        "core:domain" to File(rootDir, "core/domain/src/main/kotlin"),
        "core:data" to File(rootDir, "core/data/src/main/kotlin"),
        "core:datastore" to File(rootDir, "core/datastore/src/main/kotlin"),
        "core:database" to File(rootDir, "core/database/src/main/kotlin"),
        "core:media" to File(rootDir, "core/media/src/main/kotlin"),
        "core:cache" to File(rootDir, "core/cache/src/main/kotlin"),
        "core:testing" to File(rootDir, "core/testing/src/main/kotlin"),
        "source:demo" to File(rootDir, "source/demo/src/main/kotlin"),
        "source:ytmusic" to File(rootDir, "source/ytmusic/src/main/kotlin"),
    )

    /** Android adapter modules (Phase 3): name -> src/main dir. */
    val androidModules: Map<String, File> = mapOf(
        "android:vaultstore" to File(rootDir, "android/vaultstore/src/main"),
        "android:roomdb" to File(rootDir, "android/roomdb/src/main"),
        "android:player" to File(rootDir, "android/player/src/main"),
        "android:diskcache" to File(rootDir, "android/diskcache/src/main"),
        "android:worker" to File(rootDir, "android/worker/src/main"),
    )

    /** UI application shell (Phase 4B): name -> src/main dir. */
    val uiModules: Map<String, File> = mapOf(
        "app" to File(rootDir, "app/src/main"),
    )

    fun artifacts(): List<File> =
        System.getProperty("cadence.artifacts")
            ?.split(File.pathSeparator)
            ?.filter { it.isNotBlank() }
            ?.map(::File)
            ?: error("cadence.artifacts system property not set (arch module doFirst)")

    fun allKtFilesUnderRoot(): List<File> = generateSequence(rootDir) { parent ->
        parent.parentFile
    }.let { _ ->
        rootDir.walkTopDown()
            .onEnter { dir -> dir.name != "build" && !dir.name.startsWith(".") && dir.name != "gradle" }
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    fun classBytesFrom(artifact: File): Sequence<Pair<String, ByteArray>> = sequence {
        if (artifact.isDirectory) {
            artifact.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".class") }
                .forEach { f -> yield(f.relativeTo(artifact).path to f.readBytes()) }
        } else {
            ZipFile(artifact).use { zip ->
                for (entry in zip.entries().asSequence()) {
                    if (entry.name.endsWith(".class")) {
                        yield(entry.name to zip.getInputStream(entry).use { it.readBytes() })
                    }
                }
            }
        }
    }

    fun containsAscii(bytes: ByteArray, needle: String): Boolean {
        val n = needle.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..bytes.size - n.size) {
            for (j in n.indices) {
                if (bytes[i + j] != n[j]) continue@outer
            }
            return true
        }
        return false
    }
}
