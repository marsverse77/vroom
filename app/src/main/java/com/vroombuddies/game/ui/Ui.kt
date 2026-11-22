package com.vroombuddies.game.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Shared look: chunky rounded shapes, bold rounded-ish type, bright colours, bouncy presses. */
object Ui {
    var density = 1f
    fun dp(v: Float) = v * density
    fun dpi(v: Float) = (v * density).toInt()

    val font: Typeface = Typeface.create("sans-serif-black", Typeface.BOLD)

    const val YELLOW = 0xffffd23f.toInt()
    const val ORANGE = 0xffff7a1a.toInt()
    const val PINK = 0xffff3d8b.toInt()
    const val BLUE = 0xff2a7bff.toInt()
    const val GREEN = 0xff3ccf4e.toInt()
    const val PURPLE = 0xff8a4dff.toInt()
    const val INK = 0xff1d1b3a.toInt()

    fun text(ctx: Context, s: String, sizeSp: Float, color: Int = Color.WHITE, outline: Boolean = true): TextView =
        OutlinedText(ctx, outline).apply {
            text = s; textSize = sizeSp; typeface = font; setTextColor(color); gravity = Gravity.CENTER
            includeFontPadding = false
        }

    fun pill(color: Int, radius: Float, stroke: Int = Color.WHITE, strokeW: Float = 4f) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius)
        colors = intArrayOf(lighten(color, 0.25f), color)
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(dpi(strokeW), stroke)
    }

    fun lighten(c: Int, k: Float): Int {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return Color.rgb((r + (255 - r) * k).toInt(), (g + (255 - g) * k).toInt(), (b + (255 - b) * k).toInt())
    }

    /** Big cartoon button: squishes when pressed and springs back. */
    @SuppressLint("ClickableViewAccessibility")
    fun button(ctx: Context, label: String, color: Int, sizeSp: Float = 26f, onClick: () -> Unit): TextView =
        text(ctx, label, sizeSp).apply {
            background = pill(color, 28f)
            setPadding(dpi(28f), dpi(14f), dpi(28f), dpi(16f))
            elevation = dp(6f)
            setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).start()
                    MotionEvent.ACTION_UP -> { v.animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(4f)).setDuration(260).start(); onClick() }
                    MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                true
            }
        }

    fun popIn(v: View, delay: Long = 0) {
        v.scaleX = 0.2f; v.scaleY = 0.2f; v.alpha = 0f
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(delay).setDuration(420).setInterpolator(OvershootInterpolator(2.5f)).start()
    }

    fun frame(ctx: Context) = FrameLayout(ctx).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) }
    fun row(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    fun col(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    fun lp(w: Int, h: Int, gravity: Int, l: Float = 0f, t: Float = 0f, r: Float = 0f, b: Float = 0f) =
        FrameLayout.LayoutParams(w, h, gravity).apply { setMargins(dpi(l), dpi(t), dpi(r), dpi(b)) }
}

/** TextView with a soft dark drop shadow so text pops over any background. */
class OutlinedText(ctx: Context, outline: Boolean) : TextView(ctx) {
    init { if (outline) setShadowLayer(Ui.dp(5f), 0f, Ui.dp(3f), 0xcc1d1b3a.toInt()) }
}
