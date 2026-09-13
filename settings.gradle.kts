pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Splouch"

// `:core` is plain Kotlin: the contract layer (sockets, frame merge, race clock,
// strings) with no Android types, testable with a JDK alone.
include(":core")

// `:app` needs the Android SDK just to configure. It is included only when one is
// present, so `./gradlew :core:test` runs on a JDK-only machine or CI job.
val sdkDir: String? = run {
    val props = java.util.Properties()
    val local = rootDir.resolve("local.properties")
    if (local.exists()) local.inputStream().use(props::load)
    listOf(props.getProperty("sdk.dir"), System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
        .firstOrNull { !it.isNullOrBlank() && File(it).isDirectory }
}
if (sdkDir != null) {
    include(":app")
} else {
    logger.warn("Android SDK not found (local.properties sdk.dir / ANDROID_HOME): ':app' is not included, only ':core'.")
}
