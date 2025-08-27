package com.codenzi.mathlabs

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import com.google.firebase.Timestamp

/**
 * Kullanıcı verilerini Firestore üzerinden yöneten merkezi singleton nesne.
 * Uygulamanın "tek doğru kaynağı" (Single Source of Truth) budur.
 */
object UserRepository {

    private const val TAG = "UserRepository"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val functions: FirebaseFunctions = Firebase.functions

    private var userDocumentListener: ListenerRegistration? = null

    // UserData'yı canlı olarak tutacak ve yayınlayacak StateFlow.
    // Arayüz (Activity/Fragment) bu state'i dinleyerek anında güncellenir.
    private val _userDataState = MutableStateFlow<UserData?>(null)
    val userDataState: StateFlow<UserData?> = _userDataState

    init {
        // Kullanıcı oturum açtığında veya kapattığında dinleyiciyi otomatik olarak yönet.
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                startListeningForUserData()
            } else {
                // Oturum kapandığında dinleyiciyi durdur ve lokal veriyi temizle.
                stopListeningForUserData()
            }
        }
    }

    /**
     * Şu anki Firebase kullanıcısının UID'i (varsa)
     */
    fun currentUid(): String? = auth.currentUser?.uid

    /**
     * Firestore'daki kullanıcı belgesini canlı olarak dinlemeye başlar.
     * Veri her değiştiğinde _userDataState'i günceller.
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
                try {
                    // Geriye dönük uyumluluk: lastPdfDownloadReset sayısal ise Timestamp'e çevir
                    val raw = snapshot.get("lastPdfDownloadReset")
                    if (raw is Number) {
                        val ms = raw.toLong()
                        val ts = Timestamp(Date(ms))
                        // Sessiz migrasyon
                        userDocRef.update("lastPdfDownloadReset", ts)
                            .addOnSuccessListener { Log.d(TAG, "lastPdfDownloadReset alanı Timestamp'e migrasyon edildi.") }
                            .addOnFailureListener { e -> Log.w(TAG, "lastPdfDownloadReset migrasyonu başarısız.", e) }
                    }

                    val userData = snapshot.toObject(UserData::class.java)
                    _userDataState.value = userData
                    Log.d(TAG, "Kullanıcı verisi Firestore'dan güncellendi: $userData")
                } catch (e: Exception) {
                    Log.e(TAG, "Snapshot parse edilirken hata oluştu.", e)
                    _userDataState.value = null
                }
            } else {
                Log.d(TAG, "Kullanıcı belgesi bulunamadı.")
                _userDataState.value = null
            }
        }
    }

    /**
     * Dinleyiciyi durdurur ve StateFlow'u temizler. Oturum kapatıldığında çağrılır.
     */
    private fun stopListeningForUserData() {
        userDocumentListener?.remove()
        userDocumentListener = null
        _userDataState.value = null // Lokal veriyi temizle
        Log.d(TAG, "Kullanıcı verisi dinleyicisi durduruldu ve lokal veri temizlendi.")
    }

    /**
     * YENİ/GÜNCELLENMİŞ FONKSİYON: StateFlow'daki mevcut kullanıcı verisini lokal olarak günceller.
     * Bu fonksiyon, Firestore'dan gelecek güncellemeyi beklemeden arayüzün
     * anında yenilenmesini sağlamak için kritik öneme sahiptir.
     * Örneğin, bir satın alma işleminden hemen sonra kullanılır.
     */
    fun triggerLocalUpdate(updatedUserData: UserData) {
        _userDataState.value = updatedUserData
        Log.d(TAG, "Lokal kullanıcı verisi anında güncellendi: $updatedUserData")
    }

    /**
     * Premium durumunu doğrudan canlı state üzerinden kontrol eder.
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
     * Belirli bir alanı Firestore'da günceller.
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
     * Bir sayaç alanını Firestore'da artırır/azaltır.
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
     * PDF indirme hakkını kontrol eder ve gerekirse sıfırlar.
     */
    suspend fun requestPdfDownloadSlot(): Result<Boolean> {
        return try {
            val data = functions.getHttpsCallable("registerPdfDownload").call()
                .continueWith { it.result?.data as? Map<*, *> }.await()
            val allowed = (data?.get("allowed") as? Boolean) == true
            if (allowed) {
                // Lokal state'i tek seferlik yenile
                val refreshed = getUserDataOnce()
                refreshed?.let { triggerLocalUpdate(it) }
            }
            Result.success(allowed)
        } catch (e: Exception) {
            Log.e(TAG, "requestPdfDownloadSlot hata", e)
            Result.failure(e)
        }
    }

    suspend fun grantAdReward(amount: Int = 3): Result<Int> {
        return try {
            val payload = mapOf("amount" to amount)
            val data = functions.getHttpsCallable("grantAdReward").call(payload)
                .continueWith { it.result?.data as? Map<*, *> }.await()
            val granted = (data?.get("granted") as? Boolean) == true
            val added = if (granted) (data?.get("added") as? Number)?.toInt() ?: 0 else 0
            if (granted) {
                val refreshed = getUserDataOnce()
                refreshed?.let { triggerLocalUpdate(it) }
            }
            if (granted) Result.success(added) else Result.failure(Exception("Reward not granted"))
        } catch (e: Exception) {
            Log.e(TAG, "grantAdReward hata", e)
            Result.failure(e)
        }
    }

    /**
     * Kullanıcı için Firestore'da veri oluşturur veya eksik alanları günceller.
     */
    suspend fun createOrUpdateUserData() {
        val user = auth.currentUser ?: return
        val userDocRef = firestore.collection("users").document(user.uid)

        try {
            val document = userDocRef.get().await()
            if (!document.exists()) {
                val initialData = UserData(
                    uid = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    isPremium = false,
                    aiQueriesUsed = 0,
                    lastAiQueryReset = null,
                    rewardedQueries = 0,
                    premiumPdfDownloadCount = 0,
                    // Sunucu tarafında @ServerTimestamp ile doldurulsun
                    lastPdfDownloadReset = null,
                    registrationDate = null // Firestore bunu sunucu zaman damgasıyla dolduracak
                )
                userDocRef.set(initialData).await()
                Log.d(TAG, "Yeni kullanıcı için başlangıç verisi oluşturuldu.")
            } else {
                // Mevcut kullanıcı için displayName, email gibi bilgileri güncelle
                val updates = mutableMapOf<String, Any?>()
                if (document.getString("displayName") != user.displayName) {
                    updates["displayName"] = user.displayName
                }
                if (document.getString("email") != user.email) {
                    updates["email"] = user.email
                }
                if (updates.isNotEmpty()) {
                    userDocRef.update(updates).await()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kullanıcı verisi kontrol edilirken/oluşturulurken hata oluştu.", e)
            throw e
        }
    }

    /**
     * Birden fazla alanı Firestore'da tek seferde atomik olarak günceller.
     */
    suspend fun updateUserFields(fields: Map<String, Any>): Result<Unit> {
        val userId = auth.currentUser?.uid
        return if (userId != null) {
            try {
                firestore.collection("users").document(userId).update(fields).await()
                Log.d(TAG, "Alanlar başarıyla güncellendi: ${fields.keys}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Alanlar güncellenirken hata oluştu.", e)
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("Kullanıcı oturum açmamış."))
        }
    }
}