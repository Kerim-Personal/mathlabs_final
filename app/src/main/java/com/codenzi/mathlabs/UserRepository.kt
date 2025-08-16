package com.codenzi.mathlabs

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

object UserRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private var userDataListener: ListenerRegistration? = null

    // Değişiklikleri dinlemek için StateFlow
    private val _userDataState = MutableStateFlow<UserData?>(null)
    val userDataState: StateFlow<UserData?> = _userDataState

    /**
     * Kullanıcı verisindeki değişiklikleri dinlemeye başlar.
     * Bu fonksiyon, kullanıcı giriş yaptığında (örneğin MainActivity'de) çağrılmalıdır.
     */
    fun startListeningForUserData() {
        // Zaten dinliyorsak, eskisini durdur.
        stopListeningForUserData()

        val userId = getCurrentUserId() ?: return
        userDataListener = usersCollection.document(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Hata durumunu loglayabilirsin.
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                _userDataState.value = snapshot.toObject(UserData::class.java)
            } else {
                _userDataState.value = null
            }
        }
    }

    /**
     * Veri dinleyicisini durdurur.
     * Kullanıcı çıkış yaptığında veya uygulama kapandığında çağrılmalıdır.
     */
    fun stopListeningForUserData() {
        userDataListener?.remove()
        userDataListener = null
    }


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
            lastAiQueryReset = Date(), // null yerine başlangıç değeri
            rewardedQueries = 0,
            registrationDate = Date() // null yerine başlangıç değeri
        )
        userRef.set(newUser).await()
    }

    /**
     * Anlık olarak tek seferlik kullanıcı verisini çeker.
     * Genellikle dinleyici başlatılmadan önceki ilk kontrol için kullanılır.
     */
    suspend fun getUserDataOnce(): UserData? {
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
     * Mevcut kullanıcının premium olup olmadığını anlık olarak StateFlow'dan kontrol eder.
     * @return Premium ise true, değilse veya kullanıcı bulunamazsa false döner.
     */
    fun isCurrentUserPremium(): Boolean {
        return _userDataState.value?.isPremium ?: false
    }
}