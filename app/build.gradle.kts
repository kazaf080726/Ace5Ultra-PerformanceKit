import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val appSigning = Properties().apply {
    val f = rootProject.file("app/signing.properties")
    if (f.exists()) load(FileInputStream(f))
}

android {
    namespace = "com.ace5ultra.perfkit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ace5ultra.perfkit"
        minSdk = 29
        targetSdk = 36
        versionCode = 10100
        versionName = "1.0.1"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (appSigning.getProperty("storeFile") != null) {
                storeFile = file(appSigning.getProperty("storeFile"))
                storePassword = appSigning.getProperty("storePassword")
                keyAlias = appSigning.getProperty("keyAlias")
                keyPassword = appSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (appSigning.getProperty("storeFile") != null)
                signingConfigs.getByName("release") else null
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.libsu.core)
}
