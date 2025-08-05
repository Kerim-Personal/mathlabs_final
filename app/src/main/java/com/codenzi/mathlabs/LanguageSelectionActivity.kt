package com.codenzi.mathlabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class LanguageSelectionActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        setTheme(R.style.Theme_Pdf)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_selection)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val rootView = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
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
        // YENİ EKLENEN SATIR:
        // Dilin artık kullanıcı tarafından bilinçli olarak seçildiğini kaydet.
        SharedPreferencesManager.setLanguageSelected(this, true)

        LocaleHelper.persist(this, languageCode)

        // Dil seçimi yeni bir kullanıcı tarafından yapıldığı için,
        // kullanıcı adının daha önce kaydedilip kaydedilmediğini kontrol etmeye gerek yok.
        // Doğrudan isim girme ekranına yönlendir.
        val intent = Intent(this, NameEntryActivity::class.java)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}