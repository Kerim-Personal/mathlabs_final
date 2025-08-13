// kerim-personal/mathlabs_final/mathlabs_final-846de2bc6294564b282343e4d0a0be0e4be59898/app/src/main/java/com/codenzi/mathlabs/LocaleHelper.kt

package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private fun setLocale(context: Context, languageCode: String): Context {
        persist(context, languageCode)
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun onAttach(context: Context): Context {
        val storedLanguage = SharedPreferencesManager.getLanguage(context)

        return if (storedLanguage != null) {
            // Kayıtlı bir dil varsa, onu kullan.
            setLocale(context, storedLanguage)
        } else {
            // İlk açılışsa, cihaz diline göre ayarla ve kaydet.
            val defaultSystemLanguage = Locale.getDefault().language
            val languageToSet = if (defaultSystemLanguage == "tr") "tr" else "en"
            setLocale(context, languageToSet)
        }
    }

    fun persist(context: Context, languageCode: String) {
        SharedPreferencesManager.saveLanguage(context, languageCode)
        // Dilin artık seçildiğini işaretle (ilk açılışta veya sonradan)
        SharedPreferencesManager.setLanguageSelected(context, true)
    }

    fun applyLanguage(activity: Activity, languageCode: String) {
        persist(activity, languageCode)
        val intent = Intent(activity, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
    }
}