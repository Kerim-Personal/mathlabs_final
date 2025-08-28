package com.codenzi.mathlabs

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore kullanıcı belgesi (sadeleştirilmiş).
 * Premium sadece aktif abonelik süresine göre belirlenir.
 */
data class UserData(
    val uid: String = "",
    val email: String? = "",
    val displayName: String? = "",

    // Abonelik meta
    var subscriptionExpiryMillis: Long? = null,
    var subscriptionAutoRenewing: Boolean? = null,

    // AI Kotaları
    var aiQueriesUsed: Int = 0,
    @ServerTimestamp
    val lastAiQueryReset: Date? = null,
    var rewardedQueries: Int = 0,

    // PDF İndirme Kotaları
    var premiumPdfDownloadCount: Int = 0,
    @ServerTimestamp
    var lastPdfDownloadReset: Date? = null,

    @ServerTimestamp
    val registrationDate: Date? = null
) {
    fun isSubscriptionActive(now: Long = System.currentTimeMillis()): Boolean {
        val exp = subscriptionExpiryMillis ?: return false
        return exp > now
    }
}
