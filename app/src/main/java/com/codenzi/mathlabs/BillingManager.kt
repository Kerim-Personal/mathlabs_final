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
                    // Kurulum bitince listener'ı tetikle
                    onBillingSetupFinishedListener?.invoke()
                } else {
                    Log.e("BillingManager", "Billing Client kurulum hatası: ${billingResult.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w("BillingManager", "Billing Client bağlantısı koptu.")
                // Yeniden bağlanmayı dene
                setupBillingClient()
            }
        })
    }

    /**
     * BillingClient hazır olduğunda bir işlem gerçekleştirmek için kullanılır.
     */
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

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Satın alma başarılı
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
                // Zaten onaylanmış, sadece premium erişimi ver
                coroutineScope.launch {
                    grantPremiumAccess()
                }
            }
        }
    }

    private suspend fun grantPremiumAccess() {
        withContext(Dispatchers.IO) {
            try {
                // Firestore'da isPremium'u true yap
                UserRepository.updateUserField("isPremium", true).getOrThrow()

                // PDF indirme kotası için son sıfırlama zamanını ayarla
                UserRepository.updateUserField("lastPdfDownloadReset", System.currentTimeMillis()).getOrThrow()
                UserRepository.updateUserField("premiumPdfDownloadCount", 0).getOrThrow()

                Log.d("BillingManager", "Firestore'da isPremium alanı başarıyla true yapıldı.")

                // --- EKLENECEK EN ÖNEMLİ KISIM BURASI ---
                // Lokal state'i anında güncelle. Bu, arayüzün anında yenilenmesini sağlar.
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
                // --- EKLENECEK KISIM SONU ---


                // Event'i tetikle (Bu zaten vardı, yerinde kalmalı)
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
     * Google Play'deki abonelik durumunu kontrol eder ve Firestore ile senkronize eder
     */
    fun checkAndSyncSubscriptions() {
        if (!billingClient.isReady) {
            Log.w("BillingManager", "BillingClient hazır değil, senkronizasyon atlanıyor.")
            return
        }

        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS)
        billingClient.queryPurchasesAsync(params.build()) { billingResult, activeSubs ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                coroutineScope.launch {
                    try {
                        val userData = UserRepository.getUserDataOnce()
                        if (userData == null) {
                            Log.w("BillingManager", "Kullanıcı verisi alınamadı, senkronizasyon atlanıyor.")
                            return@launch
                        }

                        val isSubscribedOnPlay = !activeSubs.isNullOrEmpty() &&
                                activeSubs.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        val isPremiumOnFirestore = userData.isPremium

                        when {
                            !isSubscribedOnPlay && isPremiumOnFirestore -> {
                                // Abonelik iptal edilmiş veya süresi dolmuş
                                Log.d("BillingManager", "Senkronizasyon: Google Play'de aktif abonelik yok. Premium geri alınıyor.")
                                UserRepository.updateUserField("isPremium", false)

                                // Lokal state'i güncelle
                                val updatedData = userData.copy(isPremium = false)
                                withContext(Dispatchers.Main) {
                                    UserRepository.triggerLocalUpdate(updatedData)
                                    Toast.makeText(context, "Premium aboneliğiniz sona erdi.", Toast.LENGTH_LONG).show()
                                }
                            }
                            isSubscribedOnPlay && !isPremiumOnFirestore -> {
                                // Abonelik var ama Firestore'da yok
                                Log.d("BillingManager", "Senkronizasyon: Google Play'de aktif abonelik bulundu. Premium veriliyor.")
                                grantPremiumAccess()
                            }
                            else -> {
                                Log.d("BillingManager", "Senkronizasyon: Durumlar eşleşiyor, işlem yapılmadı.")
                            }
                        }

                        // Onaylanmamış satın almaları onayla
                        activeSubs?.forEach { purchase ->
                            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                                handlePurchase(purchase)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BillingManager", "Abonelik senkronizasyonu sırasında hata", e)
                    }
                }
            } else {
                Log.e("BillingManager", "Abonelikler sorgulanırken hata: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * BillingClient'ı temizle
     */
    fun dispose() {
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}