import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
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
    println("Warning: local.properties file not found in project root.")
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

        // API anahtarı artık sunucuda olduğu için bu satır gereksiz, ama projenin başka yerlerinde
        // kullanılıyor olabileceğinden ve derleme hatası vermemesi için bırakıyoruz.
        buildConfigField("String", "GEMINI_API_KEY", "\"\"")

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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.core.splashscreen)

    // ---- FIREBASE BoM (Tüm Firebase kütüphanelerini uyumlu hale getirir) ----
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    // ---- GEREKLİ FIREBASE KÜTÜPHANELERİ ----
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // ---- AĞ & PDF KÜTÜPHANELERİ ----
    implementation("com.google.android.gms:play-services-ads:23.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.android.pdf.viewer)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // ---- HILT & ROOM ----
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ---- JETPACK COMPOSE ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity)

    // ---- DİĞER KÜTÜPHANELER ----
    implementation("com.android.billingclient:billing-ktx:7.0.0")
    implementation("com.google.firebase:firebase-auth-ktx")

    // ---- TEST KÜTÜPHANELERİ ----
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

}

kapt {
    correctErrorTypes = true
}
