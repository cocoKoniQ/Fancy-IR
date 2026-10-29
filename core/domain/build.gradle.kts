plugins {
    alias(libs.plugins.fancyir.jvm.library)
}

dependencies {
    api(project(":core:ir"))
    api(project(":core:irdb"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
