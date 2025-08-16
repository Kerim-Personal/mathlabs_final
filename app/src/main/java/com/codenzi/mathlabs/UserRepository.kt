package com.codenzi.mathlabs

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

object UserRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    suspend fun createUserDocumentIfNotExist() {
        val user = auth.currentUser ?: return
        val userRef = usersCollection.document(user.uid)

        if (userRef.get().await().exists()) {
            return
        }

        val newUser = UserData(
            uid = user.uid,
            email = user.email,
            displayName = user.displayName,
            isPremium = false,
            aiQueriesUsed = 0,
            lastAiQueryReset = null,
            rewardedQueries = 0,
            registrationDate = null
        )
        userRef.set(newUser).await()
    }

    suspend fun getUserData(): UserData? {
        val userId = getCurrentUserId() ?: return null
        return try {
            usersCollection.document(userId).get().await().toObject(UserData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUserField(field: String, value: Any) {
        val userId = getCurrentUserId() ?: return
        try {
            usersCollection.document(userId).update(field, value).await()
        } catch (e: Exception) {
            // Hata yönetimi
        }
    }

    suspend fun updateUserData(data: Map<String, Any>) {
        val userId = getCurrentUserId() ?: return
        try {
            usersCollection.document(userId).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            // Hata yönetimi
        }
    }

    /**
     * YENİ EKLENEN FONKSİYON
     * Mevcut kullanıcının premium olup olmadığını kontrol eder.
     * @return Premium ise true, değilse veya kullanıcı bulunamazsa false döner.
     */
    suspend fun isCurrentUserPremium(): Boolean {
        val userData = getUserData()
        return userData?.isPremium ?: false
    }
}