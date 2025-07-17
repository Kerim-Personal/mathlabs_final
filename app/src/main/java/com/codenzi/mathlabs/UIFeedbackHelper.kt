package com.codenzi.mathlabs

import android.content.Context
import android.media.SoundPool
import android.view.View

object UIFeedbackHelper {

    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var isSoundPoolLoaded = false

    fun init(context: Context) {
        if (soundPool == null) {
            soundPool = SoundPool.Builder().setMaxStreams(2).build()
            clickSoundId = soundPool!!.load(context, R.raw.click_sound, 1)
            soundPool!!.setOnLoadCompleteListener { _, _, status ->
                isSoundPoolLoaded = status == 0
            }
        }
    }

    private fun playClickSound(context: Context) {
        if (SharedPreferencesManager.isTouchSoundEnabled(context) && isSoundPoolLoaded) {
            soundPool?.play(clickSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
        }
    }

    // performHapticFeedback metodu tamamen kaldırıldı.

    fun provideFeedback(view: View) {
        val context = view.context
        playClickSound(context)
        // performHapticFeedback çağrısı buradan kaldırıldı.
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        isSoundPoolLoaded = false
    }
}