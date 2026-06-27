plugins {
    id("dev.komrd.android.library")
    id("dev.komrd.android.hilt")
}

android {
    namespace = "dev.komrd.core.sync"
}

dependencies {
    // DB(キュー)/network(Komga)へ直接アクセスする設計であり、core:database/core:network への
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
