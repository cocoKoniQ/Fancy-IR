plugins {
    alias(libs.plugins.fancyir.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.cocokoniq.fancyir.core.data"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    // Robolectric downloads its Android framework jar from Maven Central at test time; a local
    // mirror can be set with -Probolectric.repoUrl=... (or in ~/.gradle/gradle.properties).
    providers.gradleProperty("robolectric.repoUrl").orNull?.let { systemProperty("robolectric.dependency.repo.url", it) }
}
