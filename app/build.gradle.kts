plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.example.xcommandcenter"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // The app's identity on Google Play and on devices; it can never change once published.
        // (The Kotlin package, `namespace` above, is only the code's layout and may differ.)
        applicationId = "com.xeeshan.manage_everything"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // A fixed debug key, kept in the repository, so every build (from any machine) installs
        // over the previous one instead of needing an uninstall that wipes the app's data.
        // Standard debug-key passwords; this key is not secret and only signs debug builds.
        getByName("debug") {
            storeFile = file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The Play upload key is never in the repository: release builds read it from the
        // environment, set from GitHub secrets by .github/workflows/release.yml (see RELEASING.md).
        // Without RELEASE_KEYSTORE_FILE, release builds come out unsigned.
        create("release") {
            System.getenv("RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { path ->
                val password = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                storeFile = file(path)
                storePassword = password
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "upload"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: password
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.getByName("release").takeIf { it.storeFile != null }?.let { signingConfig = it }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            // Prebuilt libraries from androidx that ship already stripped; skip the strip attempt.
            keepDebugSymbols += setOf("**/libandroidx.graphics.path.so", "**/libdatastore_shared_counter.so")
        }
    }
}

kotlin {
    compilerOptions {
        // The Definition of Done requires a warning-free build; enforce it.
        allWarningsAsErrors = true
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
