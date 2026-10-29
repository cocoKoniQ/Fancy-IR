plugins {
    alias(libs.plugins.fancyir.android.library)
    alias(libs.plugins.fancyir.android.compose)
}

android {
    namespace = "io.github.cocokoniq.fancyir.feature.hud"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:vision"))
    implementation(project(":core:haptics"))
    implementation(project(":core:sensors"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.camera.compose)
    implementation(libs.kotlinx.coroutines.android)
}
