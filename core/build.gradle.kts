plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // Coverage of `:core` by its own suite. `./gradlew :core:koverLog` prints the line
    // figure and `:core:koverHtmlReport` shows which lines; CI prints it and gates nothing,
    // the same stance the server repo takes with pytest-cov.
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // The compiler is the type checker here, and it is clean. This keeps it that way:
        // a warning is fixed or suppressed with a reason, not left for the next reader.
        allWarningsAsErrors = true
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnit()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
