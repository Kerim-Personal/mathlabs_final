package com.codenzi.mathlabs

import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView

class AlphaAnimationSpan(private val textView: TextView) : CharacterStyle(), UpdateAppearance {
    private var alpha = 0f

    override fun updateDrawState(tp: TextPaint) {
        tp.alpha = (alpha * 255).toInt()
    }

    init {
        val animation = AlphaAnimation(0f, 1f)
        animation.duration = 200
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                alpha = 1f
                textView.invalidate()
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })
        textView.startAnimation(animation)
    }
}
