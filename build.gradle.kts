buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.8.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.2.0-2.0.2")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.2.0")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.0")
    }
}

subprojects {
    group = "com.cadence"
    version = "0.1.0-SNAPSHOT"

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed")
            showExceptions = true
            showStackTraces = true
            showCauses = true
        }
    }
}
