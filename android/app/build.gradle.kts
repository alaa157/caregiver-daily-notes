plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.caregiver.mobile"
    compileSdk = 35

    val releaseSigning = if (System.getenv("KEYSTORE_PATH") != null) {
        signingConfigs.create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH"))
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    } else null

    // Demo releases are handed to testers repeatedly. CI otherwise generates a
    // fresh throwaway debug key per run, which makes every demo refuse to
    // install over the previous one (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
    // The checked-in demo keystore is public by design (debug-grade, password
    // "android") so successive demo builds share one signature and update in
    // place. Falls back to the local debug key if the file is absent.
    val demoSigning = rootProject.file("keystore/demo.keystore").takeIf { it.exists() }?.let {
        signingConfigs.create("demo") {
            storeFile = it
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.caregiver.mobile"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.6"
        // Build traceability: CI passes the commit being built (GIT_COMMIT);
        // local builds without the variable report "unknown".
        buildConfigField("String", "GIT_COMMIT", "\"${(System.getenv("GIT_COMMIT") ?: "unknown").take(12)}\"")
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
            // Standalone local-first mode: no backend, no login.
            buildConfigField("boolean", "LOCAL_MODE", "true")
        }
        release {
            buildConfigField("boolean", "DEMO_BACKEND", "false")
            // Standalone local-first mode: no backend, no login. Release is
            // intentionally local-first (see docs); not inheriting from defaultConfig.
            buildConfigField("boolean", "LOCAL_MODE", "true")
            // Keep minify off: the old cross-platform app crashed on launch with
            // R8 enabled and was never diagnosed. Revisit only with a device proof.
            isMinifyEnabled = false
            if (releaseSigning != null) signingConfig = releaseSigning
        }
        // Squeezed offline demo: R8 + resource shrink, signed with the shared
        // demo key so successive demos update in place (see demoSigning
        // above), and DEMO_BACKEND=true so it runs with no backend. Smaller
        // than `debug` for handing around; `release` above stays minify-off
        // with DEMO_BACKEND=false (real backend).
        create("minified") {
            buildConfigField("boolean", "DEMO_BACKEND", "true")
            buildConfigField("boolean", "LOCAL_MODE", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = demoSigning ?: signingConfigs.getByName("debug")
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

// Room (Task 8a): KSP is the processor here — the module had no annotation
// processor before, so KSP was chosen over adding kapt. Exported schema is
// committed so the 8b/8c migrations have a baseline to diff against.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.compose.shimmer)
    debugImplementation(libs.compose.tooling.preview)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel)
    // collectAsStateWithLifecycle: the screens stop collecting while the app
    // is in the background instead of keeping a live collector on every flow.
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    implementation(libs.coroutines.core)
    implementation(libs.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    // DAO/repository tests run on the JVM against in-memory Room.
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.work.testing)
    // Compose UI tests also run on the JVM (Robolectric), so settings/locale
    // regressions execute in CI's `testDebugUnitTest` without a device.
    testImplementation(composeBom)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.test.ext.junit)

    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.coroutines.core)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Print full test exception messages (assertion text, stack traces) in the
// Gradle console so flaky-test evidence is readable directly in CI logs
// instead of only inside the HTML report.
tasks.withType(Test::class.java).configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
