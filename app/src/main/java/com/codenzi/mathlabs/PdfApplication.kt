package com.codenzi.mathlabs

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PdfApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase servislerini başlat
        FirebaseApp.initializeApp(this)

        // Firebase App Check'i başlat
        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        // --- YENİ VE DÜZELTİLMİŞ KOD ---
        // Uygulamanın test (debug) modunda mı yoksa yayın (release) modunda mı
        // çalıştığını kontrol et ve uygun güvenlik sağlayıcısını kur.
        if (BuildConfig.DEBUG) {
            // EĞER TEST MODUNDAYSAK:
            // Test için özel "Debug" sağlayıcısını kur.
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            // EĞER YAYIN MODUNDAYSAK (PLAY STORE):
            // Gerçek kullanıcılar için Play Integrity sağlayıcısını kur.
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        // Mevcut kodlarınız
        AppCompatDelegate.setDefaultNightMode(SharedPreferencesManager.getTheme(this))
        UIFeedbackHelper.init(this)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let { LocaleHelper.onAttach(it) })
    }
}
