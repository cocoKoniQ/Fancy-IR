plugins {
    alias(libs.plugins.fancyir.jvm.library)
    application
}

application {
    mainClass = "io.github.cocokoniq.fancyir.tools.irdbcompiler.IrdbCompilerKt"
}

dependencies {
    implementation(project(":core:irdb"))
}
