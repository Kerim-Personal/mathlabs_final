package com.codenzi.mathlabs

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import java.util.Calendar
import java.util.Date
import kotlin.Result

/**
 * AI sorgularını yöneten singleton nesne.
 * Bu sınıf artık doğrudan Gemini API'sini çağırmak yerine,
 * güvenli bir şekilde Firebase Cloud Function'a istek gönderir.
 * Kullanıcı kota yönetimi için SharedPreferences yerine Cloud Firestore kullanır.
 */
object AiQueryManager {

    private val functions: FirebaseFunctions = Firebase.functions

    private const val FREE_QUERY_LIMIT = 3
    private const val PREMIUM_QUERY_LIMIT = 500

    /**
     * Kullanıcının AI sorgusu yapma hakkı olup olmadığını kontrol eder.
     * Veriyi Cloud Firestore'dan çeker.
     * @return Sorgu hakkı varsa true, yoksa false döner.
     */
    suspend fun canPerformQuery(): Boolean {
        val userData = UserRepository.getUserData() ?: return false

        // 1. Öncelik: Ödüllü sorgu hakkı var mı?
        if (userData.rewardedQueries > 0) {
            return true
        }

        val lastResetDate = userData.lastAiQueryReset ?: Date(0) // Null ise çok eski bir tarih ata
        val lastResetCal = Calendar.getInstance().apply { time = lastResetDate }
        val currentCal = Calendar.getInstance()
        var needsReset = false

        if (userData.isPremium) {
            // 2. Premium Kullanıcı: Aylık kontrol
            if (lastResetCal.get(Calendar.MONTH) != currentCal.get(Calendar.MONTH) ||
                lastResetCal.get(Calendar.YEAR) != currentCal.get(Calendar.YEAR)) {
                needsReset = true
            }
        } else {
            // 3. Ücretsiz Kullanıcı: Günlük kontrol
            if (lastResetCal.get(Calendar.DAY_OF_YEAR) != currentCal.get(Calendar.DAY_OF_YEAR) ||
                lastResetCal.get(Calendar.YEAR) != currentCal.get(Calendar.YEAR)) {
                needsReset = true
            }
        }

        // Eğer yeni bir periyoda girildiyse, sayacı sıfırla.
        if (needsReset) {
            UserRepository.updateUserData(mapOf(
                "aiQueriesUsed" to 0,
                "lastAiQueryReset" to com.google.firebase.firestore.FieldValue.serverTimestamp() // Sunucu zamanını kullan
            ))
            return true // Sayacı sıfırlandığı için hakkı var.
        }

        // Mevcut periyottaki hakkını kontrol et.
        val limit = if (userData.isPremium) PREMIUM_QUERY_LIMIT else FREE_QUERY_LIMIT
        return userData.aiQueriesUsed < limit
    }

    /**
     * Kullanıcının sorgu sayısını artırır.
     * Önce ödüllü hakkı kullanır, yoksa normal sorgu sayacını artırır.
     */
    suspend fun incrementQueryCount() {
        val userData = UserRepository.getUserData() ?: return

        if (userData.rewardedQueries > 0) {
            UserRepository.updateUserField("rewardedQueries", userData.rewardedQueries - 1)
            return
        }

        // Eğer sayaç daha önce hiç sıfırlanmamışsa, ilk sorguyla birlikte sıfırlama tarihini de ata.
        if (userData.lastAiQueryReset == null) {
            UserRepository.updateUserData(mapOf(
                "aiQueriesUsed" to userData.aiQueriesUsed + 1,
                "lastAiQueryReset" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ))
        } else {
            UserRepository.updateUserField("aiQueriesUsed", userData.aiQueriesUsed + 1)
        }
    }

    /**
     * Verilen bir metni (prompt) Firebase'deki 'askGemini' fonksiyonuna gönderir.
     * Sonucu asenkron olarak bir callback ile döndürür.
     * @param prompt Gemini'ye gönderilecek olan soru veya metin.
     * @param callback İşlem sonucunu (başarılı ise cevap, başarısız ise hata) döndüren fonksiyon.
     */
    fun getResponseFromServer(prompt: String, callback: (Result<String>) -> Unit) {
        val data = hashMapOf("prompt" to prompt)

        functions
            .getHttpsCallable("askGemini")
            .call(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val resultData = task.result?.data as? Map<String, Any>
                    val geminiResult = resultData?.get("result") as? String

                    if (geminiResult != null) {
                        callback(Result.success(geminiResult))
                    } else {
                        callback(Result.failure(Exception("Sunucudan geçersiz veya boş yanıt alındı.")))
                    }
                } else {
                    callback(Result.failure(task.exception ?: Exception("Bilinmeyen bir fonksiyon hatası oluştu.")))
                }
            }
    }
}