package com.appholaworld.offchat.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.termux.R

class RippleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var radius = 0f
    private val maxRipples = 3
    private val animators = mutableListOf<RippleAnimator>()

    inner class RippleAnimator(var offset: Float) {
        fun update() {
            offset += 0.01f
            if (offset > 1f) offset = 0f
        }
    }

    init {
        for (i in 0 until maxRipples) {
            animators.add(RippleAnimator(i.toFloat() / maxRipples))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = Math.min(cx, cy)
        
        paint.color = ContextCompat.getColor(context, R.color.primary)

        for (anim in animators) {
            anim.update()
            val currentRadius = maxRadius * anim.offset
            paint.alpha = ((1f - anim.offset) * 255).toInt()
            canvas.drawCircle(cx, cy, currentRadius, paint)
        }
        
        invalidate()
    }
}
