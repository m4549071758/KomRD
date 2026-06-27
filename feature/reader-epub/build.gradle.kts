plugins {
    id("dev.komrd.android.library")
    id("dev.komrd.android.compose")
    id("dev.komrd.android.hilt")
}

android {
    namespace = "dev.komrd.feature.readerepub"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(project(":core:prefetch"))
    implementation(project(":core:sync"))
    implementation(project(":core:network"))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(project(":core:cache"))
}
