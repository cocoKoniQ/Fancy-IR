plugins {
    alias(libs.plugins.fancyir.android.application)
    alias(libs.plugins.fancyir.android.compose)
}

android {
    namespace = "io.github.cocokoniq.fancyir"

    defaultConfig {
        applicationId = "io.github.cocokoniq.fancyir"
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":feature:hud"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:transmitter"))
    implementation(project(":core:haptics"))
    implementation(project(":core:sensors"))
    implementation(project(":core:vision"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
