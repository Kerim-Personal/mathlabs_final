package com.codenzi.mathlabs

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // Kullanıcı giriş yapmışsa, veri dinleyicisini HEMEN başlat.
                UserRepository.startListeningForUserData()

                // *** DÜZELTME BURADA YAPILDI ***
                // isNameEntered yerine, getUserName fonksiyonundan gelen sonucun
                // boş olup olmadığını kontrol ediyoruz.
                if (!SharedPreferencesManager.getUserName(this).isNullOrEmpty()) {
                    // Kullanıcı adını daha önce girmiş -> Ana Ekrana git
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    // Kullanıcı adını henüz girmemiş -> İsim Girme Ekranına git
                    startActivity(Intent(this, NameEntryActivity::class.java))
                }
            } else {
                // Kullanıcı giriş yapmamış -> Giriş Ekranına git
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish() // SplashActivity'i kapat
        }, 1500)
    }
}