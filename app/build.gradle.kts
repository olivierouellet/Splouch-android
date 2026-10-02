import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Play needs a versionCode above every one it has seen, so it is derived from the
// name: yyyy.mm.patch -> yyyymm * 1000 + patch (2026.09.0 -> 202609000).
val appVersionName = "2026.10.1"
val appVersionCode = run {
    val (year, month, patch) = requireNotNull(
        Regex("""(\d{4})\.(\d{2})\.(\d{1,3})""").matchEntire(appVersionName),
    ) { "versionName must be yyyy.mm.patch, got $appVersionName" }.destructured
    (year.toInt() * 100 + month.toInt()) * 1000 + patch.toInt()
}

// The Play upload key lives outside the repo; `local.properties` (gitignored) names it.
// Without those entries — CI, a fresh clone — the release build is simply unsigned.
val uploadKey = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}.takeIf { it.getProperty("splouch.upload.storeFile") != null }

android {
    namespace = "app.splouch.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.splouch.android"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey.getProperty("splouch.upload.storeFile"))
                storePassword = uploadKey.getProperty("splouch.upload.storePassword")
                keyAlias = uploadKey.getProperty("splouch.upload.keyAlias")
                keyPassword = uploadKey.getProperty("splouch.upload.storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
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
