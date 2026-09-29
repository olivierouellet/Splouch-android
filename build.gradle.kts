plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

// ktlint owns layout, from the root so one task covers `:core`, `:app` and the build
// scripts — and runs on a JDK-only checkout, where `:app` is not in the build but its
// sources still are. Rules and style are in `.editorconfig`, which Android Studio reads
// too. `./gradlew spotlessApply` formats; `spotlessCheck` is what CI runs.
spotless {
    kotlin {
        target("core/src/**/*.kt", "app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
    kotlinGradle {
        target("*.gradle.kts", "core/*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
}

// Spotless does not count `.editorconfig` among its inputs, so with the build cache on
// (`gradle.properties`) a rule changed there would keep serving the old result.
tasks.withType<com.diffplug.gradle.spotless.SpotlessTask>().configureEach {
    inputs.file(rootProject.file(".editorconfig"))
}
