plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.kasiralva.basic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kasiralva.basic"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "1.5.0"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KASIRALVA_KEYSTORE_PATH")
            val ksPass = System.getenv("KASIRALVA_KEYSTORE_PASSWORD")
            val alias = System.getenv("KASIRALVA_KEY_ALIAS")
            val keyPass = System.getenv("KASIRALVA_KEY_PASSWORD")
            // Hanya set jika path ada — job CI sudah validasi secret sebelumnya
            if (!ksPath.isNullOrBlank()) {
                val f = file(ksPath)
                require(f.exists()) {
                    "Keystore tidak ditemukan di path: $ksPath"
                }
                storeFile = f
                storePassword = ksPass ?: error("KASIRALVA_KEYSTORE_PASSWORD kosong")
                keyAlias = alias ?: error("KASIRALVA_KEY_ALIAS kosong")
                keyPassword = keyPass ?: error("KASIRALVA_KEY_PASSWORD kosong")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Pakai signing release hanya jika keystore path di-set (CI release job)
            val ksPath = System.getenv("KASIRALVA_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Firebase (Firestore only — no Cloud Functions)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
}
