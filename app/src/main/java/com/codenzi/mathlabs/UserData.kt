package com.codenzi.mathlabs

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import kotlin.jvm.JvmField

/**
 * Firestore'daki bir kullanıcı belgesinin yapısını temsil eden veri sınıfı.
 */
data class UserData(
    val uid: String = "",
    val email: String? = "",
    val displayName: String? = "",

    @JvmField
    var isPremium: Boolean = false,

    // AI Sorgu Kotaları
    var aiQueriesUsed: Int = 0,
    @ServerTimestamp
    val lastAiQueryReset: Date? = null,
    var rewardedQueries: Int = 0,

    // PDF İndirme Kotaları (Hata çözümü için eklendi)
    var premiumPdfDownloadCount: Int = 0,
    var lastPdfDownloadReset: Long = 0L, // Zaman damgası için Long kullanmak daha güvenilirdir.

    @ServerTimestamp
    val registrationDate: Date? = null
)
