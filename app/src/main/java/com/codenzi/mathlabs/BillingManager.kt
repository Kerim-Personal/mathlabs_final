package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
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
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "BillingClient is ready.")
                    queryAvailableProducts()
                    // OTOMATİK SENKRONİZASYON: Uygulama açıldığında alımları sorgula ve senkronize et.
                    queryAndSyncPurchases()
                } else {
                    Log.e("BillingManager", "BillingClient setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d("BillingManager", "Billing service disconnected. Retrying...")
            }
        })
    }

    private fun queryAvailableProducts() {
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
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Log.e("BillingManager", "User is not logged in, cannot process purchase.")
            return
        }

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _isPremium.postValue(true)
                        SharedPreferencesManager.setUserAsPremium(context, currentUserId, true)
                        Log.d("BillingManager", "Purchase acknowledged successfully for user: $currentUserId")
                    }
                }
            } else {
                _isPremium.postValue(true)
                SharedPreferencesManager.setUserAsPremium(context, currentUserId, true)
            }
        }
    }

    /**
     * Google Play'den mevcut abonelikleri sorgular ve yerel hafızayı (SharedPreferences)
     * bu bilgiyle senkronize eder.
     */
    fun queryAndSyncPurchases() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Log.d("BillingManager", "Cannot sync purchases, user is not logged in.")
            _isPremium.postValue(false)
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)

        billingClient.queryPurchasesAsync(params.build()) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasActiveSubscription = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                SharedPreferencesManager.setUserAsPremium(context, currentUserId, hasActiveSubscription)
                _isPremium.postValue(hasActiveSubscription)
                Log.d("BillingManager", "Purchases synced for user: $currentUserId. Is premium: $hasActiveSubscription")
            } else {
                Log.e("BillingManager", "Sync purchases query failed: ${billingResult.debugMessage}")
                // Sorgu başarısız olursa, yerel veriye güvenmeye devam et
                _isPremium.postValue(SharedPreferencesManager.isCurrentUserPremium(context))
            }
        }
    }
}