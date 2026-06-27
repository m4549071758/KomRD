import dev.komrd.buildlogic.requireLibrary
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.plugin.KaptExtension

plugins {
    id("org.jetbrains.kotlin.kapt")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", libs.requireLibrary("androidx.room.runtime"))
    add("implementation", libs.requireLibrary("androidx.room.ktx"))
    add("kapt", libs.requireLibrary("androidx.room.compiler"))
    // RoomのkaptがKotlin 2.4.0メタデータを読めるよう上書き(Hiltと同様の回避)
    add("kapt", libs.requireLibrary("kotlin.metadata.jvm"))
}

extensions.configure<KaptExtension>("kapt") {
    // スキーマをエクスポートし、将来のマイグレーションテストの基準にする
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
