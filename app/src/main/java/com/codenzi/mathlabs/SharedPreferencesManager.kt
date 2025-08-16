package com.codenzi.mathlabs

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
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
    private const val KEY_PREMIUM_USER_IDS = "premium_user_ids"
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
        getPreferences(context).edit { putString(KEY_LANGUAGE, language) }
    }

    fun getLanguage(context: Context): String? {
        return getPreferences(context).getString(KEY_LANGUAGE, null)
    }

    fun setLanguageSelected(context: Context, selected: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_LANGUAGE_SELECTED_FLAG, selected) }
    }

    fun saveTheme(context: Context, themeValue: Int) {
        getPreferences(context).edit { putInt(KEY_THEME, themeValue) }
    }

    fun getTheme(context: Context): Int {
        return getPreferences(context).getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    // Kullanıcı Bilgileri ve Tercihleri
    fun saveUserName(context: Context, name: String) {
        getPreferences(context).edit { putString(KEY_USER_NAME, name) }
    }

    fun getUserName(context: Context): String? {
        return getPreferences(context).getString(KEY_USER_NAME, null)
    }

    fun setTouchSoundEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_TOUCH_SOUND, enabled) }
    }

    fun isTouchSoundEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_TOUCH_SOUND, false)
    }

    // --- DEĞİŞİKLİKLER BURADA ---
    @Deprecated("Premium durumu artık Firestore üzerinden yönetiliyor. Bu metot yanıltıcı olabilir. UserRepository.updateUserField(\"isPremium\", isPremium) kullanın.")
    fun setUserAsPremium(context: Context, userId: String, isPremium: Boolean) {
        val prefs = getPreferences(context)
        val premiumIds = prefs.getStringSet(KEY_PREMIUM_USER_IDS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        if (isPremium) {
            premiumIds.add(userId)
        } else {
            premiumIds.remove(userId)
        }
        prefs.edit { putStringSet(KEY_PREMIUM_USER_IDS, premiumIds) }
    }

    @Deprecated("Bu metot güncel olmayan (stale) veri döndürebilir. Bunun yerine her zaman anlık ve doğru veriyi sağlayan UserRepository.isCurrentUserPremium() kullanın.", ReplaceWith("UserRepository.isCurrentUserPremium()"))
    fun isUserPremium(context: Context, userId: String?): Boolean {
        if (userId.isNullOrEmpty()) return false
        val prefs = getPreferences(context)
        val premiumIds = prefs.getStringSet(KEY_PREMIUM_USER_IDS, emptySet()) ?: emptySet()
        return premiumIds.contains(userId)
    }

    @Deprecated("Bu metot güncel olmayan (stale) veri döndürebilir. Bunun yerine her zaman anlık ve doğru veriyi sağlayan UserRepository.isCurrentUserPremium() kullanın.", ReplaceWith("UserRepository.isCurrentUserPremium()"))
    fun isCurrentUserPremium(context: Context): Boolean {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        return isUserPremium(context, currentUserId)
    }


    // Ücretsiz Kullanıcı Sorgu Kotası Yönetimi
    fun getFreeQueryCount(context: Context): Int {
        val prefs = getPreferences(context)
        val lastResetTimestamp = prefs.getLong(KEY_FREE_LAST_RESET_TIMESTAMP, 0L)
        val currentTimestamp = System.currentTimeMillis()

        val lastResetCal = Calendar.getInstance().apply { timeInMillis = lastResetTimestamp }
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentTimestamp }

        if (lastResetCal.get(Calendar.DAY_OF_YEAR) != currentCal.get(Calendar.DAY_OF_YEAR) ||
            lastResetCal.get(Calendar.YEAR) != currentCal.get(Calendar.YEAR)) {
            prefs.edit {
                putInt(KEY_FREE_QUERY_COUNT, 0)
                putLong(KEY_FREE_LAST_RESET_TIMESTAMP, currentTimestamp)
            }
            return 0
        }
        return prefs.getInt(KEY_FREE_QUERY_COUNT, 0)
    }

    fun incrementFreeQueryCount(context: Context) {
        val prefs = getPreferences(context)
        val currentCount = getFreeQueryCount(context)
        prefs.edit { putInt(KEY_FREE_QUERY_COUNT, currentCount + 1) }
    }

    // Premium Kullanıcı Sorgu Kotası Yönetimi
    fun getPremiumQueryCount(context: Context): Int {
        val prefs = getPreferences(context)
        val lastResetTimestamp = prefs.getLong(KEY_PREMIUM_LAST_RESET_TIMESTAMP, 0L)
        val currentTimestamp = System.currentTimeMillis()

        val lastResetCal = Calendar.getInstance().apply { timeInMillis = lastResetTimestamp }
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentTimestamp }

        if (lastResetCal.get(Calendar.MONTH) != currentCal.get(Calendar.MONTH) ||
            lastResetCal.get(Calendar.YEAR) != currentCal.get(Calendar.YEAR)) {
            prefs.edit {
                putInt(KEY_PREMIUM_QUERY_COUNT, 0)
                putLong(KEY_PREMIUM_LAST_RESET_TIMESTAMP, currentTimestamp)
            }
            return 0
        }
        return prefs.getInt(KEY_PREMIUM_QUERY_COUNT, 0)
    }

    fun incrementPremiumQueryCount(context: Context) {
        val prefs = getPreferences(context)
        val currentCount = getPremiumQueryCount(context)
        prefs.edit { putInt(KEY_PREMIUM_QUERY_COUNT, currentCount + 1) }
    }

    // Ödüllü Sorgu Hakkı Yönetimi
    fun getRewardedQueryCount(context: Context): Int {
        return getPreferences(context).getInt(KEY_REWARDED_QUERY_COUNT, 0)
    }

    fun addRewardedQueries(context: Context, amount: Int) {
        val currentCount = getRewardedQueryCount(context)
        getPreferences(context).edit { putInt(KEY_REWARDED_QUERY_COUNT, currentCount + amount) }
    }

    fun useRewardedQuery(context: Context) {
        val currentCount = getRewardedQueryCount(context)
        if (currentCount > 0) {
            getPreferences(context).edit { putInt(KEY_REWARDED_QUERY_COUNT, currentCount - 1) }
        }
    }

    // Premium Kullanıcı PDF İndirme Kotası Yönetimi
    fun getPremiumPdfDownloadCount(context: Context): Int {
        val prefs = getPreferences(context)
        val lastResetTimestamp = prefs.getLong(KEY_PREMIUM_PDF_LAST_RESET_TIMESTAMP, 0L)
        val currentTimestamp = System.currentTimeMillis()

        val lastResetCal = Calendar.getInstance().apply { timeInMillis = lastResetTimestamp }
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentTimestamp }

        if (lastResetCal.get(Calendar.MONTH) != currentCal.get(Calendar.MONTH) ||
            lastResetCal.get(Calendar.YEAR) != currentCal.get(Calendar.YEAR)) {
            prefs.edit {
                putInt(KEY_PREMIUM_PDF_DOWNLOAD_COUNT, 0)
                putLong(KEY_PREMIUM_PDF_LAST_RESET_TIMESTAMP, currentTimestamp)
            }
            return 0
        }
        return prefs.getInt(KEY_PREMIUM_PDF_DOWNLOAD_COUNT, 0)
    }

    fun incrementPremiumPdfDownloadCount(context: Context) {
        val prefs = getPreferences(context)
        val currentCount = getPremiumPdfDownloadCount(context)
        prefs.edit { putInt(KEY_PREMIUM_PDF_DOWNLOAD_COUNT, currentCount + 1) }
    }

    // DİKKAT: Bu metot, kullanımdan kaldırılan 'isCurrentUserPremium' metodunu kullanıyor.
    // Bu metodu kullanan yerlerde, önce asenkron olarak UserRepository'den premium durumunu kontrol etmelisiniz.
    fun canDownloadPdf(context: Context): Boolean {
        if (!isCurrentUserPremium(context)) { // Bu çağrı artık IDE'de bir uyarı gösterecektir.
            return true
        }
        return getPremiumPdfDownloadCount(context) < PREMIUM_PDF_DOWNLOAD_LIMIT
    }
}