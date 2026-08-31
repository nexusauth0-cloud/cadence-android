plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":source:api"))
    implementation(libs.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(project(":core:testing")) // fakes for adapter tests only
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
