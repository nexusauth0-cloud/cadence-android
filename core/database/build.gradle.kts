plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
