package com.codenzi.mathlabs

import android.content.Intent
import android.os.*
import android.text.SpannableStringBuilder
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SplashActivity : AppCompatActivity() {

    private lateinit var textViewSplash: TextView
    private val typingDelay: Long = 100L
    private val textToType = "Codenzi"
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema ve mod ayarları, setContentView'dan önce yapılmalı.
        val themeMode = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        setTheme(R.style.Theme_Pdf_SplashActivity)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        textViewSplash = findViewById(R.id.textViewSplash)
        textViewSplash.text = ""

        startTypingAnimation()

        handler.postDelayed({
            if (!SharedPreferencesManager.isLanguageSelected(this)) {
                val systemLanguage = java.util.Locale.getDefault().language
                LocaleHelper.persist(this, systemLanguage)
            }

            val intent = if (SharedPreferencesManager.getUserName(this) == null) {
                Intent(this, NameEntryActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }

            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 2500)
    }

    private fun startTypingAnimation() {
        if (currentIndex < textToType.length) {
            val builder = SpannableStringBuilder()
            for (i in 0..currentIndex) {
                builder.append(textToType[i])
                if (i == currentIndex) {
                    builder.setSpan(
                        AlphaAnimationSpan(textViewSplash),
                        i,
                        i + 1,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            textViewSplash.text = builder
            currentIndex++
            handler.postDelayed({ startTypingAnimation() }, typingDelay)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}