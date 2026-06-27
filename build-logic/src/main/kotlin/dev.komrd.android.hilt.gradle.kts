import dev.komrd.buildlogic.requireLibrary
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.plugin.KaptExtension

plugins {
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", libs.requireLibrary("hilt.android"))
    add("kapt", libs.requireLibrary("hilt.compiler"))
    // Hilt同梱版より新しいkotlin-metadata-jvmで上書きし、Kotlin 2.4.0メタデータ非対応を回避
    add("kapt", libs.requireLibrary("kotlin.metadata.jvm"))
}

extensions.configure<KaptExtension>("kapt") {
    correctErrorTypes = true
}
