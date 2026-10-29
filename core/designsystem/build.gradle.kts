plugins {
    alias(libs.plugins.fancyir.android.library)
    alias(libs.plugins.fancyir.android.compose)
}

android {
    namespace = "io.github.cocokoniq.fancyir.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
}
