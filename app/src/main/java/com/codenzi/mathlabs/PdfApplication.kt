package com.codenzi.mathlabs

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp // Hilt import'u

@HiltAndroidApp // Hilt'e bu sınıfın ana bağımlılık konteyneri olduğunu söyler
class PdfApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(SharedPreferencesManager.getTheme(this))
        UIFeedbackHelper.init(this)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let { LocaleHelper.onAttach(it) })
    }
}