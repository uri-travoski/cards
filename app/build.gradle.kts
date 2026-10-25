import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "au.sam.cards"
    compileSdk = 36

    defaultConfig {
        applicationId = "au.sam.cards"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
        resourceConfigurations += listOf("en")
        // Test builds only: point the Drive client at a mock server and skip Google sign-in.
        buildConfigField("String", "MOCK_DRIVE", "\"${local.getProperty("cards.mockDrive", "")}\"")
        // Test builds only: allow picking a folder on the phone itself (release builds refuse it).
        buildConfigField("boolean", "ALLOW_LOCAL", local.getProperty("cards.allowLocalFolder", "false"))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            // The bundled OCR/barcode models are large per CPU type; phones are all arm64.
            ndk { abiFilters += "arm64-v8a" }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
}
