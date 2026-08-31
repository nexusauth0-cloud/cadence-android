plugins {
    kotlin("jvm")
}

// Architecture enforcement module (spike spec section 13).
// Deliberately has NO project dependencies: it inspects the *artifacts* of the
// production modules via a dedicated configuration so that violations are
// detected on compiled output, not source text alone.

val scannedRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false // scan OUR production jars only, not third-party libs
}

dependencies {
    listOf(
        ":core:model",
        ":core:common",
        ":source:api",
        ":core:domain",
        ":core:data",
        ":core:datastore",
        ":core:database",
        ":core:media",
        ":core:cache",
        ":source:demo",
        ":source:ytmusic",
    ).forEach { scannedRuntime(project(it)) }
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    // Scanned artifacts are injected at execution time and are not tracked as
    // task inputs — force re-run so violations can never hide behind
    // UP-TO-DATE checks.
    outputs.upToDateWhen { false }
    // Ensure producer jars are built BEFORE resolution in doFirst.
    dependsOn(scannedRuntime.buildDependencies)
    systemProperty("cadence.root", rootDir.absolutePath)
    doFirst {
        systemProperty("cadence.artifacts", scannedRuntime.files.joinToString(File.pathSeparator))
    }
}
