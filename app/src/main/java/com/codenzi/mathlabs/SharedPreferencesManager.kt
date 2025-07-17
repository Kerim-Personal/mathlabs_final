package com.codenzi.mathlabs

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import android.util.Log

object SharedPreferencesManager {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    private const val KEY_LANGUAGE_SELECTED_FLAG = "language_selected_flag"
    // KEY_HAPTIC_FEEDBACK sabiti kaldırıldı.
    private const val KEY_TOUCH_SOUND = "touch_sound_enabled"
    private const val KEY_THEME = "theme_preference"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_PEN_COLOR = "pen_color"
    private const val KEY_PEN_SIZE_TYPE = "pen_size_type"
    private const val KEY_ERASER_SIZE_TYPE = "eraser_size_type"
    private const val KEY_SELECTED_APP_COLOR_THEME = "selected_app_color_theme"
    private const val KEY_LAST_GEMINI_API_CALL_TIMESTAMP = "last_gemini_api_call_timestamp"
    private const val KEY_IS_FIRST_GEMINI_API_CALL = "is_first_gemini_api_call"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveUserName(context: Context, name: String) {
        getPreferences(context).edit().putString(KEY_USER_NAME, name).apply()
        Log.d("ThemeDebug", "SharedPreferencesManager - Kullanıcı adı kaydedildi: $name")
    }

    fun getUserName(context: Context): String? {
        val name = getPreferences(context).getString(KEY_USER_NAME, null)
        Log.d("ThemeDebug", "SharedPreferencesManager - Kullanıcı adı alındı: $name")
        return name
    }

    fun savePenColor(context: Context, color: Int) {
        getPreferences(context).edit().putInt(KEY_PEN_COLOR, color).apply()
        Log.d("ThemeDebug", "SharedPreferencesManager - Kalem rengi kaydedildi: $color")
    }

    fun getPenColor(context: Context): Int {
        val color = getPreferences(context).getInt(KEY_PEN_COLOR, Color.RED)
        Log.d("ThemeDebug", "SharedPreferencesManager - Kalem rengi alındı: $color")
        return color
    }

    fun savePenSizeType(context: Context, sizeTypeOrdinal: Int) {
        getPreferences(context).edit().putInt(KEY_PEN_SIZE_TYPE, sizeTypeOrdinal).apply()
        Log.d("ThemeDebug", "SharedPreferencesManager - Kalem boyutu kaydedildi: $sizeTypeOrdinal")
    }

    fun getPenSizeType(context: Context): Int {
        val size = getPreferences(context).getInt(KEY_PEN_SIZE_TYPE, DrawingModeType.MEDIUM.ordinal)
        Log.d("ThemeDebug", "SharedPreferencesManager - Kalem boyutu alındı: $size")
        return size
    }

    fun saveEraserSizeType(context: Context, sizeTypeOrdinal: Int) {
        getPreferences(context).edit().putInt(KEY_ERASER_SIZE_TYPE, sizeTypeOrdinal).apply()
        Log.d("ThemeDebug", "SharedPreferencesManager - Silgi boyutu kaydedildi: $sizeTypeOrdinal")
    }

    fun getEraserSizeType(context: Context): Int {
        val size = getPreferences(context).getInt(KEY_ERASER_SIZE_TYPE, DrawingModeType.MEDIUM.ordinal)
        Log.d("ThemeDebug", "SharedPreferencesManager - Silgi boyutu alındı: $size")
        return size
    }

    fun saveAppColorTheme(context: Context, themeIndex: Int) {
        getPreferences(context).edit().putInt(KEY_SELECTED_APP_COLOR_THEME, themeIndex).apply()
        Log.d("ThemeDebug", "SharedPreferencesManager - Renk teması kaydedildi: $themeIndex")
    }

    fun getAppColorTheme(context: Context): Int {
        val themeIndex = getPreferences(context).getInt(KEY_SELECTED_APP_COLOR_THEME, 0)
        Log.d("ThemeDebug", "SharedPreferencesManager - Renk teması alındı: $themeIndex")
        return themeIndex
    }

    fun saveLanguage(context: Context, language: String) {
        getPreferences(context).edit().putString(KEY_LANGUAGE, language).apply()
        Log.d("ThemeDebug", "SharedPreferencesManager - Dil kaydedildi: $language")
    }

    fun getLanguage(context: Context): String? {
        val language = getPreferences(context).getString(KEY_LANGUAGE, null)
        Log.d("ThemeDebug", "SharedPreferencesManager - Dil alındı: $language")
        return language
    }

    fun setLanguageSelected(context: Context, selected: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_LANGUAGE_SELECTED_FLAG, selected).apply()
        Log.d("ThemeDebug", "SharedPreferencesManager - Dil seçildi: $selected")
    }

    fun isLanguageSelected(context: Context): Boolean {
        val selected = getPreferences(context).getBoolean(KEY_LANGUAGE_SELECTED_FLAG, false)
        Log.d("ThemeDebug", "SharedPreferencesManager - Dil seçildi alındı: $selected")
        return selected
    }

    // setHapticFeedbackEnabled ve isHapticFeedbackEnabled fonksiyonları kaldırıldı.

    fun setTouchSoundEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_TOUCH_SOUND, enabled).apply()
        Log.d("ThemeDebug", "SharedPreferencesManager - Dokunma sesi: $enabled")
    }

    fun isTouchSoundEnabled(context: Context): Boolean {
        val enabled = getPreferences(context).getBoolean(KEY_TOUCH_SOUND, true)
        Log.d("ThemeDebug", "SharedPreferencesManager - Dokunma sesi alındı: $enabled")
        return enabled
    }

    fun saveTheme(context: Context, themeValue: Int) {
        getPreferences(context).edit().putInt(KEY_THEME, themeValue).apply()
        Log.d("ThemeDebug", "SharedPreferencesManager - Tema kaydedildi: $themeValue")
    }

    fun getTheme(context: Context): Int {
        val theme = getPreferences(context).getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        Log.d("ThemeDebug", "SharedPreferencesManager - Tema alındı: $theme")
        return theme
    }

    fun saveLastGeminiApiCallTimestamp(context: Context, timestamp: Long) {
        getPreferences(context).edit().putLong(KEY_LAST_GEMINI_API_CALL_TIMESTAMP, timestamp).apply()
        Log.d("GeminiApiUsage", "Last Gemini API call timestamp saved: $timestamp")
    }

    fun getLastGeminiApiCallTimestamp(context: Context): Long {
        val timestamp = getPreferences(context).getLong(KEY_LAST_GEMINI_API_CALL_TIMESTAMP, 0L)
        Log.d("GeminiApiUsage", "Last Gemini API call timestamp retrieved: $timestamp")
        return timestamp
    }

    fun setIsFirstGeminiApiCall(context: Context, isFirst: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_IS_FIRST_GEMINI_API_CALL, isFirst).apply()
        Log.d("GeminiApiUsage", "isFirstGeminiApiCall set to: $isFirst")
    }

    fun getIsFirstGeminiApiCall(context: Context): Boolean {
        val isFirst = getPreferences(context).getBoolean(KEY_IS_FIRST_GEMINI_API_CALL, true)
        Log.d("GeminiApiUsage", "isFirstGeminiApiCall retrieved: $isFirst")
        return isFirst
    }
}