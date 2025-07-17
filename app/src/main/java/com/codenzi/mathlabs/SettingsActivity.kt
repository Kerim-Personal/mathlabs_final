package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private fun applyGlobalThemeAndColor() {
        val theme = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(theme)
        setTheme(ThemeManager.getThemeResId(this))
        Log.d("ThemeDebug", "SettingsActivity - Tema uygulandı. Gece Modu: $theme")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("ThemeDebug", "SettingsActivity - onCreate çağrıldı.")
        applyGlobalThemeAndColor()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Modern tam ekran yöntemini etkinleştir
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val toolbar: MaterialToolbar = findViewById(R.id.settingsToolbar)

        // Toolbar'ın bildirim çubuğu altına girmemesi için
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

        val layoutLanguageSettings: LinearLayout = findViewById(R.id.layoutLanguageSettings)
        layoutLanguageSettings.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            val intent = Intent(this, LanguageSelectionActivity::class.java)
            startActivity(intent)
        }

        val layoutThemeSettings: LinearLayout = findViewById(R.id.layoutThemeSettings)
        layoutThemeSettings.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            showThemeDialog()
        }

        val layoutAppColorSettings: LinearLayout = findViewById(R.id.layoutAppColorSettings)
        layoutAppColorSettings.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            showAppColorDialog()
        }

        setupSwitches()
    }

    private fun setupSwitches() {
        val switchTouchSound: SwitchMaterial = findViewById(R.id.switchTouchSound)
        switchTouchSound.isChecked = SharedPreferencesManager.isTouchSoundEnabled(this)
        switchTouchSound.setOnCheckedChangeListener { buttonView, isChecked ->
            UIFeedbackHelper.provideFeedback(buttonView)
            SharedPreferencesManager.setTouchSoundEnabled(this, isChecked)
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
                    AppCompatDelegate.setDefaultNightMode(selectedTheme)
                    setResult(Activity.RESULT_OK)
                    recreate()
                }
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showAppColorDialog() {
        val themeColorNames = Array(ThemeManager.getAppColorThemeCount()) { i ->
            ThemeManager.getAppColorThemeName(this, i)
        }
        val currentAppColorThemeIndex = SharedPreferencesManager.getAppColorTheme(this)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_color_title))
            .setSingleChoiceItems(themeColorNames, currentAppColorThemeIndex) { dialog, which ->
                if (which != currentAppColorThemeIndex) {
                    ThemeManager.applyAppColorTheme(this, which)
                    setResult(Activity.RESULT_OK)
                }
                dialog.dismiss()
            }
            .create()
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(Activity.RESULT_OK)
            Log.d("ThemeDebug", "SettingsActivity - Toolbar geri tuşu: RESULT_OK ayarlandı.")
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_OK)
        Log.d("ThemeDebug", "SettingsActivity - Fiziksel geri tuşu: RESULT_OK ayarlandı.")
        super.onBackPressed()
    }
}