plugins {
    id("dev.komrd.android.library")
    id("dev.komrd.android.room")
    id("dev.komrd.android.hilt")
}

android {
    namespace = "dev.komrd.core.database"

    sourceSets {
        getByName("test").assets.srcDir(file("$projectDir/schemas"))
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.ext.junit)
}
