package com.codenzi.mathlabs

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlin.Result // Kotlin'in kendi Result sınıfını kullanıyoruz.

/**
 * AI sorgularını yöneten singleton nesne.
 * Bu sınıf artık doğrudan Gemini API'sini çağırmak yerine,
 * güvenli bir şekilde Firebase Cloud Function'a istek gönderir.
 */
object AiQueryManager {

    // Firebase Functions servisine erişim sağlıyoruz.
    // Fonksiyonunuzu farklı bir bölgeye deploy ettiyseniz, parantez içine "europe-west1" gibi belirtebilirsiniz.
    private val functions: FirebaseFunctions = Firebase.functions

    // --- KULLANICI KOTA YÖNETİMİ (Bu kısım aynı kalıyor) ---
    // Bu mantık, kullanıcı hakkı bittiğinde gereksiz sunucu çağrıları yapmamızı engeller.
    private const val FREE_QUERY_LIMIT = 3
    private const val PREMIUM_QUERY_LIMIT = 500

    fun canPerformQuery(context: Context): Boolean {
        if (SharedPreferencesManager.getRewardedQueryCount(context) > 0) {
            return true
        }
        // DÜZELTME: Mevcut kullanıcının premium olup olmadığı kontrol ediliyor.
        return if (SharedPreferencesManager.isCurrentUserPremium(context)) {
            SharedPreferencesManager.getPremiumQueryCount(context) < PREMIUM_QUERY_LIMIT
        } else {
            SharedPreferencesManager.getFreeQueryCount(context) < FREE_QUERY_LIMIT
        }
    }

    fun incrementQueryCount(context: Context) {
        if (SharedPreferencesManager.getRewardedQueryCount(context) > 0) {
            SharedPreferencesManager.useRewardedQuery(context)
            return
        }
        // DÜZELTME: Mevcut kullanıcının premium olup olmadığı kontrol ediliyor.
        if (SharedPreferencesManager.isCurrentUserPremium(context)) {
            SharedPreferencesManager.incrementPremiumQueryCount(context)
        } else {
            SharedPreferencesManager.incrementFreeQueryCount(context)
        }
    }

    fun getQuotaExceededMessage(context: Context): String {
        // DÜZELTME: Mevcut kullanıcının premium olup olmadığı kontrol ediliyor.
        val period = if (SharedPreferencesManager.isCurrentUserPremium(context)) {
            context.getString(R.string.period_monthly)
        } else {
            context.getString(R.string.period_daily)
        }
        return context.getString(R.string.ai_quota_exceeded, period)
    }

    // --- YENİ: SUNUCUYA İSTEK GÖNDEREN FONKSİYON ---

    /**
     * Verilen bir metni (prompt) Firebase'deki 'askGemini' fonksiyonuna gönderir.
     * Sonucu asenkron olarak bir callback ile döndürür.
     * @param prompt Gemini'ye gönderilecek olan soru veya metin.
     * @param callback İşlem sonucunu (başarılı ise cevap, başarısız ise hata) döndüren fonksiyon.
     */
    fun getResponseFromServer(prompt: String, callback: (Result<String>) -> Unit) {
        // Sunucuya gönderilecek veriyi bir harita (map) olarak hazırlıyoruz.
        // 'prompt' anahtarı, index.js dosyasında beklediğimiz 'request.data.prompt' ile eşleşmelidir.
        val data = hashMapOf(
            "prompt" to prompt
        )

        functions
            .getHttpsCallable("askGemini") // Cloud Function'ımızın adı
            .call(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Sunucudan gelen cevabı parse ediyoruz.
                    val resultData = task.result?.data as? Map<String, Any>
                    val geminiResult = resultData?.get("result") as? String

                    if (geminiResult != null) {
                        // Cevap başarılı ve doğru formatta ise callback ile geri döndürüyoruz.
                        callback(Result.success(geminiResult))
                    } else {
                        // Cevap geldi ama formatı bozuksa hata döndürüyoruz.
                        callback(Result.failure(Exception("Sunucudan geçersiz veya boş yanıt alındı.")))
                    }
                } else {
                    // Sunucuya bağlanırken bir hata oluştuysa, bu hatayı geri döndürüyoruz.
                    callback(Result.failure(task.exception ?: Exception("Bilinmeyen bir fonksiyon hatası oluştu.")))
                }
            }
    }
}