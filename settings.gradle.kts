pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Fancy-IR"

include(":app")
include(":feature:hud")
include(":core:designsystem")
include(":core:vision")
include(":core:data")
include(":core:transmitter")
include(":core:haptics")
include(":core:sensors")
include(":core:domain")
include(":core:irdb")
include(":core:ir")
include(":tools:irdb-compiler")
