import com.android.build.api.dsl.ApplicationExtension
import dev.komrd.buildlogic.KomrdBuild
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

extensions.configure<ApplicationExtension>("android") {
    compileSdk = KomrdBuild.COMPILE_SDK

    defaultConfig {
        applicationId = "dev.komrd"
        minSdk = KomrdBuild.MIN_SDK
        targetSdk = KomrdBuild.TARGET_SDK
        versionCode = 3
        versionName = "0.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }

    signingConfigs {
        create("release") {
            storeFile =
                keystoreProperties["storeFile"]?.toString()?.let(::file)
                    ?: System.getenv("KOMRD_KEYSTORE_FILE")?.let(::file)
            storePassword = keystoreProperties["storePassword"]?.toString() ?: System.getenv("KOMRD_KEYSTORE_PASSWORD")
            keyAlias = keystoreProperties["keyAlias"]?.toString() ?: System.getenv("KOMRD_KEY_ALIAS")
            keyPassword = keystoreProperties["keyPassword"]?.toString() ?: System.getenv("KOMRD_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            val releaseSigning = signingConfigs.getByName("release")
            val hasReleaseSigning =
                releaseSigning.storeFile?.exists() == true &&
                    releaseSigning.storePassword != null &&
                    releaseSigning.keyAlias != null &&
                    releaseSigning.keyPassword != null
            signingConfig = if (hasReleaseSigning) releaseSigning else signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // 複数依存（okhttp / jspecify等）が同梱するOSGIマニフェストの重複を除外
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

extensions.configure<KotlinAndroidProjectExtension>("kotlin") {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
