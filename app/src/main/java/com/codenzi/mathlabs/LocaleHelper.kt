package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun setLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    /**
     * Bu fonksiyon, aktivitelerin attachBaseContext metodunda çağrılmalıdır.
     * Cihazın veya kullanıcının seçtiği dili yükler.
     */
    fun onAttach(context: Context): Context {
        val storedLanguage = SharedPreferencesManager.getLanguage(context)
        val isLanguageExplicitlySelected = SharedPreferencesManager.isLanguageSelected(context)

        return if (isLanguageExplicitlySelected && storedLanguage != null) {
            // Eğer daha önce bir dil seçilmişse, o dili kullan
            setLocale(context, storedLanguage)
        } else {
            // Eğer hiç dil seçilmemişse (ilk açılış), sistemin varsayılan dilini kullan
            val defaultSystemLanguage = Locale.getDefault().language
            setLocale(context, defaultSystemLanguage)
        }
    }

    /**
     * Kullanıcının yaptığı dil seçimini SharedPreferences'a kaydeder.
     */
    fun persist(context: Context, languageCode: String) {
        SharedPreferencesManager.saveLanguage(context, languageCode)
        SharedPreferencesManager.setLanguageSelected(context, true) // Dilin bilinçli olarak seçildiğini işaretler
    }

    /**
     * Seçilen dili uygular ve mevcut aktiviteyi yeniden başlatarak
     * değişikliğin anında yansımasını sağlar. Genellikle ayarlar ekranında kullanılır.
     */
    fun applyLanguage(activity: Activity, languageCode: String) {
        persist(activity, languageCode)
        activity.recreate() // Mevcut aktiviteyi yeniden oluşturur
    }
}