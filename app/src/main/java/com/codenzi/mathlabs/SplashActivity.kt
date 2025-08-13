// kerim-personal/mathlabs_final/mathlabs_final-846de2bc6294564b282343e4d0a0be0e4be59898/app/src/main/java/com/codenzi/mathlabs/SplashActivity.kt

package com.codenzi.mathlabs

import android.content.Context // attachBaseContext için eklendi
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

    // --- YENİ EKLENEN KISIM ---
    // Her aktivitede olduğu gibi, dil ayarını en başta yapmak için.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = SharedPreferencesManager.getTheme(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        setTheme(R.style.Theme_Pdf_SplashActivity)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        textViewSplash = findViewById(R.id.textViewSplash)
        startElegantAnimation()
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
                signInAnonymously()
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
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
                        Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Log.d("SplashActivity", "User is already signed in.")
            navigateToNextScreen()
        }
    }

    // --- DÜZELTİLMİŞ KISIM ---
    private fun navigateToNextScreen() {
        // Dil seçimi kontrolü kaldırıldı.
        val userName = SharedPreferencesManager.getUserName(this)

        val intent = if (userName.isNullOrEmpty()) {
            // İsim yoksa, isim girme ekranına git
            Intent(this, NameEntryActivity::class.java)
        } else {
            // İsim varsa, ana ekrana git
            Intent(this, MainActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        textViewSplash.clearAnimation()
    }
}