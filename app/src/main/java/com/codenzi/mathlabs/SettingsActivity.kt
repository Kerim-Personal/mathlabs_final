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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var togglePlan: MaterialButtonToggleGroup
    private lateinit var textViewPrice: TextView
    private lateinit var textViewPricePeriod: TextView
    private var premiumTitleClickCount = 0 // Tıklama sayacı için yeni değişken

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

        initializePremiumViews()
        setupClickListeners()
        setupSwitches()
        setupPremiumPlanToggle()
        setupDeveloperMode() // Gizli premium modu için yeni fonksiyon çağrısı
    }

    private fun initializePremiumViews() {
        togglePlan = findViewById(R.id.togglePlan)
        textViewPrice = findViewById(R.id.textViewPrice)
        textViewPricePeriod = findViewById(R.id.textViewPricePeriod)
    }

    private fun setupClickListeners() {
        findViewById<LinearLayout>(R.id.layoutLanguageSettings).setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            showLanguageDialog()
        }

        findViewById<LinearLayout>(R.id.layoutThemeSettings).setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            showThemeDialog()
        }

        findViewById<LinearLayout>(R.id.layoutContactUs).setOnClickListener {
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

        findViewById<LinearLayout>(R.id.layoutPrivacyPolicy).setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            val url = "https://www.codenzi.com/privacy-math-labs.html"
            val privacyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(privacyIntent)
        }

        findViewById<Button>(R.id.buttonSubscribe).setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            Toast.makeText(this, getString(R.string.premium_toast_message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwitches() {
        val switchTouchSound: SwitchMaterial = findViewById(R.id.switchTouchSound)
        switchTouchSound.isChecked = SharedPreferencesManager.isTouchSoundEnabled(this)
        switchTouchSound.setOnCheckedChangeListener { buttonView, isChecked ->
            UIFeedbackHelper.provideFeedback(buttonView)
            SharedPreferencesManager.setTouchSoundEnabled(this, isChecked)
        }
    }

    private fun setupPremiumPlanToggle() {
        togglePlan.check(R.id.buttonMonthly)
        updatePriceDisplay(isMonthly = true)

        togglePlan.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                UIFeedbackHelper.provideFeedback(group)
                when (checkedId) {
                    R.id.buttonMonthly -> updatePriceDisplay(isMonthly = true)
                    R.id.buttonYearly -> updatePriceDisplay(isMonthly = false)
                }
            }
        }
    }

    // YENİ EKLENEN FONKSİYON
    private fun setupDeveloperMode() {
        findViewById<TextView>(R.id.premiumFeaturesTitle).setOnClickListener {
            premiumTitleClickCount++
            if (premiumTitleClickCount == 5) {
                val isPremium = !SharedPreferencesManager.isUserPremium(this)
                SharedPreferencesManager.setUserAsPremium(this, isPremium)
                val message = if (isPremium) "Geliştirici Modu: Premium Aktif!" else "Geliştirici Modu: Premium Devre Dışı!"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                premiumTitleClickCount = 0
            }
        }
    }

    private fun updatePriceDisplay(isMonthly: Boolean) {
        if (isMonthly) {
            textViewPrice.text = getString(R.string.premium_monthly_price).substringBefore('/')
            textViewPricePeriod.text = getString(R.string.price_period_monthly)
        } else {
            textViewPrice.text = getString(R.string.premium_yearly_price).substringBefore('/')
            textViewPricePeriod.text = getString(R.string.price_period_yearly)
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system_default)
        )
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