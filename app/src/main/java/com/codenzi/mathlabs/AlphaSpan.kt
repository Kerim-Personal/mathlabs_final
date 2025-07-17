package com.codenzi.mathlabs

import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView

class AlphaSpan : CharacterStyle(), UpdateAppearance {
    private var alpha: Float = 0f

    override fun updateDrawState(tp: TextPaint) {
        tp.alpha = (alpha * 255).toInt()
    }

    fun fadeIn(textView: TextView) {
        val animation = AlphaAnimation(0f, 1f)
        animation.duration = 300
        animation.fillAfter = true

        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                alpha = 1f
                textView.invalidate()
            }
        })

        textView.startAnimation(animation)
    }
}


