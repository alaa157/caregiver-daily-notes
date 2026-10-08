// Top-level build file for the native Kotlin Android app.
// Backend stays Maven-only; Gradle exists solely for android/.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
