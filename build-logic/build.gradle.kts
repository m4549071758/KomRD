plugins {
    `kotlin-dsl`
}

group = "dev.komrd.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
}
