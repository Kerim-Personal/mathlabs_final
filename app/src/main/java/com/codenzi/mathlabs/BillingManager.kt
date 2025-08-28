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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.tasks.await

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

    private val functions: FirebaseFunctions = Firebase.functions

    // Aynı token için birden fazla paralel aktivasyon denemesini engellemek için
    private val activatingTokens = Collections.synchronizedSet(mutableSetOf<String>())

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

            // Sunucuda doğrulama + Firestore güncellemesi. Başarılıysa event tetikle.
            appScope.launch {
                try {
                    val activationOk = activatePremiumOnServer(purchase)
                    if (activationOk) {
                        _newPurchaseEvent.postValue(Event(true))
                    }
                } catch (e: Exception) {
                    Log.e("BillingManager", "activatePremium çağrısı başarısız", e)
                }
            }
        }
    }

    /**
     * Satın alma token'ını sunucuya göndererek premium aktivasyonu yapar.
     * Başarılı olursa true döner. Sunucu Firestore isPremium alanını günceller.
     */
    private suspend fun activatePremiumOnServer(purchase: Purchase): Boolean = withContext(Dispatchers.IO) {
        val productId = purchase.products.firstOrNull() ?: return@withContext false
        val packageName = context.packageName
        val purchaseToken = purchase.purchaseToken
        if (!activatingTokens.add(purchaseToken)) { Log.d("BillingManager", "activatePremium zaten işleniyor token=$purchaseToken"); return@withContext false }
        try {
            val data = mapOf("packageName" to packageName, "productId" to productId, "purchaseToken" to purchaseToken)
            val result = functions.getHttpsCallable("activatePremium").call(data).continueWith { it.result?.data as? Map<*, *> }.await()
            val success = (result?.get("success") as? Boolean) == true
            val bypass = (result?.get("bypass") as? Boolean) == true
            val expiry = (result?.get("expiry") as? Number)?.toLong()
            if (success) {
                val current = UserRepository.userDataState.value
                if (current != null && expiry != null && current.subscriptionExpiryMillis != expiry) {
                    UserRepository.triggerLocalUpdate(current.copy(subscriptionExpiryMillis = expiry))
                }
                if (bypass) Log.w("BillingManager", "activatePremium BYPASS (test)!" )
                if (!purchase.isAcknowledged) { acknowledgePurchaseSafely(purchase.purchaseToken) }
            } else {
                Log.e("BillingManager", "activatePremium sunucu sonucu success=false result=$result")
                withContext(Dispatchers.Main) { Toast.makeText(context, "Abonelik doğrulanamadı.", Toast.LENGTH_LONG).show() }
            }
            success
        } catch (e: Exception) {
            Log.e("BillingManager", "Sunucu abonelik aktivasyonu hatası", e)
            val userMessage = if (e is FirebaseFunctionsException) {
                when (e.code) {
                    FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "Satın alma verileri geçersiz."
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "Google Play doğrulaması başarısız."
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "Çok hızlı deneme yapıldı."
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Oturum geçersiz, yeniden giriş yapın."
                    else -> "Aktivasyon başarısız: ${e.code.name}"
                }
            } else "Aktivasyon hata: ${e.localizedMessage}"
            withContext(Dispatchers.Main) { Toast.makeText(context, userMessage, Toast.LENGTH_LONG).show() }
            false
        } finally { activatingTokens.remove(purchaseToken) }
    }

    private fun acknowledgePurchaseSafely(token: String) {
        if (!::billingClient.isInitialized || !billingClient.isReady) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        billingClient.acknowledgePurchase(params) { br ->
            if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("BillingManager", "Satın alma acknowledge edildi (isteğe bağlı yedek).")
            } else {
                Log.w("BillingManager", "Satın alma acknowledge edilemedi: ${br.debugMessage}")
            }
        }
    }

    // Eski yaklaşım: doğrudan Firestore güncelleyen grantPremiumAccess kaldırıldı.
    @Deprecated("Sunucu tarafı doğrulama kullanılıyor.")
    private suspend fun grantPremiumAccess(targetUid: String) { /* Artık kullanılmıyor */ }

    // checkAndSyncSubscriptions içinde eski grantPremiumAccess kullanımını kaldırmak için revize
    private suspend fun revokePremiumOnServer(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = functions.getHttpsCallable("revokePremium").call()
                .continueWith { task -> task.result?.data as? Map<*, *> }
                .await()
            (result?.get("success") as? Boolean) == true
        } catch (e: Exception) {
            Log.e("BillingManager", "revokePremium çağrısı başarısız", e)
            false
        }
    }

    fun checkAndSyncSubscriptions() {
        if (!::billingClient.isInitialized || !billingClient.isReady) {
            Log.w("BillingManager", "BillingClient hazır değil, senkronizasyon planlandı.")
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
                appScope.launch(Dispatchers.IO) {
                    val userData = UserRepository.getUserDataOnce() ?: return@launch
                    val myActiveSubs = activeSubs.filter { it.accountIdentifiers?.obfuscatedAccountId == myObfuscatedId }
                    val purchasedSubs = myActiveSubs.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    // Google Play tarafında abonelik yoksa ve bizde geleceğe dönük bir expiry varsa refresh tetikle
                    if (purchasedSubs.isEmpty()) {
                        UserRepository.refreshPremiumStatus(force = true)
                    } else {
                        UserRepository.refreshPremiumStatus(force = true)
                    }
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