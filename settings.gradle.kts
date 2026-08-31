dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "cadence"

include(
    ":core:model",
    ":core:common",
    ":core:domain",
    ":core:data",
    ":core:datastore",
    ":core:database",
    ":core:media",
    ":core:cache",
    ":core:testing",
    ":source:api",
    ":source:demo",
    ":source:ytmusic",
    ":tests:e2e",
    ":tests:architecture",
    ":android:vaultstore",
    ":android:roomdb",
    ":android:player",
    ":android:diskcache",
    ":android:worker",
    ":app",
)