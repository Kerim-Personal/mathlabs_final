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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest

class BillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    private val _productDetails = MutableLiveData<Map<String, ProductDetails>>()
    val productDetails: LiveData<Map<String, ProductDetails>> = _productDetails

    private val _newPurchaseEvent = MutableLiveData<Event<Boolean>>()
    val newPurchaseEvent: LiveData<Event<Boolean>> = _newPurchaseEvent

    private var onBillingSetupFinishedListener: (() -> Unit)? = null

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
                    onBillingSetupFinishedListener?.invoke()
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
        if (billingClient.isReady) {
            listener()
        } else {
            onBillingSetupFinishedListener = listener
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

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingParamsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))

        // Satın almayı bu Firebase kullanıcısına bağla
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
            if (expectedOa == null || purchaseOa == null || expectedOa != purchaseOa) {
                Log.w(
                    "BillingManager",
                    "Satın alma bu Firebase kullanıcısına ait görünmüyor. expected=$expectedOa, purchase=$purchaseOa"
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
                        coroutineScope.launch {
                            grantPremiumAccess()
                        }
                    }
                }
            } else {
                coroutineScope.launch {
                    grantPremiumAccess()
                }
            }
        }
    }

    /**
     * YENİ/GÜNCELLENMİŞ FONKSİYON: Kullanıcıya premium erişim verir.
     * Hem Firestore'u günceller hem de UserRepository aracılığıyla lokal state'i ANINDA tetikler.
     */
    private suspend fun grantPremiumAccess() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Firestore'u güncelle
                UserRepository.updateUserField("isPremium", true).getOrThrow()
                UserRepository.updateUserField("lastPdfDownloadReset", System.currentTimeMillis()).getOrThrow()
                UserRepository.updateUserField("premiumPdfDownloadCount", 0).getOrThrow()
                Log.d("BillingManager", "Firestore'da isPremium alanı başarıyla true yapıldı.")

                // 2. Lokal veriyi ANINDA güncelle
                val currentUserData = UserRepository.userDataState.value
                if (currentUserData != null) {
                    val updatedLocalData = currentUserData.copy(
                        isPremium = true,
                        lastPdfDownloadReset = System.currentTimeMillis(),
                        premiumPdfDownloadCount = 0
                    )
                    // Ana thread'e geçerek lokal state'i tetikle
                    withContext(Dispatchers.Main) {
                        UserRepository.triggerLocalUpdate(updatedLocalData)
                    }
                }

                // 3. Satın alma olayını dinleyen (varsa) diğer arayüzlere haber ver
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

    /**
     * Google Play'deki abonelik durumunu kontrol eder ve Firestore ile senkronize eder.
     */
    fun checkAndSyncSubscriptions() {
        if (!billingClient.isReady) {
            Log.w("BillingManager", "BillingClient hazır değil, senkronizasyon planlandı.")
            // Hazır olduğunda tekrar dene
            onBillingSetupFinishedListener = {
                checkAndSyncSubscriptions()
            }
            return
        }

        val myObfuscatedId = currentObfuscatedAccountId()
        if (myObfuscatedId == null) {
            Log.w("BillingManager", "Firebase kullanıcısı yok, senkronizasyon atlandı.")
            return
        }

        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS)
        billingClient.queryPurchasesAsync(params.build()) { billingResult, activeSubs ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                coroutineScope.launch(Dispatchers.IO) {
                    val userData = UserRepository.getUserDataOnce() ?: return@launch

                    // Cihazda herhangi bir aktif abonelik var mı?
                    val hasAnyActiveSubs = activeSubs?.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } == true
                    // Sadece bu kullanıcıya ait abonelikleri dikkate al
                    val myActiveSubs = activeSubs?.filter { it.accountIdentifiers?.obfuscatedAccountId == myObfuscatedId }
                    val isSubscribedOnPlay = myActiveSubs?.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } == true

                    if (isSubscribedOnPlay != userData.isPremium) {
                        Log.d(
                            "BillingManager",
                            "Senkronizasyon: Play durumu (${isSubscribedOnPlay}) ile Firestore durumu (${userData.isPremium}) farklı. Güncelleniyor..."
                        )
                        if (isSubscribedOnPlay) {
                            // Yükseltme: yalnızca bu kullanıcıya ait aktif abonelik varsa ver
                            grantPremiumAccess()
                        } else {
                            // Düşürme: cihazda hiç aktif abonelik yoksa güvenle false yap
                            if (!hasAnyActiveSubs) {
                                UserRepository.updateUserField("isPremium", false)
                                val updatedData = userData.copy(isPremium = false)
                                withContext(Dispatchers.Main) {
                                    UserRepository.triggerLocalUpdate(updatedData)
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
                    myActiveSubs?.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                        ?.forEach { handlePurchase(it) }
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
    }
}