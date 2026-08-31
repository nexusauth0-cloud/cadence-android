plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    api(project(":source:api"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(project(":core:media"))
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.test)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}
