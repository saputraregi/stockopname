
// Top-level build file (D:\Aplikasi Project Perpus\build.gradle.kts)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin) apply false
    alias(libs.plugins.ksp) apply false // Ini adalah cara yang benar untuk mendeklarasikan KSP agar bisa digunakan oleh modul
}
