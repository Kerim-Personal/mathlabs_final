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
                    // Uygulama başlarken senkronizasyonu yapması için Splash'tan çağrılacak.
                    // checkAndSyncSubscriptions()
                } else {
                    Log.e("BillingManager", "Billing Client kurulum hatası: ${billingResult.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w("BillingManager", "Billing Client bağlantısı koptu.")
            }
        })
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
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
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
        }
    }

    private suspend fun grantPremiumAccess() {
        withContext(Dispatchers.IO) {
            try {
                UserRepository.updateUserField("isPremium", true).getOrThrow()
                Log.d("BillingManager", "Firestore'da isPremium alanı başarıyla true yapıldı.")
                val currentUserData = UserRepository.userDataState.value
                if (currentUserData != null) {
                    val updatedLocalData = currentUserData.copy(isPremium = true)
                    withContext(Dispatchers.Main) {
                        UserRepository.triggerLocalUpdate(updatedLocalData)
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

    // *** DEĞİŞTİRİLEN VE YENİ GÖREV EKlenen FONKSİYON ***
    fun checkAndSyncSubscriptions() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS)
        billingClient.queryPurchasesAsync(params.build()) { billingResult, activeSubs ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                coroutineScope.launch {
                    val isSubscribedOnPlay = !activeSubs.isNullOrEmpty()
                    val isPremiumOnFirestore = UserRepository.userDataState.value?.isPremium ?: false

                    if (!isSubscribedOnPlay && isPremiumOnFirestore) {
                        Log.d("BillingManager", "Senkronizasyon: Google Play'de aktif abonelik yok. Premium geri alınıyor.")
                        UserRepository.updateUserField("isPremium", false)
                    } else if (isSubscribedOnPlay && !isPremiumOnFirestore) {
                        Log.d("BillingManager", "Senkronizasyon: Google Play'de aktif abonelik bulundu. Premium veriliyor.")
                        UserRepository.updateUserField("isPremium", true)
                    } else {
                        Log.d("BillingManager", "Senkronizasyon: Durumlar eşleşiyor, işlem yapılmadı.")
                    }

                    // Ayrıca yarım kalmış onaylanmamış işlemleri de tamamla
                    activeSubs?.forEach { purchase ->
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                            handlePurchase(purchase)
                        }
                    }
                }
            } else {
                Log.e("BillingManager", "Abonelikler sorgulanırken hata: ${billingResult.debugMessage}")
            }
        }
    }
}