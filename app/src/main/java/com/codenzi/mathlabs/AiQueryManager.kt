package com.codenzi.mathlabs

import android.content.Context

/**
 * AI sorgu kotalarını ve ödüllü hakları yöneten singleton nesne.
 */
object AiQueryManager {

    private const val FREE_QUERY_LIMIT = 3
    private const val PREMIUM_QUERY_LIMIT = 1000

    /**
     * Kullanıcının yeni bir sorgu yapıp yapamayacağını kontrol eder.
     * Öncelik sırası:
     * 1. Ödülle kazanılmış haklar
     * 2. Premium kullanıcı hakları
     * 3. Standart kullanıcı hakları
     * @param context SharedPreferences'a erişim için gereklidir.
     * @return Sorgu hakkı varsa true, yoksa false döner.
     */
    fun canPerformQuery(context: Context): Boolean {
        // 1. Önce ödülle kazanılmış bir hak var mı diye kontrol et.
        if (SharedPreferencesManager.getRewardedQueryCount(context) > 0) {
            return true
        }

        // 2. Ödül hakkı yoksa, premium veya standart kullanıcı durumuna göre kontrol et.
        return if (SharedPreferencesManager.isUserPremium(context)) {
            SharedPreferencesManager.getPremiumQueryCount(context) < PREMIUM_QUERY_LIMIT
        } else {
            SharedPreferencesManager.getFreeQueryCount(context) < FREE_QUERY_LIMIT
        }
    }

    /**
     * Kullanıcının sorgu sayacını yönetir.
     * Öncelikli olarak ödülle kazanılmış hakları kullanır.
     * Bu fonksiyon, yeni bir AI isteği gönderilmeden hemen önce çağrılmalıdır.
     * @param context SharedPreferences'a erişim için gereklidir.
     */
    fun incrementQueryCount(context: Context) {
        // 1. Eğer ödülle kazanılmış hak varsa, önce onu kullan.
        if (SharedPreferencesManager.getRewardedQueryCount(context) > 0) {
            SharedPreferencesManager.useRewardedQuery(context)
            return
        }

        // 2. Ödül hakkı yoksa, premium veya standart kullanıcı sayacını artır.
        if (SharedPreferencesManager.isUserPremium(context)) {
            SharedPreferencesManager.incrementPremiumQueryCount(context)
        } else {
            SharedPreferencesManager.incrementFreeQueryCount(context)
        }
    }
}