package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    // XML dosyanızdaki view'lar için değişken tanımlamaları
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


    private lateinit var billingManager: BillingManager
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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val toolbar: MaterialToolbar = findViewById(R.id.settingsToolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBarInsets.top
            }
            insets
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        initializeViews()
        setupBilling()
        setupClickListeners()
        setupSwitches()
        setupPremiumPlanToggle()
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
    }

    private fun setupBilling() {
        billingManager = BillingManager(this, lifecycleScope)
        billingManager.productDetails.observe(this) { detailsMap ->
            monthlyPlanDetails = detailsMap["monthly_premium_plan"]
            yearlyPlanDetails = detailsMap["yearly_premium_plan"]
            updatePriceDisplay()
        }
        billingManager.isPremium.observe(this) { isPremium ->
            updatePremiumUI(isPremium)
        }
        billingManager.checkPurchases()
    }

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

        layoutContactUs.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("info@codenzi.com"))
                putExtra(Intent.EXTRA_SUBJECT, "MathLabs Geri Bildirim")
            }
            try {
                startActivity(Intent.createChooser(emailIntent, "E-posta gönder..."))
            } catch (ex: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "Uygun bir e-posta uygulaması bulunamadı.", Toast.LENGTH_SHORT).show()
            }
        }

        layoutPrivacyPolicy.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            val url = "https://www.codenzi.com/privacy-math-labs.html"
            val privacyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(privacyIntent)
        }

        buttonSubscribe.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)

            val selectedPlanDetails = if (togglePlan.checkedButtonId == R.id.buttonMonthly) {
                monthlyPlanDetails
            } else {
                yearlyPlanDetails
            }

            selectedPlanDetails?.let {
                billingManager.launchPurchaseFlow(this, it)
            } ?: run {
                Toast.makeText(this, "Abonelik planı detayları henüz yüklenmedi.", Toast.LENGTH_SHORT).show()
            }
        }
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
            textViewPricePeriod.text = if (isMonthly) {
                getString(R.string.price_period_monthly)
            } else {
                getString(R.string.price_period_yearly)
            }
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
                    setResult(Activity.RESULT_OK)
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
                dialog.dismiss() // Önce diyalogu kapat
                val selectedLanguageCode = languageCodes[which]

                if (selectedLanguageCode != currentLanguageCode) {
                    // YENİ VE DOĞRU YÖNTEM
                    // 1. Yeni dili SharedPreferences'a kaydet
                    SharedPreferencesManager.saveLanguage(this, selectedLanguageCode)

                    // 2. Uygulamayı sıfırdan başlatmak için Intent hazırla
                    val intent = Intent(this, SplashActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

                    // 3. Yeni görevi başlat
                    startActivity(intent)

                    // 4. Mevcut uygulama prosesini sonlandırarak hafızada eski
                    // bir versiyon kalma ihtimalini ortadan kaldır.
                    finishAffinity()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(Activity.RESULT_OK)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("This method has been deprecated in favor of using the OnBackPressedDispatcher.", ReplaceWith("onBackPressedDispatcher.onBackPressed()"))
    override fun onBackPressed() {
        setResult(Activity.RESULT_OK)
        super.onBackPressed()
    }
}