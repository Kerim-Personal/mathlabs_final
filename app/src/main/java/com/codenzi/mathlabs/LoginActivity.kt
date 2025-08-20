package com.codenzi.mathlabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.codenzi.mathlabs.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import android.view.animation.DecelerateInterpolator
import kotlinx.coroutines.launch
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build

class LoginActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        auth = FirebaseAuth.getInstance()

        // BillingManager'ı başlat
        billingManager = BillingManager(applicationContext, lifecycleScope)

        // Google resmi buton stil ayarı
        binding.signInButton.setSize(SignInButton.SIZE_WIDE)
        binding.signInButton.setColorScheme(SignInButton.COLOR_DARK)

        // Giriş animasyonları (hafif)
        runEntryAnimations()

        binding.signInButton.setOnClickListener {
            signIn()
        }

        // Animated WebP (Android 9+): decode ve başlat
        startAnimatedBackgroundIfSupported()

        // Google Sign-In buton metnini yerel dizeyle ayarla
        updateGoogleButtonText()
    }

    private fun signIn() {
        setLoading(true)
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            setLoading(false)
            Log.w("LoginActivity", "Google ile giriş başarısız", e)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Kullanıcı verisi oluştur veya güncelle
                    lifecycleScope.launch {
                        try {
                            // Her kullanıcı için Firestore kaydı oluştur/kontrol et
                            UserRepository.createOrUpdateUserData()

                            // Abonelik kontrolü yap (Billing hazır değilse kendisi planlar)
                            billingManager.checkAndSyncSubscriptions()

                            // Yönlendirme yap (yeni kullanıcılar dahil hepsi MainActivity'ye)
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } catch (e: Exception) {
                            Log.e("LoginActivity", "Kullanıcı verisi oluşturulurken hata", e)
                            Toast.makeText(this@LoginActivity, "Bir hata oluştu, lütfen tekrar deneyin.", Toast.LENGTH_SHORT).show()
                        } finally {
                            setLoading(false)
                        }
                    }
                } else {
                    setLoading(false)
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.signInButton.isEnabled = !loading
    }

    private fun runEntryAnimations() {
        // Kart animasyonu
        binding.heroCard.alpha = 0f
        binding.heroCard.translationY = 40f
        binding.heroCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(150)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun startAnimatedBackgroundIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val src = ImageDecoder.createSource(resources, R.drawable.login)
                val drawable = ImageDecoder.decodeDrawable(src)
                binding.imageBackground.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.apply {
                    repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    start()
                }
            } catch (e: Throwable) {
                Log.w("LoginActivity", "Animated WebP yüklenemedi: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (binding.imageBackground.drawable as? AnimatedImageDrawable)?.start()
        }
        // Yeniden çizim sonrası metni tekrar uygula
        updateGoogleButtonText()
    }

    private fun updateGoogleButtonText() {
        try {
            val tv = (0 until binding.signInButton.childCount)
                .map { binding.signInButton.getChildAt(it) }
                .filterIsInstance<TextView>()
                .firstOrNull()
            val text = getString(R.string.sign_in_with_google)
            tv?.text = text
            tv?.isAllCaps = false
            binding.signInButton.contentDescription = text
        } catch (e: Exception) {
            Log.w("LoginActivity", "Google buton metni ayarlanamadı: ${e.message}")
        }
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (binding.imageBackground.drawable as? AnimatedImageDrawable)?.stop()
        }
        super.onPause()
    }
}
