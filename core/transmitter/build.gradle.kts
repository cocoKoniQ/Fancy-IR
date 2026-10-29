plugins {
    alias(libs.plugins.fancyir.android.library)
}

android {
    namespace = "io.github.cocokoniq.fancyir.core.transmitter"
}

dependencies {
    api(project(":core:ir"))
    implementation(libs.kotlinx.coroutines.android)
}
