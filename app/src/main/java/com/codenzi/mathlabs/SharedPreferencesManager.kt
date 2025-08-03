package com.codenzi.mathlabs

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import java.util.Calendar

/**
 * Uygulamanın tüm SharedPreferences işlemlerini yöneten singleton nesne.
 */
object SharedPreferencesManager {

    private const val PREFS_NAME = "app_prefs"

    // Anahtar (Key) Tanımlamaları
    private const val KEY_LANGUAGE = "selected_language"
    private const val KEY_LANGUAGE_SELECTED_FLAG = "language_selected_flag"
    private const val KEY_TOUCH_SOUND = "touch_sound_enabled"
    private const val KEY_THEME = "theme_preference"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_PEN_COLOR = "pen_color"
    private const val KEY_PEN_SIZE_TYPE = "pen_size_type"
    private const val KEY_ERASER_SIZE_TYPE = "eraser_size_type"
    private const val KEY_SELECTED_APP_COLOR_THEME = "selected_app_color_theme"
    private const val KEY_LAST_GEMINI_API_CALL_TIMESTAMP = "last_gemini_api_call_timestamp"
    private const val KEY_IS_FIRST_GEMINI_API_CALL = "is_first_gemini_api_call"
    private const val KEY_USER_PREMIUM_STATUS = "user_premium_status"
    private const val KEY_FREE_QUERY_COUNT = "free_query_count"
    private const val KEY_FREE_LAST_RESET_TIMESTAMP = "free_last_reset_timestamp"
    private const val KEY_PREMIUM_QUERY_COUNT = "premium_query_count"
    private const val KEY_PREMIUM_LAST_RESET_TIMESTAMP = "premium_last_reset_timestamp"
    private const val KEY_REWARDED_QUERY_COUNT = "rewarded_query_count"
    private const val KEY_PREMIUM_PDF_DOWNLOAD_COUNT = "premium_pdf_download_count"
    private const val KEY_PREMIUM_PDF_LAST_RESET_TIMESTAMP = "premium_pdf_last_reset_timestamp"

    private const val PREMIUM_PDF_DOWNLOAD_LIMIT = 20


    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Dil ve Tema Ayarları
    fun saveLanguage(context: Context, language: String) {
        getPreferences(context).edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun getLanguage(context: Context): String? {
        return getPreferences(context).getString(KEY_LANGUAGE, null)
    }

    fun setLanguageSelected(context: Context, selected: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_LANGUAGE_SELECTED_FLAG, selected).apply()
    }

    fun isLanguageSelected(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_LANGUAGE_SELECTED_FLAG, false)
    }

    fun saveTheme(context: Context, themeValue: Int) {
        getPreferences(context).edit().putInt(KEY_THEME, themeValue).apply()
    }

    fun getTheme(context: Context): Int {
        return getPreferences(context).getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    fun saveAppColorTheme(context: Context, themeIndex: Int) {
        getPreferences(context).edit().putInt(KEY_SELECTED_APP_COLOR_THEME, themeIndex).apply()
    }

    fun getAppColorTheme(context: Context): Int {
        return getPreferences(context).getInt(KEY_SELECTED_APP_COLOR_THEME, 0)
    }

    // Kullanıcı Bilgileri ve Tercihleri
    fun saveUserName(context: Context, name: String) {
        getPreferences(context).edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(context: Context): String? {
        return getPreferences(context).getString(KEY_USER_NAME, null)
    }

    fun setTouchSoundEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_TOUCH_SOUND, enabled).apply()
    }

    fun isTouchSoundEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_TOUCH_SOUND, true)
    }

    // Çizim Ayarları
    fun savePenColor(context: Context, color: Int) {
        getPreferences(context).edit().putInt(KEY_PEN_COLOR, color).apply()
    }

    fun getPenColor(context: Context): Int {
        return getPreferences(context).getInt(KEY_PEN_COLOR, Color.RED)
    }

    fun savePenSizeType(context: Context, sizeTypeOrdinal: Int) {
        getPreferences(context).edit().putInt(KEY_PEN_SIZE_TYPE, sizeTypeOrdinal).apply()
    }

    fun getPenSizeType(context: Context): Int {
        return getPreferences(context).getInt(KEY_PEN_SIZE_TYPE, BrushSize.MEDIUM.ordinal)
    }

    fun saveEraserSizeType(context: Context, sizeTypeOrdinal: Int) {
        getPreferences(context).edit().putInt(KEY_ERASER_SIZE_TYPE, sizeTypeOrdinal).apply()
    }

    fun getEraserSizeType(context: Context): Int {
        return getPreferences(context).getInt(KEY_ERASER_SIZE_TYPE, BrushSize.MEDIUM.ordinal)
    }

    // Gemini API Kullanım Zaman Damgaları
    fun saveLastGeminiApiCallTimestamp(context: Context, timestamp: Long) {
        getPreferences(context).edit().putLong(KEY_LAST_GEMINI_API_CALL_TIMESTAMP, timestamp).apply()
    }

    fun getLastGeminiApiCallTimestamp(context: Context): Long {
        return getPreferences(context).getLong(KEY_LAST_GEMINI_API_CALL_TIMESTAMP, 0L)
    }

    fun setIsFirstGeminiApiCall(context: Context, isFirst: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_IS_FIRST_GEMINI_API_CALL, isFirst).apply()
    }

    fun getIsFirstGeminiApiCall(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_FIRST_GEMINI_API_CALL, true)
    }

    // Premium Durumu Yönetimi
    // NOT: Bu bir yer tutucudur. Gerçek uygulamada Google Play Faturalandırma Kitaplığı ile entegrasyon gerekir.
    fun setUserAsPremium(context: Context, isPremium: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_USER_PREMIUM_STATUS, isPremium).apply()
    }

    fun isUserPremium(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_USER_PREMIUM_STATUS, false)
    }

    // Ücretsiz Kullanıcı Sorgu Kotası Yönetimi
    fun getFreeQueryCount(context: Context): Int {
        val prefs = getPreferences(context)
        val lastResetTimestamp = prefs.getLong(KEY_FREE_LAST_RESET_TIMESTAMP, 0L)
        val currentTimestamp = System.currentTimeMillis()

        val lastResetCal = Calendar.getInstance().apply { timeInMillis = lastResetTimestamp }
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentTimestamp }

        // Yeni bir güne geçilmişse sayacı sıfırla
        if (lastResetCal.get(Calendar.DAY_OF_YEAR) != currentCal.get(Calendar.DAY_OF_YEAR) ||
            lastResetCal.get(Calendar.YEAR) != currentCal.get(Calendar.YEAR)) {
            prefs.edit()
                .putInt(KEY_FREE_QUERY_COUNT, 0)
                .putLong(KEY_FREE_LAST_RESET_TIMESTAMP, currentTimestamp)
                .apply()
            return 0
        }
        return prefs.getInt(KEY_FREE_QUERY_COUNT, 0)
    }

    fun incrementFreeQueryCount(context: Context) {
        val prefs = getPreferences(context)
        val currentCount = getFreeQueryCount(context)
        prefs.edit().putInt(KEY_FREE_QUERY_COUNT, currentCount + 1).apply()
    }

    // Premium Kullanıcı Sorgu Kotası Yönetimi
    fun getPremiumQueryCount(context: Context): Int {
        val prefs = getPreferences(context)
        val lastResetTimestamp = prefs.getLong(KEY_PREMIUM_LAST_RESET_TIMESTAMP, 0L)
        val currentTimestamp = System.currentTimeMillis()

        val lastResetCal = Calendar.getInstance().apply { timeInMillis = lastResetTimestamp }
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentTimestamp }

        // Yeni bir aya geçilmişse sayacı sıfırla
        if (lastResetCal.get(Calendar.MONTH) != currentCal.get(Calendar.MONTH) ||
            lastResetCal.get(Calendar.YEAR) != currentCal.get(Calendar.YEAR)) {
            prefs.edit()
                .putInt(KEY_PREMIUM_QUERY_COUNT, 0)
                .putLong(KEY_PREMIUM_LAST_RESET_TIMESTAMP, currentTimestamp)
                .apply()
            return 0
        }
        return prefs.getInt(KEY_PREMIUM_QUERY_COUNT, 0)
    }

    fun incrementPremiumQueryCount(context: Context) {
        val prefs = getPreferences(context)
        val currentCount = getPremiumQueryCount(context)
        prefs.edit().putInt(KEY_PREMIUM_QUERY_COUNT, currentCount + 1).apply()
    }

    // Ödüllü Sorgu Hakkı Yönetimi
    fun getRewardedQueryCount(context: Context): Int {
        return getPreferences(context).getInt(KEY_REWARDED_QUERY_COUNT, 0)
    }

    fun addRewardedQueries(context: Context, amount: Int) {
        val currentCount = getRewardedQueryCount(context)
        getPreferences(context).edit().putInt(KEY_REWARDED_QUERY_COUNT, currentCount + amount).apply()
    }

    fun useRewardedQuery(context: Context) {
        val currentCount = getRewardedQueryCount(context)
        if (currentCount > 0) {
            getPreferences(context).edit().putInt(KEY_REWARDED_QUERY_COUNT, currentCount - 1).apply()
        }
    }

    // Premium Kullanıcı PDF İndirme Kotası Yönetimi
    fun getPremiumPdfDownloadCount(context: Context): Int {
        val prefs = getPreferences(context)
        val lastResetTimestamp = prefs.getLong(KEY_PREMIUM_PDF_LAST_RESET_TIMESTAMP, 0L)
        val currentTimestamp = System.currentTimeMillis()

        val lastResetCal = Calendar.getInstance().apply { timeInMillis = lastResetTimestamp }
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentTimestamp }

        // Yeni bir aya geçilmişse sayacı sıfırla
        if (lastResetCal.get(Calendar.MONTH) != currentCal.get(Calendar.MONTH) ||
            lastResetCal.get(Calendar.YEAR) != currentCal.get(Calendar.YEAR)) {
            prefs.edit()
                .putInt(KEY_PREMIUM_PDF_DOWNLOAD_COUNT, 0)
                .putLong(KEY_PREMIUM_PDF_LAST_RESET_TIMESTAMP, currentTimestamp)
                .apply()
            return 0
        }
        return prefs.getInt(KEY_PREMIUM_PDF_DOWNLOAD_COUNT, 0)
    }

    fun incrementPremiumPdfDownloadCount(context: Context) {
        val prefs = getPreferences(context)
        val currentCount = getPremiumPdfDownloadCount(context)
        prefs.edit().putInt(KEY_PREMIUM_PDF_DOWNLOAD_COUNT, currentCount + 1).apply()
    }

    fun canDownloadPdf(context: Context): Boolean {
        if (!isUserPremium(context)) {
            return true // Premium olmayanlar için şimdilik bir kısıtlama yok
        }
        return getPremiumPdfDownloadCount(context) < PREMIUM_PDF_DOWNLOAD_LIMIT
    }
}