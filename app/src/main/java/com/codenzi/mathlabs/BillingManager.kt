package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import java.security.MessageDigest

class BillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    // UI yaşam döngüsünden bağımsız işlem scope'u (satın alma/Firestore için)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _productDetails = MutableLiveData<Map<String, ProductDetails>>()
    val productDetails: LiveData<Map<String, ProductDetails>> = _productDetails

    private val _newPurchaseEvent = MutableLiveData<Event<Boolean>>()
    val newPurchaseEvent: LiveData<Event<Boolean>> = _newPurchaseEvent

    // Birden fazla bekleyen iş için callback listesi
    private val onReadyCallbacks = mutableListOf<() -> Unit>()

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "Billing Client başarıyla kuruldu.")
                    queryProductDetails()
                    // Hazır olunca bekleyen tüm işlerin çalıştırılması
                    val toRun = onReadyCallbacks.toList()
                    onReadyCallbacks.clear()
                    toRun.forEach { it.invoke() }
                } else {
                    Log.e("BillingManager", "Billing Client kurulum hatası: ${billingResult.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w("BillingManager", "Billing Client bağlantısı koptu. Yeniden deneniyor...")
                setupBillingClient()
            }
        })
    }

    fun executeOnBillingSetupFinished(listener: () -> Unit) {
        if (::billingClient.isInitialized && billingClient.isReady) {
            listener()
        } else {
            onReadyCallbacks.add(listener)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("BillingManager", "Kullanıcı satın almayı iptal etti.")
        } else {
            Log.e("BillingManager", "Satın alma hatası: ${billingResult.debugMessage}")
        }
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("monthly_premium_plan")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("yearly_premium_plan")
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList)

        billingClient.queryProductDetailsAsync(params.build()) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && !detailsList.isNullOrEmpty()) {
                _productDetails.postValue(detailsList.associateBy { it.productId })
            }
        }
    }

    // Ürün detaylarını yeniden çekmek için public API
    fun refreshProductDetails() {
        if (::billingClient.isInitialized && billingClient.isReady) {
            queryProductDetails()
        } else {
            // Hazır olduğunda tekrar dene (diğer bekleyen işleri etkilemeden sıraya ekle)
            onReadyCallbacks.add { queryProductDetails() }
        }
    }

    // Firebase kullanıcısına bağlı obfuscatedAccountId üretir (SHA-256(uid))
    private fun currentObfuscatedAccountId(): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return sha256(uid)
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xff
            if (i < 0x10) hex.append('0')
            hex.append(i.toString(16))
        }
        return hex.toString()
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails, preferredRecurringPeriod: String? = null) {
        // Tercih edilen döneme (ör. "P1M" veya "P1Y") sahip tekrarlayan fazı içeren teklifi bul
        val chosenOffer = productDetails.subscriptionOfferDetails
            ?.firstOrNull { offer ->
                if (preferredRecurringPeriod == null) return@firstOrNull true
                offer.pricingPhases.pricingPhaseList.any { phase ->
                    phase.billingCycleCount == 0 && phase.billingPeriod == preferredRecurringPeriod
                }
            }
            ?: productDetails.subscriptionOfferDetails?.firstOrNull()
            ?: return

        val offerToken = chosenOffer.offerToken
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingParamsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))

        currentObfuscatedAccountId()?.let { oa ->
            billingParamsBuilder.setObfuscatedAccountId(oa)
        }

        val billingFlowParams = billingParamsBuilder.build()
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Bu satın alma mevcut Firebase kullanıcısına mı ait?
            val expectedOa = currentObfuscatedAccountId()
            val purchaseOa = purchase.accountIdentifiers?.obfuscatedAccountId
            val uidAtPurchase = FirebaseAuth.getInstance().currentUser?.uid
            if (expectedOa == null || purchaseOa == null || expectedOa != purchaseOa || uidAtPurchase == null) {
                Log.w(
                    "BillingManager",
                    "Satın alma bu Firebase kullanıcısına ait görünmüyor veya UID alınamadı. expected=$expectedOa, purchase=$purchaseOa"
                )
                return
            }

            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d("BillingManager", "Satın alma başarıyla onaylandı.")
                        appScope.launch {
                            grantPremiumAccess(uidAtPurchase)
                        }
                    }
                }
            } else {
                appScope.launch {
                    grantPremiumAccess(uidAtPurchase)
                }
            }
        }
    }

    /**
     * Kullanıcıya premium erişim verir (hedef UID'e sabitlenmiş güvenli sürüm).
     * Hem Firestore'u günceller hem de UserRepository aracılığıyla lokal state'i ANINDA tetikler.
     */
    private suspend fun grantPremiumAccess(targetUid: String) {
        // Bu blok iptal edilemez; kullanıcı ekranı değiştirse de tamamlanır.
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                try {
                    // Tek seferde atomik güncelleme (hedef UID için)
                    UserRepository.updateUserFieldsFor(
                        targetUid,
                        mapOf(
                            "isPremium" to true,
                            // Sunucu zamanı kullan
                            "lastPdfDownloadReset" to FieldValue.serverTimestamp(),
                            "premiumPdfDownloadCount" to 0
                        )
                    ).getOrThrow()
                    Log.d("BillingManager", "($targetUid) Firestore'da isPremium ve ilgili alanlar güncellendi.")

                    // Lokal veriyi anında güncelle (yalnızca bu UID hala aktifse)
                    val activeUid = UserRepository.currentUid()
                    if (activeUid == targetUid) {
                        val currentUserData = UserRepository.userDataState.value
                        if (currentUserData != null) {
                            val updatedLocalData = currentUserData.copy(
                                isPremium = true,
                                lastPdfDownloadReset = java.util.Date(),
                                premiumPdfDownloadCount = 0
                            )
                            withContext(Dispatchers.Main) {
                                UserRepository.triggerLocalUpdate(updatedLocalData)
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _newPurchaseEvent.postValue(Event(true))
                    }
                } catch (e: Exception) {
                    Log.e("BillingManager", "Firestore isPremium güncelleme hatası", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Premium aktivasyonunda bir sorun oluştu.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /**
     * Google Play'deki abonelik durumunu kontrol eder ve Firestore ile senkronize eder.
     */
    fun checkAndSyncSubscriptions() {
        if (!::billingClient.isInitialized || !billingClient.isReady) {
            Log.w("BillingManager", "BillingClient hazır değil, senkronizasyon planlandı.")
            // Hazır olduğunda tekrar dene (sıraya ekle)
            onReadyCallbacks.add { checkAndSyncSubscriptions() }
            return
        }

        val myObfuscatedId = currentObfuscatedAccountId()
        val uidSnapshot = FirebaseAuth.getInstance().currentUser?.uid
        if (myObfuscatedId == null || uidSnapshot == null) {
            Log.w("BillingManager", "Firebase kullanıcısı yok, senkronizasyon atlandı.")
            return
        }

        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS)
        billingClient.queryPurchasesAsync(params.build()) { billingResult, activeSubs ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                coroutineScope.launch(Dispatchers.IO) {
                    val userData = UserRepository.getUserDataOnce() ?: return@launch

                    // Cihazda herhangi bir aktif abonelik var mı?
                    val hasAnyActiveSubs = activeSubs.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    // Sadece bu kullanıcıya ait abonelikleri dikkate al
                    val myActiveSubs = activeSubs.filter { it.accountIdentifiers?.obfuscatedAccountId == myObfuscatedId }
                    val isSubscribedOnPlay = myActiveSubs.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }

                    if (isSubscribedOnPlay != userData.isPremium) {
                        Log.d(
                            "BillingManager",
                            "Senkronizasyon: Play durumu (${isSubscribedOnPlay}) ile Firestore durumu (${userData.isPremium}) farklı. Güncelleniyor..."
                        )
                        if (isSubscribedOnPlay) {
                            // Yükseltme: arka planda hedef UID'e güvenli şekilde ver
                            appScope.launch { grantPremiumAccess(uidSnapshot) }
                        } else {
                            // Düşürme: cihazda hiç aktif abonelik yoksa güvenle false yap (hedef UID)
                            if (!hasAnyActiveSubs) {
                                UserRepository.updateUserFieldFor(uidSnapshot, "isPremium", false)
                                val activeUid = UserRepository.currentUid()
                                if (activeUid == uidSnapshot) {
                                    val updatedData = userData.copy(isPremium = false)
                                    withContext(Dispatchers.Main) {
                                        UserRepository.triggerLocalUpdate(updatedData)
                                    }
                                }
                            } else {
                                Log.d(
                                    "BillingManager",
                                    "Diğer bir Google hesabında aktif abonelik var; bu kullanıcıya ait olmadığı için düşürme yapılmadı."
                                )
                            }
                        }
                    } else {
                        Log.d("BillingManager", "Senkronizasyon: Durumlar eşleşiyor, işlem yapılmadı.")
                    }

                    // Onaylanmamışları onayla (sadece bu kullanıcıya ait olanları)
                    myActiveSubs.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                        .forEach { handlePurchase(it) }
                }
            } else {
                Log.e("BillingManager", "Abonelikler sorgulanırken hata: ${billingResult.debugMessage}")
            }
        }
    }

    fun dispose() {
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
        // İsteğe bağlı: uzun ömürlü scope'u uygulama kapanırken iptal etmek isteyebilirsiniz.
        // appScope.cancel()
    }
}