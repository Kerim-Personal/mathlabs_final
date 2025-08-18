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

    // PDF İndirme Kotaları (Timestamp olarak tutulur)
    var premiumPdfDownloadCount: Int = 0,
    @ServerTimestamp
    var lastPdfDownloadReset: Date? = null,

    @ServerTimestamp
    val registrationDate: Date? = null
)
