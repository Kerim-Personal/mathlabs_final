package com.codenzi.mathlabs

import android.content.Context

object AiQueryManager {

    private const val FREE_QUERY_LIMIT = 3
    private const val PREMIUM_QUERY_LIMIT = 500

    fun canPerformQuery(context: Context): Boolean {
        // İlk olarak, ödülle kazanılmış sorgu hakkı var mı diye kontrol et.
        if (SharedPreferencesManager.getRewardedQueryCount(context) > 0) {
            return true
        }

        // Kullanıcının premium durumuna göre kontrol yap.
        return if (SharedPreferencesManager.isUserPremium(context)) {
            SharedPreferencesManager.getPremiumQueryCount(context) < PREMIUM_QUERY_LIMIT
        } else {
            SharedPreferencesManager.getFreeQueryCount(context) < FREE_QUERY_LIMIT
        }
    }

    fun incrementQueryCount(context: Context) {
        // Eğer ödüllü hakkı kullanarak sorgu yaptıysa, onu azalt.
        if (SharedPreferencesManager.getRewardedQueryCount(context) > 0) {
            SharedPreferencesManager.useRewardedQuery(context)
            return
        }

        // Kullanıcının premium durumuna göre doğru sayacı artır.
        if (SharedPreferencesManager.isUserPremium(context)) {
            SharedPreferencesManager.incrementPremiumQueryCount(context)
        } else {
            SharedPreferencesManager.incrementFreeQueryCount(context)
        }
    }

    // Bu fonksiyon, kullanıcıya hangi limitin aşıldığını söylemek için kullanılabilir.
    fun getQuotaExceededMessage(context: Context): String {
        val period = if (SharedPreferencesManager.isUserPremium(context)) {
            context.getString(R.string.period_monthly)
        } else {
            context.getString(R.string.period_daily)
        }
        return context.getString(R.string.ai_quota_exceeded, period)
    }
}