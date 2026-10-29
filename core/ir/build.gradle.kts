plugins {
    alias(libs.plugins.fancyir.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
