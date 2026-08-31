plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}
