plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":core:model"))
    api(libs.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
