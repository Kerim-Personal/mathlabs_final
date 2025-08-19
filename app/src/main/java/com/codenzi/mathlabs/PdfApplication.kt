package com.codenzi.mathlabs

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import java.util.Calendar
import kotlin.math.abs

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

        // Uygulama herhangi bir aktiviteye geri döndüğünde tarih/saat bütünlüğünü doğrula.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            // ...existing code...
            override fun onActivityResumed(activity: android.app.Activity) {
                // Otomatik saat ve tarih ayarlarını kontrol et
                val isAutoTimeEnabled = Settings.Global.getInt(activity.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
                val isAutoTimeZoneEnabled = Settings.Global.getInt(activity.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0) == 1

                if (!isAutoTimeEnabled || !isAutoTimeZoneEnabled) {
                    val currentLocale = activity.resources.configuration.locales[0]
                    val message = if (currentLocale.language == "en") {
                        "Please enable automatic date and time settings on your device!"
                    } else {
                        "Lütfen cihazınızın tarih ve saat ayarlarını otomatik olarak ayarlayın!"
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    // Tüm back stack'i kapat
                    activity.finishAffinity()
                    return
                }

                // Sistem saatini kontrol et
                val currentTime = System.currentTimeMillis()
                val calendar = Calendar.getInstance()
                val systemTime = calendar.timeInMillis

                if (abs(currentTime - systemTime) > 10000) { // 10 saniyeden fazla fark varsa
                    val currentLocale = activity.resources.configuration.locales[0]
                    val message = if (currentLocale.language == "en") {
                        "The system time on your device is incorrect!"
                    } else {
                        "Cihaz sistem saati doğru değil!"
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    activity.finishAffinity()
                    return
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let { LocaleHelper.onAttach(it) })
    }
}