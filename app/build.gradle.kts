import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Play needs a versionCode above every one it has seen, so it is derived from the
// name: yyyy.mm.patch -> yyyymm * 1000 + patch (2026.09.0 -> 202609000).
val appVersionName = "2026.10.4"
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

// app.md §10: the Firebase project heat notifications arrive through. Its four values are
// per deployment — the cloud that sends must hold the same project's service account — so
// they come from `local.properties` (`splouch.firebase.appId`, `.apiKey`, `.projectId`,
// `.senderId`) or the environment (`FIREBASE_APP_ID`, …) and never from the repo. There
// is no `google-services.json` and no Google Services plugin: the app initialises Firebase
// itself from these. Absent, the build has no push and no bell (`N-01`).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun firebase(key: String, env: String): String =
    (localProps.getProperty("splouch.firebase.$key") ?: System.getenv(env)).orEmpty().replace("\"", "")

android {
    namespace = "app.splouch.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.splouch.android"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebase("appId", "FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebase("apiKey", "FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebase("projectId", "FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${firebase("senderId", "FIREBASE_SENDER_ID")}\"")
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
        // P-19's About shows the version, read from `BuildConfig.VERSION_NAME`.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    bundle {
        // T-08: the reader picks the app's language in Settings, over the device's. Play
        // splits an App Bundle by language by default and installs only the device's, so
        // French chosen on an English phone would find no `values-fr` and read English.
        language {
            enableSplit = false
        }
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

    // app.md §10: Firebase Cloud Messaging only — no Analytics, no Crashlytics. The privacy
    // policy names it (`[privacy] notify_1`).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
