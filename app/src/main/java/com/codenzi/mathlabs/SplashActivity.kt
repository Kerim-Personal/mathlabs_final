package com.codenzi.mathlabs

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SplashActivity : AppCompatActivity() {

    private lateinit var textViewSplash: TextView
    private lateinit var retryButton: Button // Tekrar deneme butonu
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
        retryButton = findViewById(R.id.retryButton) // Butonu layout'tan al

        startElegantAnimation()

        retryButton.setOnClickListener {
            // Butona tıklandığında, butonu gizle ve işlemi tekrar başlat
            it.visibility = View.GONE
            textViewSplash.text = getString(R.string.app_name) // Yazıyı eski haline getir
            startElegantAnimation()
        }
    }

    private fun startElegantAnimation() {
        // Animasyon her başladığında butonun gizli olduğundan emin ol
        retryButton.visibility = View.GONE

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
                checkCurrentUser()
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        textViewSplash.startAnimation(animationSet)
    }

    private fun checkCurrentUser() {
        if (auth.currentUser != null) {
            navigateToNextScreen()
        } else {
            signInAnonymously()
        }
    }

    private fun signInAnonymously() {
        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("SplashActivity", "signInAnonymously:success")
                    navigateToNextScreen()
                } else {
                    // --- İSTENEN DEĞİŞİKLİK BURADA ---
                    Log.w("SplashActivity", "signInAnonymously:failure", task.exception)

                    // İnternet bağlantısını kontrol et
                    if (!isNetworkAvailable()) {
                        Toast.makeText(baseContext, "Lütfen internet bağlantınızı kontrol edin.", Toast.LENGTH_LONG).show()
                    } else {
                        // İnternet var ama başka bir Firebase hatası oluştu
                        Toast.makeText(baseContext, "Kimlik doğrulama başarısız oldu. Lütfen tekrar deneyin.", Toast.LENGTH_LONG).show()
                    }

                    // Uygulamayı kapatmak yerine kullanıcıya tekrar deneme şansı ver
                    textViewSplash.text = getString(R.string.connection_error)
                    retryButton.visibility = View.VISIBLE
                    // finish() komutu kaldırıldı.
                }
            }
    }

    private fun navigateToNextScreen() {
        val userName = SharedPreferencesManager.getUserName(this)
        val intent = if (userName.isNullOrEmpty()) {
            Intent(this, NameEntryActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // İnternet bağlantısını kontrol eden yardımcı fonksiyon
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textViewSplash.clearAnimation()
    }
}
