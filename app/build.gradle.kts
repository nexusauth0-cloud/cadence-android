import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cadence.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cadence"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // The Compose UI-state matrix needs the Compose ui-test-manifest (debug-only
    // ComponentActivity) which release unit tests do not merge; keep it the
    // exclusive province of testDebugUnitTest so `./gradlew test` stays green.
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        if (name.contains("ReleaseUnitTest", ignoreCase = true)) {
            filter {
                excludeTestsMatching("com.cadence.app.LibraryUiMatrixTest")
                excludeTestsMatching("com.cadence.app.DownloadsUiMatrixTest")
                excludeTestsMatching("com.cadence.app.PlayerUiMatrixTest")
                excludeTestsMatching("com.cadence.app.UpdateDialogUiTest")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Domain foundation (pure JVM) consumed by the UI layer.
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:media"))
    implementation(project(":core:data"))
    implementation(project(":source:api"))
    implementation(libs.coroutines.core)

    // Composition root only: provider implementation + real player adapter.
    implementation(project(":source:demo"))
    implementation(project(":source:ytmusic"))
    implementation(project(":android:player"))
    implementation(project(":android:roomdb"))
    implementation(project(":android:worker"))
    implementation(project(":android:diskcache"))

    // AndroidX / Compose.
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material.window.size)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // JVM unit tests (junit4 on the JUnit platform via vintage engine, mirroring
    // the other android modules).
    testImplementation(kotlin("test"))
    testImplementation(project(":core:testing"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation("junit:junit:4.13.2")
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)

    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.coroutines.test)
}
