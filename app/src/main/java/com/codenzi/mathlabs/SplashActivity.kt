package com.codenzi.mathlabs

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    // BillingManager'ı burada tanımlıyoruz.
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Kullanıcı oturumunun durumunu kontrol et
        val user = FirebaseAuth.getInstance().currentUser

        // Eğer kullanıcı giriş yapmışsa, abonelik durumunu Google Play ile senkronize et
        if (user != null) {
            // BillingManager'ı başlat ve senkronizasyonu tetikle.
            // Bu işlem arka planda çalışacak ve Splash ekranının ilerleyişini engellemeyecek.
            // Not: BillingManager'ın içindeki coroutineScope, activity yok olduğunda
            // otomatik olarak iptal edileceği için lifecycleScope kullanmak en güvenlisidir.
            billingManager = BillingManager(applicationContext, lifecycleScope)

            // Billing client hazır olur olmaz senkronizasyonu başlatmasını sağla.
            // setupBillingClient içindeki onBillingSetupFinished içinde çağrılıyor.
            // Biz burada sadece başlatıyoruz. Güvenli olması için senkronizasyonu
            // doğrudan çağırabiliriz.
            billingManager.checkAndSyncSubscriptions()
        }

        // Ana ekrana yönlendirme işlemini gecikmeli olarak yap.
        Handler(Looper.getMainLooper()).postDelayed({
            if (user == null) {
                // Kullanıcı giriş yapmamış, LoginActivity'e yönlendir
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                // Kullanıcı giriş yapmış, MainActivity'e yönlendir
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }, 2000) // 2 saniye bekleme süresi
    }
}