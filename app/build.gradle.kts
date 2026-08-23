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
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = alias
                keyPassword = keyPass
            }
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
