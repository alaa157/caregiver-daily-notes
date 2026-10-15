plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.caregiver.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.caregiver.mobile"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Only en + ar ship in the app; strip other locales' resources
        // pulled in by dependencies.
        resourceConfigurations += listOf("en", "ar")
    }

    buildTypes {
        // Demo APKs are the offline build: DEMO_BACKEND serves canned
        // in-memory data with no network, so the app can be installed and
        // clicked through with no backend running. `debug` is unminified;
        // `minified` is the R8-shrunk offline demo for smaller installs.
        debug {
            buildConfigField("boolean", "DEMO_BACKEND", "true")
        }
        release {
            buildConfigField("boolean", "DEMO_BACKEND", "false")
            // Keep minify off: the old cross-platform app crashed on launch with
            // R8 enabled and was never diagnosed. Revisit only with a device proof.
            isMinifyEnabled = false
        }
        // Squeezed offline demo: R8 + resource shrink, debug-signed so it
        // installs like the debug APK, and DEMO_BACKEND=true so it runs with
        // no backend. Smaller than `debug` for handing around; `release`
        // above stays minify-off with DEMO_BACKEND=false (real backend).
        create("minified") {
            buildConfigField("boolean", "DEMO_BACKEND", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // 2015-era devices (API 21+): backport java.time and friends.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // Explicit: AppGraph reads BuildConfig.DEMO_BACKEND for the offline switch.
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    // Shared fakes/fixtures for local JVM tests and instrumentation tests alike.
    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)
    implementation(libs.core.ktx)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime.saveable)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.tooling.preview)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.activity.compose)

    implementation(libs.coroutines.core)
    implementation(libs.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.coroutines.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
