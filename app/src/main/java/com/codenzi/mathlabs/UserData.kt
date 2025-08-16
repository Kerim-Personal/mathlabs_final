package com.codenzi.mathlabs

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import kotlin.jvm.JvmField // Bu satırı ekleyin

data class UserData(
    val uid: String = "",
    val email: String? = "",
    val displayName: String? = "",

    // *** YENİ EKLENEN ANNOTATION ***
    // Bu, Firestore'un bu alanı her zaman doğru bulmasını ve
    // "No setter/field" uyarısını engellemesini sağlar.
    @JvmField
    var isPremium: Boolean = false,

    var aiQueriesUsed: Int = 0,
    @ServerTimestamp
    val lastAiQueryReset: Date? = null,
    var rewardedQueries: Int = 0,
    @ServerTimestamp
    val registrationDate: Date? = null
)