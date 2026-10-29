// Puts the Android, Kotlin and Compose plugins on the root classpath once, so every module
// (via the build-logic convention plugins) shares a single version of each.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
}
