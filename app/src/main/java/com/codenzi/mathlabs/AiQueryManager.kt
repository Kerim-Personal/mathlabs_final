package com.codenzi.mathlabs

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlin.Result

/**
 * AI sorgularını yöneten singleton nesne.
 * Tüm kota kontrolü ve tüketimi sunucudaki Cloud Functions üzerinden yapılır.
 */
object AiQueryManager {

    private val functions: FirebaseFunctions = Firebase.functions

    /**
     * Kullanıcının AI sorgusu yapma hakkı olup olmadığını kontrol eder.
     * Sunucudaki 'checkAiQuota' callable fonksiyonunu çağırır.
     * @return Sorgu hakkı varsa true, yoksa false döner.
     */
    suspend fun canPerformQuery(): Boolean {
        return try {
            val result = functions
                .getHttpsCallable("checkAiQuota")
                .call()
                .await()

            val data = result.data as? Map<*, *>
            (data?.get("allowed") as? Boolean) == true
        } catch (_: Exception) {
            false
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
                    val resultData = task.result?.data as? Map<*, *>
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
