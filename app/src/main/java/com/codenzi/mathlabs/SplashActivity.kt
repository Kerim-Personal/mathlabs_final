package com.codenzi.mathlabs

import android.content.Intent
import android.os.*
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SplashActivity : AppCompatActivity() {

    private lateinit var textViewSplash: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema ve mod ayarları, setContentView'dan önce yapılmalı.
        val themeMode = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        setTheme(R.style.Theme_Pdf_SplashActivity)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        textViewSplash = findViewById(R.id.textViewSplash)

        // Mükemmelleştirilmiş yeni animasyonu başlat.
        startElegantAnimation()
    }

    private fun startElegantAnimation() {
        // 1. Animasyon Seti: Birden fazla animasyonu aynı anda çalıştırmak için kullanılır.
        val animationSet = AnimationSet(true).apply {
            interpolator = AccelerateDecelerateInterpolator() // Animasyonun başlangıçta yavaşlayıp sonra hızlanmasını sağlar.
            fillAfter = true // Animasyon bittiğinde yazı son halinde kalır.
        }

        // 2. Fade-in Animasyonu: Yazıyı görünmezden görünür hale getirir.
        val fadeIn = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 1200 // 1.2 saniye
        }

        // 3. Aşağıdan Yukarı Kayma Animasyonu: Yazıya zarif bir giriş efekti verir.
        val slideUp = TranslateAnimation(0f, 0f, 100f, 0f).apply {
            duration = 1200 // 1.2 saniye
        }

        // Oluşturulan animasyonları sete ekle.
        animationSet.addAnimation(fadeIn)
        animationSet.addAnimation(slideUp)

        // 4. Animasyon Bittiğinde Ne Olacağını Belirle
        animationSet.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                // Animasyon bittiğinde, kimlik doğrulama işlemini başlat.
                // Bu sayede animasyon ve mantık akışı arasında mükemmel bir senkronizasyon sağlanır.
                signInAnonymously()
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        // 5. Animasyonu TextView üzerinde başlat.
        textViewSplash.startAnimation(animationSet)
    }

    private fun signInAnonymously() {
        val auth = Firebase.auth
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("SplashActivity", "signInAnonymously:success")
                        navigateToNextScreen()
                    } else {
                        Log.w("SplashActivity", "signInAnonymously:failure", task.exception)
                        Toast.makeText(baseContext, "Authentication failed. Please check your internet connection.", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Log.d("SplashActivity", "User is already signed in.")
            navigateToNextScreen()
        }
    }

    private fun navigateToNextScreen() {
        // Bu fonksiyon artık bekleme yapmadan, animasyon biter bitmez çalışır.
        val isLanguageSelected = SharedPreferencesManager.isLanguageSelected(this)
        val userName = SharedPreferencesManager.getUserName(this)

        val intent = if (isLanguageSelected) {
            if (userName.isNullOrEmpty()) {
                Intent(this, NameEntryActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }
        } else {
            Intent(this, LanguageSelectionActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Kaynak sızıntılarını önlemek için animasyonu temizle.
        textViewSplash.clearAnimation()
    }
}
