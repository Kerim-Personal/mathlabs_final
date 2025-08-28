package com.codenzi.mathlabs

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
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
        // Firebase init + App Check
        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())

        // Reklam SDK sadece 1 kez initialize
        try {
            MobileAds.initialize(this) {}
            // Debug derlemelerinde test cihazı tanımla (gerçek cihaz id'sini ekleyin)
            if (BuildConfig.DEBUG) {
                val config = RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf("TEST_DEVICE_ID")) // Gerekirse gerçek test id ile değiştirin
                    .build()
                MobileAds.setRequestConfiguration(config)
            }
        } catch (e: Exception) {
            // Sessiz log; uygulama çökmesin
        }

        AppCompatDelegate.setDefaultNightMode(SharedPreferencesManager.getTheme(this))
        UIFeedbackHelper.init(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            // ...existing code...
            override fun onActivityResumed(activity: android.app.Activity) {
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
                    activity.finishAffinity(); return
                }
                val currentTime = System.currentTimeMillis()
                val calendar = Calendar.getInstance()
                val systemTime = calendar.timeInMillis
                if (abs(currentTime - systemTime) > 10000) {
                    val currentLocale = activity.resources.configuration.locales[0]
                    val message = if (currentLocale.language == "en") {
                        "The system time on your device is incorrect!"
                    } else {
                        "Cihaz sistem saati doğru değil!"
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    activity.finishAffinity(); return
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