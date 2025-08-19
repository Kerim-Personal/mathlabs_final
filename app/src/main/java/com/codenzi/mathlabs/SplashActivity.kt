package com.codenzi.mathlabs

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            billingManager = BillingManager(applicationContext, lifecycleScope)

            // DEĞİŞİKLİK: Billing hazır olunca önce kullanıcı kaydını garantiye al, sonra aboneliği senkronize et.
            billingManager.executeOnBillingSetupFinished {
                lifecycleScope.launch {
                    try {
                        UserRepository.createOrUpdateUserData()
                    } catch (_: Exception) {
                        // Sessiz geç: Splash'ta kullanıcı akışını bloklama
                    } finally {
                        billingManager.checkAndSyncSubscriptions()
                    }
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (user == null) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }, 2000)
    }
}