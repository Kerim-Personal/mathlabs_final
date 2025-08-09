package com.codenzi.mathlabs

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PdfApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // --- SADELEŞTİRİLMİŞ APP CHECK YAPILANDIRMASI ---

        // 1. Firebase ana uygulamasını başlat
        FirebaseApp.initializeApp(this)

        // 2. Firebase App Check örneğini al
        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        // 3. Doğrudan Play Integrity sağlayıcısını kur.
        //    Bu, uygulamanın sadece Google Play'den indirilen orijinal versiyonlarda
        //    Firebase servislerine erişmesini sağlar.
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // --- YAPILANDIRMA BİTTİ ---


        // Mevcut kodlarınız
        AppCompatDelegate.setDefaultNightMode(SharedPreferencesManager.getTheme(this))
        UIFeedbackHelper.init(this)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let { LocaleHelper.onAttach(it) })
    }
}