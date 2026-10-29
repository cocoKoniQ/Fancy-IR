import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** SDK levels shared by every Android module. See ARCHITECTURE.md §4.1. */
object FancyIrSdk {
    const val COMPILE = 37
    const val TARGET = 36

    /** Android 13: AGSL RuntimeShader and haptic composition primitives without fallbacks. */
    const val MIN = 33
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()
