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

        // Vosk traz .so pra 4 ABIs. Sem filtro o APK vai a 70 MB.
        // O alvo real e arm64; 32 bits fica pra nao excluir aparelho antigo.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
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

// Tier 0 = zero dependencia externa de runtime.
// Vosk: STT 100% offline (o modelo viaja no APK).
dependencies {
    implementation("com.alphacephei:vosk-android:0.3.75")
}
