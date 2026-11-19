plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.elerto.homemanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elerto.homemanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "2.2"
    }

    // Keystore fisso nel repo: cosi' ogni build ha la stessa firma e l'app
    // si aggiorna sopra la versione precedente senza perdere i dati.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/homemanager.jks")
            storePassword = "homemanager"
            keyAlias = "homemanager"
            keyPassword = "homemanager"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
}
