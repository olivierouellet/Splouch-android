plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.splouch.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.splouch.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2026.09.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Every issue Lint reported when this was written is recorded here, and each of
        // the errors among them is a decision (see the Lint step in ci.yml). Anything
        // not in the file fails `lintDebug`. Fix a new error, or if it too is a
        // decision, regenerate with `./gradlew :app:updateLintBaseline` and say why in
        // the commit.
        baseline = file("lint-baseline.xml")
    }
}

kotlin {
    compilerOptions {
        // Same as `:core`: the build is warning-free, and stays so.
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
