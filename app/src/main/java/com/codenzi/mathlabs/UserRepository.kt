package com.codenzi.mathlabs

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * Kullanıcı verilerini Firestore üzerinden yöneten merkezi singleton nesne.
 */
object UserRepository {

    private const val TAG = "UserRepository"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var userDocumentListener: ListenerRegistration? = null

    // UserData'yı canlı olarak tutacak ve yayınlayacak StateFlow.
    private val _userDataState = MutableStateFlow<UserData?>(null)
    val userDataState: StateFlow<UserData?> = _userDataState

    init {
        // Kullanıcı oturum açtığında veya kapattığında dinleyiciyi otomatik olarak yönet.
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                startListeningForUserData()
            } else {
                stopListeningForUserData()
            }
        }
    }

    /**
     * Firestore'daki kullanıcı belgesini canlı olarak dinlemeye başlar.
     */
    private fun startListeningForUserData() {
        val userId = auth.currentUser?.uid ?: return
        userDocumentListener?.remove() // Önceki dinleyiciyi temizle

        val userDocRef = firestore.collection("users").document(userId)
        userDocumentListener = userDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Kullanıcı verisi dinlenirken hata oluştu.", error)
                _userDataState.value = null
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val userData = snapshot.toObject(UserData::class.java)
                _userDataState.value = userData
                Log.d(TAG, "Kullanıcı verisi güncellendi: $userData")
            } else {
                Log.d(TAG, "Kullanıcı belgesi bulunamadı.")
                _userDataState.value = null
            }
        }
    }

    /**
     * Dinleyiciyi durdurur ve StateFlow'u temizler.
     */
    private fun stopListeningForUserData() {
        userDocumentListener?.remove()
        userDocumentListener = null
        _userDataState.value = null
        Log.d(TAG, "Kullanıcı verisi dinleyicisi durduruldu.")
    }

    /**
     * StateFlow'daki mevcut kullanıcı verisini lokal olarak günceller.
     */
    fun triggerLocalUpdate(updatedUserData: UserData) {
        _userDataState.value = updatedUserData
        Log.d(TAG, "Lokal kullanıcı verisi anında güncellendi: $updatedUserData")
    }

    /**
     * Premium kontrolü
     */
    fun isCurrentUserPremium(): Boolean {
        return _userDataState.value?.isPremium ?: false
    }

    /**
     * Anlık olarak tek seferlik kullanıcı verisini çeker.
     */
    suspend fun getUserDataOnce(): UserData? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            firestore.collection("users").document(userId).get().await().toObject(UserData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Tek seferlik kullanıcı verisi çekilirken hata.", e)
            null
        }
    }

    /**
     * Belirli bir alanı günceller
     */
    suspend fun updateUserField(field: String, value: Any): Result<Unit> {
        val userId = auth.currentUser?.uid
        return if (userId != null) {
            try {
                firestore.collection("users").document(userId).update(field, value).await()
                Log.d(TAG, "$field alanı başarıyla güncellendi.")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "$field alanı güncellenirken hata oluştu.", e)
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("Kullanıcı oturum açmamış."))
        }
    }

    /**
     * Sayaç alanını artırır
     */
    suspend fun incrementUserCounter(fieldName: String, incrementBy: Long = 1): Result<Unit> {
        val userId = auth.currentUser?.uid
        return if (userId != null) {
            try {
                val increment = FieldValue.increment(incrementBy)
                firestore.collection("users").document(userId).update(fieldName, increment).await()
                Log.d(TAG, "$fieldName sayacı $incrementBy artırıldı.")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "$fieldName sayacı artırılırken hata oluştu.", e)
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("Kullanıcı oturum açmamış."))
        }
    }

    /**
     * PDF indirme hakkını kontrol eder ve gerekirse sıfırlar
     */
    suspend fun canDownloadPdf(): Boolean {
        val userData = _userDataState.value ?: getUserDataOnce() ?: return false

        if (!userData.isPremium) {
            return false
        }

        // Aylık sıfırlama kontrolü
        val currentTime = System.currentTimeMillis()
        val lastResetTime = userData.lastPdfDownloadReset

        val currentCal = Calendar.getInstance()
        currentCal.timeInMillis = currentTime

        val lastResetCal = Calendar.getInstance()
        lastResetCal.timeInMillis = lastResetTime

        // Ay veya yıl değiştiyse sıfırla
        if (currentCal.get(Calendar.MONTH) != lastResetCal.get(Calendar.MONTH) ||
            currentCal.get(Calendar.YEAR) != lastResetCal.get(Calendar.YEAR)) {

            // Sayacı sıfırla
            updateUserField("premiumPdfDownloadCount", 0).getOrThrow()
            updateUserField("lastPdfDownloadReset", currentTime).getOrThrow()

            // Lokal state'i güncelle
            val updatedData = userData.copy(
                premiumPdfDownloadCount = 0,
                lastPdfDownloadReset = currentTime
            )
            triggerLocalUpdate(updatedData)

            return true // Yeni ay, limit sıfırlandı
        }

        // Mevcut ay içinde limit kontrolü
        val limit = 20
        return userData.premiumPdfDownloadCount < limit
    }

    /**
     * Kullanıcı için Firestore'da veri oluşturur veya günceller
     */
    suspend fun createOrUpdateUserData() {
        val user = auth.currentUser ?: return
        val userDocRef = firestore.collection("users").document(user.uid)

        try {
            val document = userDocRef.get().await()
            if (!document.exists()) {
                // Yeni kullanıcı için başlangıç verisi oluştur
                val initialData = UserData(
                    uid = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    isPremium = false,
                    aiQueriesUsed = 0,
                    lastAiQueryReset = null,
                    rewardedQueries = 0,
                    premiumPdfDownloadCount = 0,
                    lastPdfDownloadReset = System.currentTimeMillis(),
                    registrationDate = null
                )
                userDocRef.set(initialData).await()
                Log.d(TAG, "Yeni kullanıcı için başlangıç verisi oluşturuldu.")
            } else {
                // Mevcut kullanıcı için eksik alanları güncelle
                val existingData = document.toObject(UserData::class.java)
                if (existingData != null) {
                    val updates = mutableMapOf<String, Any>()

                    // Eksik alanları kontrol et ve güncelle
                    if (existingData.displayName != user.displayName) {
                        updates["displayName"] = user.displayName ?: ""
                    }
                    if (existingData.email != user.email) {
                        updates["email"] = user.email ?: ""
                    }
                    if (existingData.lastPdfDownloadReset == 0L) {
                        updates["lastPdfDownloadReset"] = System.currentTimeMillis()
                    }

                    if (updates.isNotEmpty()) {
                        userDocRef.update(updates).await()
                        Log.d(TAG, "Kullanıcı verisi güncellendi: $updates")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kullanıcı verisi kontrol edilirken/oluşturulurken hata oluştu.", e)
            throw e
        }
    }
}