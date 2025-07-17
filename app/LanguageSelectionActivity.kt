package com.example.pdf

import android.content.Context
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View

object UIFeedbackHelper {

    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var isSoundPoolLoaded = false

    /**
     * SoundPool'u başlatır ve ses dosyasını hafızaya yükler.
     * MainActivity'nin onCreate metodunda çağrılmalıdır.
     */
    fun init(context: Context) {
        if (soundPool == null) {
            soundPool = SoundPool.Builder().setMaxStreams(2).build()
            // R.raw.click_sound, res/raw klasöründeki ses dosyasına işaret eder.
            clickSoundId = soundPool!!.load(context, R.raw.click_sound, 1)
            soundPool!!.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) {
                    isSoundPoolLoaded = true
                }
            }
        }
    }

    /**
     * Ayarlardan dokunma sesi açıksa ve ses dosyası yüklenmişse sesi çalar.
     */
    private fun playClickSound(context: Context) {
        if (SharedPreferencesManager.isTouchSoundEnabled(context) && isSoundPoolLoaded) {
            soundPool?.play(clickSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    /**
     * Ayarlardan dokunsal geri bildirim açıksa titreşim verir.
     */
    private fun performHapticFeedback(context: Context) {
        if (SharedPreferencesManager.isHapticFeedbackEnabled(context)) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                // API 26'dan küçük sürümler için eski metot
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    /**
     * Bir view'a tıklandığında hem ses hem de titreşim geri bildirimini tetikler.
     * Bu, onClick listener'lar içinden çağrılacak ana fonksiyondur.
     */
    fun provideFeedback(view: View) {
        val context = view.context
        playClickSound(context)
        performHapticFeedback(context)
    }

    /**
     * Uygulama kapatılırken SoundPool kaynaklarını serbest bırakır.
     * MainActivity'nin onDestroy metodunda çağrılmalıdır.
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        isSoundPoolLoaded = false
    }
}