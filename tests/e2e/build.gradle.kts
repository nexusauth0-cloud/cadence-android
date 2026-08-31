plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":source:api"))
    implementation(project(":core:data"))
    implementation(project(":core:testing"))
    // NOTE: deliberately NO dependency on :source:demo or any provider impl —
    // the E2E proves the seams with fakes only.
    implementation(libs.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    systemProperty("cadence.root", rootDir.absolutePath)
}
