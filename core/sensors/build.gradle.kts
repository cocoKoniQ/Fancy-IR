plugins {
    alias(libs.plugins.fancyir.android.library)
}

android {
    namespace = "io.github.cocokoniq.fancyir.core.sensors"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.android)
}
