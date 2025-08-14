// app/src/main/java/com/codenzi/mathlabs/BillingManager.kt
package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BillingManager(private val context: Context, private val coroutineScope: CoroutineScope) {

    private lateinit var billingClient: BillingClient
    private val _productDetails = MutableLiveData<Map<String, ProductDetails>>()
    val productDetails: LiveData<Map<String, ProductDetails>> = _productDetails

    private val _isPremium = MutableLiveData<Boolean>()
    val isPremium: LiveData<Boolean> = _isPremium

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("BillingManager", "User canceled the purchase flow.")
        } else {
            Log.e("BillingManager", "Purchase Error: ${billingResult.debugMessage}")
        }
    }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases() // Bu satır geri eklendi
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "BillingClient is ready.")
                    queryAvailableProducts()
                    checkPurchases()
                } else {
                    Log.e("BillingManager", "BillingClient setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d("BillingManager", "Billing service disconnected. Retrying...")
                // İsteğe bağlı olarak yeniden bağlanma mantığı eklenebilir.
            }
        })
    }

    private fun queryAvailableProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("monthly_premium_plan") // Google Play Console'daki ürün ID'niz
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("yearly_premium_plan") // Google Play Console'daki ürün ID'niz
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList)

        billingClient.queryProductDetailsAsync(params.build()) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                val productDetailsMap = productDetailsList.associateBy { it.productId }
                _productDetails.postValue(productDetailsMap)
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(productDetails.subscriptionOfferDetails?.first()?.offerToken ?: "")
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _isPremium.postValue(true)
                        SharedPreferencesManager.setUserAsPremium(context, true)
                        Log.d("BillingManager", "Purchase acknowledged successfully.")
                    }
                }
            } else {
                _isPremium.postValue(true)
                SharedPreferencesManager.setUserAsPremium(context, true)
            }
        }
    }

    fun checkPurchases() {
        coroutineScope.launch(Dispatchers.IO) {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)

            billingClient.queryPurchasesAsync(params.build()) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val isUserPremium = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    _isPremium.postValue(isUserPremium)
                    SharedPreferencesManager.setUserAsPremium(context, isUserPremium)
                }
            }
        }
    }
}