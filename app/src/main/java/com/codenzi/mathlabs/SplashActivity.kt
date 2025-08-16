package com.codenzi.mathlabs

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()

        // HATA DÜZELTİLDİ: Bu satır kaldırıldı.
        // UserRepository, AuthStateListener sayesinde veri dinlemesini
        // artık kendi içinde otomatik olarak başlatıyor.
        // UserRepository.startListeningForUserData() // <-- BU SATIR SİLİNDİ

        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatus()
        }, 2000) // 2 saniye bekleme
    }

    private fun checkUserStatus() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Kullanıcı oturum açmış, ana ekrana yönlendir
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // Kullanıcı oturum açmamış, giriş ekranına yönlendir
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish() // SplashActivity'yi kapat
    }
}
