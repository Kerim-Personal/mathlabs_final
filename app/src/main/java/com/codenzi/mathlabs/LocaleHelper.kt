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
        // HATA DÜZELTİLDİ: setLanguageSelected çağrısı gereksiz olduğu için kaldırıldı.
        // Dili kaydetmek, zaten bir dilin seçildiği anlamına gelir.
        SharedPreferencesManager.saveLanguage(context, languageCode)
    }

    fun applyLanguage(activity: Activity, languageCode: String) {
        persist(activity, languageCode)
        val intent = Intent(activity, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
    }
}
