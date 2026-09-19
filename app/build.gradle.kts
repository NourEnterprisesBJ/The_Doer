plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.thedoer.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.thedoer.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0-sprint2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // STT : Vosk (moteur par défaut, tiers LOW/MID)
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    // Persistance locale : Room (Sprint 2 - surnoms de contacts, mémoire)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // TODO (Phase 2+) : ONNX Runtime Mobile (Piper/Whisper avancé)
    // implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")

    // TODO (Sprint 4) : ML Kit (OCR par défaut)
    // implementation("com.google.mlkit:text-recognition:16.0.0")

    // TODO (Sprint 4) : MediaPipe (vision)
    // implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // TODO (Phase 2) : SQLCipher (chiffrement base de données)
    // implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    // TODO (Phase 2) : ObjectBox (mémoire sémantique/vectorielle)
    // implementation("io.objectbox:objectbox-android:3.8.0")
}
