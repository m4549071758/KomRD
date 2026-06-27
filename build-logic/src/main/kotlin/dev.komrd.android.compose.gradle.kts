import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import dev.komrd.buildlogic.requireLibrary
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension>("android") {
        buildFeatures {
            compose = true
        }
    }
}

pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension>("android") {
        buildFeatures {
            compose = true
        }
    }
}

// debugImplementationにすることでリリースAPKに含まれない。
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    add("debugImplementation", libs.requireLibrary("androidx.compose.ui.tooling.preview"))
    add("debugImplementation", libs.requireLibrary("androidx.compose.ui.tooling"))
}
