package com.codenzi.mathlabs

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class UserData(
    val uid: String = "",
    val email: String? = "",
    val displayName: String? = "",
    var isPremium: Boolean = false,
    var aiQueriesUsed: Int = 0,
    @ServerTimestamp // Bu alanın sunucu zaman damgasıyla dolmasını sağlar
    val lastAiQueryReset: Date? = null,
    var rewardedQueries: Int = 0,
    @ServerTimestamp
    val registrationDate: Date? = null
)