plugins {
    alias(libs.plugins.fancyir.android.library)
}

android {
    namespace = "io.github.cocokoniq.fancyir.core.vision"
}

dependencies {
    api(project(":core:domain"))
    api(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.kotlinx.coroutines.android)
}
