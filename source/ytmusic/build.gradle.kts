plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":source:api"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.core)
    // NewPipe Extractor (GPL-3.0) — adapter-isolated to this module (LICENSE_DECISION.md cond.5).
    implementation(libs.newpipe.extractor)
    // Production transport uses OkHttp (Apache-2.0, GPL-compatible) as NewPipe Downloader.
    implementation(libs.okhttp)
    testImplementation(kotlin("test"))
    testImplementation(project(":core:testing"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.register<JavaExec>("liveCapture") {
    group = "verification"
    description = "Authoring-time live capture of YT Music wire fixtures (never run in CI)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.cadence.source.ytmusic.YtMusicLiveCaptureKt")
    providers.gradleProperty("ytmusic.recordPath").orNull?.let { systemProperty("ytmusic.recordPath", it) }
    providers.gradleProperty("ytmusic.query").orNull?.let { systemProperty("ytmusic.query", it) }
}
