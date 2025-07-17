package com.codenzi.mathlabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class LanguageSelectionActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private fun applyThemeAndColor() {
        setTheme(ThemeManager.getThemeResId(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeAndColor()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_selection)

        // Modern tam ekran yöntemini etkinleştir
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // İçeriğin sistem çubukları altına girmemesi için ana layout'a padding ver
        val rootView = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            // Düzeltilmiş satır aşağıda
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBarInsets.left,
                top = systemBarInsets.top,
                right = systemBarInsets.right,
                bottom = systemBarInsets.bottom
            )
            insets
        }

        val layoutTurkish: LinearLayout = findViewById(R.id.layoutTurkish)
        val layoutEnglish: LinearLayout = findViewById(R.id.layoutEnglish)

        layoutTurkish.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            setLanguageAndProceed("tr")
        }

        layoutEnglish.setOnClickListener {
            UIFeedbackHelper.provideFeedback(it)
            setLanguageAndProceed("en")
        }
    }

    private fun setLanguageAndProceed(languageCode: String) {
        LocaleHelper.persist(this, languageCode)

        // 'Assignment' uyarısı için düzeltilmiş, daha kısa kod
        val intent = if (SharedPreferencesManager.getUserName(this) == null) {
            Intent(this, NameEntryActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}