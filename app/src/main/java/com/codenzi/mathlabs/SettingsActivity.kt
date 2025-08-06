package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
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
            buttonSubscribe.text = getString(R.string.premium_active)
            buttonSubscribe.isEnabled = false
        } else {
            buttonSubscribe.text = getString(R.string.subscribe_now)
            buttonSubscribe.isEnabled = true
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
        val planDetails = if (isMonthly) monthlyPlanDetails else yearlyPlanDetails

        planDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.let {
            textViewPrice.text = it.formattedPrice
            textViewPricePeriod.text = if (isMonthly) getString(R.string.price_period_monthly) else getString(R.string.price_period_yearly)
        } ?: run {
            val defaultPrice = if(isMonthly) getString(R.string.premium_monthly_price) else getString(R.string.premium_yearly_price)
            textViewPrice.text = defaultPrice.substringBefore('/')
            textViewPricePeriod.text = if (isMonthly) getString(R.string.price_period_monthly) else getString(R.string.price_period_yearly)
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
                val selectedLanguageCode = languageCodes[which]
                if (selectedLanguageCode != currentLanguageCode) {
                    LocaleHelper.applyLanguage(this, selectedLanguageCode)
                }
                dialog.dismiss()
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