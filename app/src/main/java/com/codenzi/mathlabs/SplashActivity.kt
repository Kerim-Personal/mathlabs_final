package com.codenzi.mathlabs

import android.content.Intent
import android.os.*
import android.text.SpannableStringBuilder
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var textViewSplash: TextView
    private val typingDelay: Long = 100L
    private val textToType = "Codenzi"
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        textViewSplash = findViewById(R.id.textViewSplash)
        textViewSplash.text = ""

        startTypingAnimation()

        handler.postDelayed({
            // Uygulama ilk kez mi açılıyor kontrolü (dil seçimi yapılmamışsa)
            if (!SharedPreferencesManager.isLanguageSelected(this)) {
                // Cihazın sistem dilini al
                val systemLanguage = java.util.Locale.getDefault().language
                // Alınan sistem dilini kaydet ve dilin seçildiğini işaretle
                LocaleHelper.persist(this, systemLanguage)
            }

            val intent: Intent
            // Kullanıcı adı daha önce girilmiş mi diye kontrol et
            if (SharedPreferencesManager.getUserName(this) == null) {
                // Kullanıcı adı yoksa, isim giriş ekranına yönlendir
                intent = Intent(this, NameEntryActivity::class.java)
            } else {
                // Kullanıcı adı varsa, ana ekrana yönlendir
                intent = Intent(this, MainActivity::class.java)
            }

            // Yeni aktiviteyi başlat ve splash ekranını kapat (önceki aktiviteleri temizle)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 2500)
    }

    private fun startTypingAnimation() {
        if (currentIndex <= textToType.lastIndex) {
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
        // Aktivite yok edildiğinde handler'daki bekleyen işlemleri iptal et
        handler.removeCallbacksAndMessages(null)
    }
}