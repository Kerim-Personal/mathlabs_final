package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class BillingManager(private val context: Context, private val lifecycleScope: CoroutineScope) {

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    private val _productDetails = MutableLiveData<Map<String, ProductDetails>>()
    val productDetails: LiveData<Map<String, ProductDetails>> = _productDetails

    // YENİ YAPI: Sadece YENİ satın alımları bildiren tek seferlik olay.
    private val _newPurchaseEvent = MutableLiveData<Event<Boolean>>()
    val newPurchaseEvent: LiveData<Event<Boolean>> = _newPurchaseEvent

    init {
        connectToBillingService()
    }

    private fun connectToBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "BillingClient setup successful.")
                    queryProductDetails()
                    queryPurchases() // Mevcut durumu sessizce senkronize et
                }
            }
            override fun onBillingServiceDisconnected() {
                connectToBillingService()
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId("monthly_premium_plan").setProductType(BillingClient.ProductType.SUBS).build(),
            QueryProductDetailsParams.Product.newBuilder().setProductId("yearly_premium_plan").setProductType(BillingClient.ProductType.SUBS).build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        billingClient.queryProductDetailsAsync(params) { _, productDetailsList ->
            _productDetails.postValue(productDetailsList.associateBy { it.productId })
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails).setOfferToken(offerToken).build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build()
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken).build()
            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // SATIN ALMA BAŞARILI VE ONAYLANDI!
                    lifecycleScope.launch {
                        // 1. Firestore'u güncelle
                        UserRepository.updateUserField("isPremium", true)
                        // 2. Sadece bu anda dinleyiciye haber ver!
                        _newPurchaseEvent.postValue(Event(true))
                    }
                }
            }
        }
    }

    // Bu fonksiyon artık SESSİZCE çalışır. Sadece Firestore'u günceller, event göndermez.
    fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            lifecycleScope.launch {
                val isUserPremium = billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                val currentDbStatus = UserRepository.isCurrentUserPremium()
                // Sadece veritabanındaki durum ile Play Store durumu farklıysa güncelle
                if (isUserPremium != currentDbStatus) {
                    UserRepository.updateUserField("isPremium", isUserPremium)
                }
            }
        }
    }
}