plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.argos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.argos"
        // API 26 = NotificationChannel / startForegroundService.
        // Alvo real de teste e um Moto G6 Plus (API 28), por isso nada de API 31 como minimo.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }
}

// Tier 0 = zero dependencia externa. O bridge do llama.cpp entra no proximo corte.
dependencies {
}
