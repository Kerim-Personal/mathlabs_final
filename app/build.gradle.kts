// kerim-personal/mathlabs_final/mathlabs_final-f49787796173bd93b9413051b0018b2349ef86c8/app/build.gradle.kts

import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt") // KAPT plugin'i Hilt ve Room için gereklidir
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists() && localPropertiesFile.isFile) {
    try {
        localProperties.load(FileInputStream(localPropertiesFile))
    } catch (e: Exception) {
        println("Warning: Could not load local.properties: ${e.message}")
    }
} else {
    println("Warning: local.properties file not found in project root. Please ensure it exists and contains your GEMINI_API_KEY.")
}

android {
    namespace = "com.codenzi.mathlabs"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codenzi.mathlabs"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val geminiApiKeyFromProperties = localProperties.getProperty("GEMINI_API_KEY")?.trim() ?: ""

        if (geminiApiKeyFromProperties.isEmpty()) {
            println("Warning: GEMINI_API_KEY is empty in local.properties. AI features may not work.")
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
        } else {
            println("Info: GEMINI_API_KEY loaded from local.properties.")
            buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKeyFromProperties.replace("\"", "\\\"")}\"")
        }

        // AdMob kimliklerini local.properties'ten alıp string kaynağı olarak ekle
        val admobAppId = localProperties.getProperty("ADMOB_APP_ID") ?: ""
        resValue("string", "admob_app_id", admobAppId)

        val admobBannerUnitId = localProperties.getProperty("ADMOB_BANNER_UNIT_ID") ?: ""
        resValue("string", "admob_banner_unit_id", admobBannerUnitId)

        val admobInterstitialUnitId = localProperties.getProperty("ADMOB_INTERSTITIAL_UNIT_ID") ?: ""
        resValue("string", "admob_interstitial_unit_id", admobInterstitialUnitId)

        val admobRewardedUnitId = localProperties.getProperty("ADMOB_REWARDED_UNIT_ID") ?: ""
        resValue("string", "admob_rewarded_unit_id", admobRewardedUnitId)
    }

    buildTypes {
        release {
            // Proguard'ı etkinleştir
            isMinifyEnabled = true
            // Kaynakları küçült
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val geminiApiKeyFromProperties = localProperties.getProperty("GEMINI_API_KEY")?.trim() ?: ""
            if (geminiApiKeyFromProperties.isNotEmpty()) {
                buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKeyFromProperties.replace("\"", "\\\"")}\"")
            } else {
                buildConfigField("String", "GEMINI_API_KEY", "\"\"")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ---- ÇEKİRDEK KÜTÜPHANELER ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // ---- Firebase Bağımlılıkları ----
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.android.gms:play-services-ads:23.0.0")
    implementation("com.google.firebase:firebase-storage-ktx")

    // ---- Ağ ve Önbellekleme ----
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ---- Yaşam Döngüsü (Lifecycle) ve ViewModel ----
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

    // ---- UI Kütüphaneleri ----
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.core.splashscreen)

    // ---- PDF Görüntüleyici ve Metin Çıkarma ----
    implementation(libs.android.pdf.viewer)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // ---- Google AI (Gemini) ----
    implementation("com.google.ai.client.generativeai:generativeai:0.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ---- Hilt - Dependency Injection ----
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")

    // ---- Room Veritabanı ----
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ---- Jetpack Compose ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity)

    // ---- Test Kütüphaneleri ----
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Google Play Faturalandırma Kütüphanesi
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    // Firebase Crashlytics
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // Google Analytics
    implementation("com.google.firebase:firebase-analytics-ktx")
}

kapt {
    correctErrorTypes = true
}
