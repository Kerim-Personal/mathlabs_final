package com.codenzi.mathlabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    // --- View Değişkenleri ---
    private lateinit var togglePlan: MaterialButtonToggleGroup
    private lateinit var textViewPrice: TextView
    private lateinit var textViewPricePeriod: TextView
    private lateinit var buttonSubscribe: Button
    private lateinit var switchTouchSound: SwitchMaterial
    private lateinit var layoutThemeSettings: LinearLayout
    private lateinit var layoutLanguageSettings: LinearLayout
    private lateinit var layoutContactUs: LinearLayout
    private lateinit var layoutPrivacyPolicy: LinearLayout
    private lateinit var premiumPurchaseContainer: LinearLayout
    private lateinit var premiumStatusContainer: LinearLayout
    private lateinit var userProfileImage: ImageView
    private lateinit var textViewUserName: TextView
    private lateinit var textViewUserEmail: TextView
    private lateinit var buttonSignOut: Button

    // --- Diğer Değişkenler ---
    private lateinit var billingManager: BillingManager
    private lateinit var auth: FirebaseAuth
    private var monthlyPlanDetails: ProductDetails? = null
    private var yearlyPlanDetails: ProductDetails? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        setTheme(R.style.Theme_Pdf)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        auth = Firebase.auth
        val toolbar: MaterialToolbar = findViewById(R.id.settingsToolbar)

        initializeViews()
        setupToolbar(toolbar)
        setupBilling()
        setupClickListeners()
        setupSwitches()
        setupPremiumPlanToggle()
        setupBackButton()

        // Kullanıcı verisini canlı olarak dinleyip arayüzü anında güncelliyoruz.
        // Bu, satın alma sonrası veya oturum durumu değiştiğinde arayüzün
        // tutarlı kalmasını sağlar.
        observeUserStatusAndUpdateUI()
    }

    private fun setupToolbar(toolbar: MaterialToolbar) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = systemBarInsets.top }
            insets
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
    }

    private fun initializeViews() {
        togglePlan = findViewById(R.id.togglePlan)
        textViewPrice = findViewById(R.id.textViewPrice)
        textViewPricePeriod = findViewById(R.id.textViewPricePeriod)
        buttonSubscribe = findViewById(R.id.buttonSubscribe)
        switchTouchSound = findViewById(R.id.switchTouchSound)
        layoutThemeSettings = findViewById(R.id.layoutThemeSettings)
        layoutLanguageSettings = findViewById(R.id.layoutLanguageSettings)
        layoutContactUs = findViewById(R.id.layoutContactUs)
        layoutPrivacyPolicy = findViewById(R.id.layoutPrivacyPolicy)
        premiumPurchaseContainer = findViewById(R.id.premiumPurchaseContainer)
        premiumStatusContainer = findViewById(R.id.premiumStatusContainer)
        userProfileImage = findViewById(R.id.userProfileImage)
        textViewUserName = findViewById(R.id.textViewUserName)
        textViewUserEmail = findViewById(R.id.textViewUserEmail)
        buttonSignOut = findViewById(R.id.buttonSignOut)
    }

    private fun setupBilling() {
        billingManager = BillingManager(this, lifecycleScope)

        billingManager.productDetails.observe(this) { detailsMap ->
            monthlyPlanDetails = detailsMap["monthly_premium_plan"]
            yearlyPlanDetails = detailsMap["yearly_premium_plan"]
            updatePriceDisplay()
        }

        billingManager.newPurchaseEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { isPremium ->
                if (isPremium) {
                    Toast.makeText(this, getString(R.string.premium_activated_toast), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * UserRepository'deki canlı veri akışını (StateFlow) dinler.
     * Kullanıcı verisi her değiştiğinde (örneğin isPremium true olduğunda) arayüzü anında günceller.
     * Bu fonksiyon, tüm tutarsızlık sorunlarını ortadan kaldırır.
     */
    private fun observeUserStatusAndUpdateUI() {
        lifecycleScope.launch {
            UserRepository.userDataState.collectLatest { userData ->
                val user = auth.currentUser

                // 1. KESİN KONTROL: Eğer kullanıcı oturumu gerçekten kapalıysa, giriş ekranına yönlendir.
                // En güvenilir kontrol budur.
                if (user == null) {
                    navigateToLogin()
                    return@collectLatest
                }

                // 2. SABIRLI BEKLEYİŞ: Oturum açıksa ama Firestore'dan veri henüz gelmediyse (userData == null ise),
                // yönlendirme YAPMA, sadece bekle veya geçici bir "yükleniyor" durumu göster.
                if (userData != null) {
                    // Veri geldiğinde arayüzü güncelle.
                    updatePremiumUI(userData.isPremium)
                    textViewUserName.text = user.displayName
                    textViewUserEmail.text = user.email
                    Glide.with(this@SettingsActivity)
                        .load(user.photoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_premium_badge)
                        .into(userProfileImage)
                } else {
                    // userData henüz null. Bu durum, verinin yüklendiği anlamına gelir.
                    // Arayüze geçici bilgi basabiliriz ama ASLA yönlendirme yapmayız.
                    textViewUserName.text = getString(R.string.loading) // Örnek: "Yükleniyor..."
                    textViewUserEmail.text = user.email // E-posta bilgisi zaten `user` objesinde var.
                }
            }
        }
    }

    /**
     * Premium durumuna göre satın alma ve premium durumu konteynerlerinin görünürlüğünü ayarlar.
     */
    private fun updatePremiumUI(isPremium: Boolean) {
        if (isPremium) {
            premiumPurchaseContainer.visibility = View.GONE
            premiumStatusContainer.visibility = View.VISIBLE
        } else {
            premiumPurchaseContainer.visibility = View.VISIBLE
            premiumStatusContainer.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        layoutThemeSettings.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            showThemeDialog()
        }
        layoutLanguageSettings.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            showLanguageDialog()
        }
        buttonSignOut.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            showSignOutConfirmationDialog()
        }
        layoutContactUs.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf("info@codenzi.com"))
                putExtra(Intent.EXTRA_SUBJECT, "MathLabs Geri Bildirim")
            }
            try {
                startActivity(Intent.createChooser(emailIntent, "E-posta gönder..."))
            } catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "Uygun bir e-posta uygulaması bulunamadı.", Toast.LENGTH_SHORT).show()
            }
        }
        layoutPrivacyPolicy.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            val url = "https://www.codenzi.com/privacy-math-labs.html"
            val privacyIntent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(privacyIntent)
        }
        buttonSubscribe.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            val selectedPlanDetails = if (togglePlan.checkedButtonId == R.id.buttonMonthly) {
                monthlyPlanDetails
            } else {
                yearlyPlanDetails
            }
            selectedPlanDetails?.let { planDetails ->
                billingManager.launchPurchaseFlow(this, planDetails)
            } ?: run {
                Toast.makeText(this, "Abonelik planı detayları henüz yüklenmedi.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_OK)
                finish()
            }
        })
    }

    private fun showSignOutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sign_out_confirm_title))
            .setMessage(getString(R.string.sign_out_confirm_message))
            .setPositiveButton(getString(R.string.confirm)) { dialog, _ ->
                signOut()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun signOut() {
        // --- EKLENECEK EN ÖNEMLİ KISIM ---
        // Önce lokal veriyi temizle
        UserRepository.clearLocalUserData()
        // --- EKLENECEK KISIM SONU ---

        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun setupSwitches() {
        switchTouchSound.isChecked = SharedPreferencesManager.isTouchSoundEnabled(this)
        switchTouchSound.setOnCheckedChangeListener { buttonView, isChecked ->
            UIFeedbackHelper.provideFeedback(buttonView)
            SharedPreferencesManager.setTouchSoundEnabled(this, isChecked)
        }
    }

    private fun setupPremiumPlanToggle() {
        togglePlan.check(R.id.buttonMonthly)
        updatePriceDisplay()
        togglePlan.addOnButtonCheckedListener { group, _, _ ->
            UIFeedbackHelper.provideFeedback(group)
            updatePriceDisplay()
        }
    }

    private fun updatePriceDisplay() {
        val isMonthly = togglePlan.checkedButtonId == R.id.buttonMonthly
        val monthlyDetails = monthlyPlanDetails
        val yearlyDetails = yearlyPlanDetails
        val currentPlanDetails = if (isMonthly) monthlyDetails else yearlyDetails
        currentPlanDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.let { pricingPhase ->
            textViewPrice.text = pricingPhase.formattedPrice
            textViewPricePeriod.text = if (isMonthly) getString(R.string.price_period_monthly) else getString(R.string.price_period_yearly)
            buttonSubscribe.visibility = View.VISIBLE
            textViewPrice.visibility = View.VISIBLE
            textViewPricePeriod.visibility = View.VISIBLE
        } ?: run {
            textViewPrice.text = ""
            textViewPricePeriod.text = ""
            buttonSubscribe.visibility = View.GONE
            textViewPrice.visibility = View.INVISIBLE
            textViewPricePeriod.visibility = View.INVISIBLE
        }
        val textViewYearlyDiscount: TextView = findViewById(R.id.textViewYearlyDiscount)
        if (!isMonthly && monthlyDetails != null && yearlyDetails != null) {
            val monthlyPrice = monthlyDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros
            val yearlyPrice = yearlyDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros
            if (monthlyPrice != null && yearlyPrice != null && yearlyPrice > 0) {
                val yearlyPriceFromMonthly = monthlyPrice * 12
                val discount = ((yearlyPriceFromMonthly - yearlyPrice) * 100 / yearlyPriceFromMonthly)
                if (discount > 0) {
                    textViewYearlyDiscount.text = getString(R.string.premium_yearly_discount, "%$discount")
                    textViewYearlyDiscount.visibility = View.VISIBLE
                } else {
                    textViewYearlyDiscount.visibility = View.GONE
                }
            } else {
                textViewYearlyDiscount.visibility = View.GONE
            }
        } else {
            textViewYearlyDiscount.visibility = View.GONE
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf(getString(R.string.theme_light), getString(R.string.theme_dark), getString(R.string.theme_system_default))
        val currentTheme = SharedPreferencesManager.getTheme(this)
        val checkedItem = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> 0
            AppCompatDelegate.MODE_NIGHT_YES -> 1
            else -> 2
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.theme_title))
            .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                val selectedTheme = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (selectedTheme != currentTheme) {
                    SharedPreferencesManager.saveTheme(this, selectedTheme)
                    setResult(RESULT_OK)
                    recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("Türkçe", "English")
        val languageCodes = arrayOf("tr", "en")
        val currentLanguageCode = SharedPreferencesManager.getLanguage(this)
        val checkedItem = languageCodes.indexOf(currentLanguageCode).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.language_settings))
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                dialog.dismiss()
                val selectedLanguageCode = languageCodes[which]
                if (selectedLanguageCode != currentLanguageCode) {
                    LocaleHelper.applyLanguage(this, selectedLanguageCode)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_OK)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}