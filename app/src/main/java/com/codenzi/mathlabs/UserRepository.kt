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
 * Bu sınıf, mevcut kullanıcının tüm verileri için "tek doğru kaynak" (single source of truth) görevi görür.
 */
object UserRepository {

    private const val TAG = "UserRepository"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var userDocumentListener: ListenerRegistration? = null

    // Değişiklik 1: UserData'yı canlı olarak tutacak ve yayınlayacak StateFlow.
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
     * Değişiklik 2: Firestore'daki kullanıcı belgesini canlı olarak dinlemeye başlar.
     * Herhangi bir değişiklik olduğunda _userDataState'i günceller.
     */
    private fun startListeningForUserData() {
        val userId = auth.currentUser?.uid ?: return
        userDocumentListener?.remove() // Önceki dinleyiciyi temizle

        val userDocRef = firestore.collection("users").document(userId)
        userDocumentListener = userDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) { // HATA DÜZELTİLDİ: 'nil' -> 'null'
                Log.e(TAG, "Kullanıcı verisi dinlenirken hata oluştu.", error)
                _userDataState.value = null
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val userData = snapshot.toObject(UserData::class.java)
                _userDataState.value = userData // StateFlow'u en güncel veriyle besle
                Log.d(TAG, "Kullanıcı verisi güncellendi: $userData")
            } else {
                Log.d(TAG, "Kullanıcı belgesi bulunamadı.")
                _userDataState.value = null // HATA DÜZELTİLDİ: 'nil' -> 'null'
            }
        }
    }

    /**
     * Değişiklik 3: Dinleyiciyi durdurur ve StateFlow'u temizler (kullanıcı çıkış yaptığında).
     */
    private fun stopListeningForUserData() {
        userDocumentListener?.remove()
        userDocumentListener = null
        _userDataState.value = null // StateFlow'u temizle
        Log.d(TAG, "Kullanıcı verisi dinleyicisi durduruldu.")
    }

    /**
     * Değişiklik 4: Premium kontrolü artık doğrudan canlı veriyi tutan StateFlow üzerinden yapılır.
     */
    fun isCurrentUserPremium(): Boolean {
        return _userDataState.value?.isPremium ?: false
    }

    /**
     * Anlık olarak tek seferlik kullanıcı verisini çeker.
     * Dinleyici henüz veri getirmeden önce hızlı bir kontrol gerektiğinde kullanılır.
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

    suspend fun updateUserField(field: String, value: Any): Result<Unit> {
        val userId = auth.currentUser?.uid
        return if (userId != null) { // HATA DÜZELTİLDİ: 'nil' -> 'null'
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

    suspend fun incrementUserCounter(fieldName: String, incrementBy: Long = 1): Result<Unit> {
        val userId = auth.currentUser?.uid
        return if (userId != null) { // HATA DÜZELTİLDİ: 'nil' -> 'null'
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

    fun canDownloadPdf(): Boolean {
        val userData = _userDataState.value ?: return false
        if (!userData.isPremium) {
            return false
        }
        val limit = 20
        // HATA DÜZELTİLDİ: UserData'ya eklenen yeni alan kullanılıyor.
        return userData.premiumPdfDownloadCount < limit
    }

    /**
     * Oturum açan yeni kullanıcı için Firestore'da başlangıç verilerini oluşturur (eğer mevcut değilse).
     * Bu fonksiyon LoginActivity'de çağrılmalıdır.
     */
    suspend fun createInitialUserDataIfNotExist() {
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
                    premiumPdfDownloadCount = 0,
                    lastPdfDownloadReset = System.currentTimeMillis()
                )
                userDocRef.set(initialData).await()
                Log.d(TAG, "Yeni kullanıcı için başlangıç verisi oluşturuldu.")
            } else {
                Log.d(TAG, "Kullanıcı verisi zaten mevcut.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kullanıcı verisi kontrol edilirken/oluşturulurken hata oluştu.", e)
        }
    }
}