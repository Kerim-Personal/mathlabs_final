package com.codenzi.mathlabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SplashActivity : AppCompatActivity() {

    private lateinit var textViewSplash: TextView
    private lateinit var retryButton: Button
    private lateinit var auth: FirebaseAuth

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        setTheme(R.style.Theme_Pdf_SplashActivity)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = Firebase.auth
        textViewSplash = findViewById(R.id.textViewSplash)
        retryButton = findViewById(R.id.retryButton)

        // Butonun başlangıçta görünmez olduğundan emin ol
        retryButton.visibility = View.GONE

        // Animasyonu başlat
        startElegantAnimation()

        // Tekrar deneme butonu işlevselliği (internet yoksa kullanılır)
        retryButton.setOnClickListener {
            it.visibility = View.GONE
            textViewSplash.text = getString(R.string.app_name)
            startElegantAnimation()
        }
    }

    private fun startElegantAnimation() {
        val animationSet = AnimationSet(true).apply {
            interpolator = AccelerateDecelerateInterpolator()
            fillAfter = true
        }
        val fadeIn = AlphaAnimation(0.0f, 1.0f).apply { duration = 1200 }
        val slideUp = TranslateAnimation(0f, 0f, 100f, 0f).apply { duration = 1200 }
        animationSet.addAnimation(fadeIn)
        animationSet.addAnimation(slideUp)

        animationSet.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                // Animasyon bittiğinde kullanıcı kontrolü ve yönlendirme yap
                checkUserAndNavigate()
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        textViewSplash.startAnimation(animationSet)
    }

    /**
     * Kullanıcı durumunu kontrol eder ve doğru ekrana yönlendirir.
     */
    private fun checkUserAndNavigate() {
        val currentUser = auth.currentUser
        // Eğer bir kullanıcı varsa VE bu kullanıcı anonim değilse ana ekrana git.
        if (currentUser != null && !currentUser.isAnonymous) {
            Log.d("SplashActivity", "User is signed in. Navigating to MainActivity.")
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            // Eğer hiç kullanıcı yoksa veya kullanıcı anonim ise giriş ekranına git.
            Log.d("SplashActivity", "No signed-in user found. Navigating to LoginActivity.")
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textViewSplash.clearAnimation()
    }
}