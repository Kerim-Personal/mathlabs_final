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

    // Call this in attachBaseContext of Activities
    fun onAttach(context: Context): Context {
        val storedLanguage = SharedPreferencesManager.getLanguage(context)
        val isLanguageExplicitlySelected = SharedPreferencesManager.isLanguageSelected(context)

        return if (isLanguageExplicitlySelected && storedLanguage != null) {
            // If a language was explicitly selected before, use it
            setLocale(context, storedLanguage)
        } else {
            // If no language was explicitly selected, use the system's default language.
            // This is primarily for the *very first launch* before a choice is made.
            val defaultSystemLanguage = Locale.getDefault().language
            setLocale(context, defaultSystemLanguage)
        }
    }

    // Call this when the language is actively changed by the user (e.g., in LanguageSelectionActivity)
    fun persist(context: Context, languageCode: String) {
        SharedPreferencesManager.saveLanguage(context, languageCode)
        SharedPreferencesManager.setLanguageSelected(context, true) // Ensure this flag is set to true
    }

    // Call this to apply the selected language and restart the *current* activity.
    // This is typically used in SettingsActivity, NOT for the initial language selection.
    fun applyLanguage(activity: Activity, languageCode: String) {
        persist(activity, languageCode)
        activity.recreate() // Simply recreates the current activity to apply new locale
    }
}