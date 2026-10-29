plugins {
    alias(libs.plugins.fancyir.jvm.library)
}

dependencies {
    api(project(":core:ir"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
}
