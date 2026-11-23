package com.vroombuddies.game.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.vroombuddies.game.R

/**
 * Shared cartoon look. Everything has a thick ink outline, a darker "lip" underneath so it looks like a
 * chunky toy you can press, and a glossy highlight on top. Titles lean forward a little so they feel fast.
 */
object Ui {
    var density = 1f
    fun dp(v: Float) = v * density
    fun dpi(v: Float) = (v * density).toInt()

    /** Rounded, friendly display font (Fredoka Bold, SIL OFL). Falls back to a heavy system font. */
    var font: Typeface = Typeface.create("sans-serif-black", Typeface.BOLD); private set

    fun init(ctx: Context) {
        density = ctx.resources.displayMetrics.density
        font = try { ctx.resources.getFont(R.font.fredoka_bold) } catch (t: Throwable) { font }
    }

    const val YELLOW = 0xffffd23f.toInt()
    const val ORANGE = 0xffff7a1a.toInt()
    const val PINK = 0xffff3d8b.toInt()
    const val BLUE = 0xff2a7bff.toInt()
    const val SKY = 0xff3fc6ff.toInt()
    const val GREEN = 0xff3ccf4e.toInt()
    const val PURPLE = 0xff8a4dff.toInt()
    const val RED = 0xffff4646.toInt()
    const val INK = 0xff1d1b3a.toInt()
    const val NAVY = 0xff2b2f6b.toInt()

    /** Forward lean for fast titles. */
    const val SLANT = -0.18f

    fun text(ctx: Context, s: String, sizeSp: Float, color: Int = Color.WHITE, outline: Boolean = true, slant: Boolean = false): TextView =
        OutlinedText(ctx, outline).apply {
            text = s; textSize = sizeSp; typeface = font; setTextColor(color); gravity = Gravity.CENTER
            includeFontPadding = false
            if (slant) { paint.textSkewX = SLANT; setPadding(paddingLeft, paddingTop, paddingRight + (textSize * 0.22f).toInt(), paddingBottom) }
        }

    fun lighten(c: Int, k: Float): Int {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return Color.argb(Color.alpha(c), (r + (255 - r) * k).toInt(), (g + (255 - g) * k).toInt(), (b + (255 - b) * k).toInt())
    }
    fun darken(c: Int, k: Float): Int =
        Color.argb(Color.alpha(c), (Color.red(c) * (1 - k)).toInt(), (Color.green(c) * (1 - k)).toInt(), (Color.blue(c) * (1 - k)).toInt())

    private val chunkyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()

    /**
     * Draw a chunky toy shape into [r]: ink outline, darker lip of [lip] px under the body, body gradient and a
     * glossy stripe across the top. [r] includes the lip. Used by buttons, panels and the HUD so they all match.
     */
    fun drawChunky(c: Canvas, r: RectF, radius: Float, color: Int, lip: Float = dp(6f), outline: Float = dp(3.5f), gloss: Boolean = true, p: Paint = chunkyPaint) {
        val o = outline / 2
        p.shader = null; p.style = Paint.Style.FILL
        // ink silhouette (outline) under everything
        p.color = INK
        c.drawRoundRect(r, radius, radius, p)
        // lip
        tmp.set(r.left + outline, r.top + outline, r.right - outline, r.bottom - outline)
        p.color = darken(color, 0.38f); c.drawRoundRect(tmp, radius - o, radius - o, p)
        // body
        tmp.set(r.left + outline, r.top + outline, r.right - outline, r.bottom - outline - lip)
        p.shader = LinearGradient(0f, tmp.top, 0f, tmp.bottom, lighten(color, 0.22f), color, Shader.TileMode.CLAMP)
        c.drawRoundRect(tmp, radius - o, radius - o, p); p.shader = null
        if (gloss) {
            val gh = (tmp.height() * 0.38f)
            val inset = min(radius * 0.45f, tmp.width() * 0.2f)
            tmp.set(tmp.left + inset, tmp.top + outline * 0.6f, tmp.right - inset, tmp.top + outline * 0.6f + gh)
            p.color = 0x55ffffff; c.drawRoundRect(tmp, gh / 2, gh / 2, p)
        }
    }
    private fun min(a: Float, b: Float) = if (a < b) a else b

    /** Drawable version of [drawChunky] for view backgrounds. */
    class ChunkyDrawable(var color: Int, private val radius: Float, val lip: Float = dp(6f), private val outline: Float = dp(3.5f), private val gloss: Boolean = true) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG); private val r = RectF()
        override fun draw(c: Canvas) { r.set(bounds); drawChunky(c, r, kotlin.math.min(radius, r.height() / 2), color, lip, outline, gloss, p) }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Semi-see-through rounded card with an ink outline: for name plates, stats and settings. */
    fun panel(color: Int = 0xe62b2f6b.toInt(), radius: Float = 24f) = ChunkyDrawable(color, dp(radius), lip = dp(5f), gloss = false)

    /** Big cartoon button: squishes and sinks into its lip when pressed, springs back. */
    @SuppressLint("ClickableViewAccessibility")
    fun button(ctx: Context, label: String, color: Int, sizeSp: Float = 26f, onClick: () -> Unit): TextView = makeButton(ctx, label, color, sizeSp, false, onClick)

    @SuppressLint("ClickableViewAccessibility")
    fun makeButton(ctx: Context, label: String, color: Int, sizeSp: Float, round: Boolean, onClick: () -> Unit): TextView =
        text(ctx, label, sizeSp).apply {
            (this as OutlinedText).square = round
            val bg = ChunkyDrawable(color, dp(if (round) 200f else 22f), lip = dp(7f))
            background = bg
            // round buttons get slim vertical padding: a TextView whose text overflows scrolls, and that
            // would shift the background up and clip the bottom of the circle
            if (round) setPadding(dpi(14f), dpi(4f), dpi(14f), dpi(4f) + bg.lip.toInt())
            else setPadding(dpi(26f), dpi(10f), dpi(26f), dpi(12f) + bg.lip.toInt())
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.88f).translationY(dp(3f)).setDuration(70).start()
                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1f).scaleY(1f).translationY(0f).setInterpolator(OvershootInterpolator(4f)).setDuration(260).start()
                        if (e.x in 0f..v.width.toFloat() && e.y in 0f..v.height.toFloat()) onClick()
                    }
                    MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(120).start()
                }
                true
            }
        }

    /** A round icon button (back, pause, arrows) with a fixed square size. */
    fun roundButton(ctx: Context, label: String, color: Int, sizeSp: Float = 26f, onClick: () -> Unit) = makeButton(ctx, label, color, sizeSp, true, onClick)

    fun popIn(v: View, delay: Long = 0) {
        v.scaleX = 0.2f; v.scaleY = 0.2f; v.alpha = 0f
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(delay).setDuration(420).setInterpolator(OvershootInterpolator(2.5f)).start()
    }

    fun frame(ctx: Context) = FrameLayout(ctx).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) }
    /** Linear layout whose children wrap their content by default (Android's default is match-parent across,
     *  which clips wide children to the narrowest sibling inside a wrap-content parent). */
    private class Lin(ctx: Context) : LinearLayout(ctx) {
        override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }
    fun row(ctx: Context): LinearLayout = Lin(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; isBaselineAligned = false }
    fun col(ctx: Context): LinearLayout = Lin(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    fun lp(w: Int, h: Int, gravity: Int, l: Float = 0f, t: Float = 0f, r: Float = 0f, b: Float = 0f) =
        FrameLayout.LayoutParams(w, h, gravity).apply { setMargins(dpi(l), dpi(t), dpi(r), dpi(b)) }
}

/**
 * Cartoon text: a thick ink outline with a hard drop shadow under it, then the colour on top.
 * The outline is a real stroke, so it reads over any 3D background.
 */
class OutlinedText(ctx: Context, private val outline: Boolean) : TextView(ctx) {
    private var drawing = false
    var strokeColor = Ui.INK
    /** Round buttons: always as wide as tall so the circle never turns into an egg. */
    var square = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (square) { val m = maxOf(measuredWidth, measuredHeight); setMeasuredDimension(m, m) }
    }

    init {
        if (outline) {
            // room for the stroke and shadow so glyph edges never clip
            val pad = Ui.dpi(4f)
            setPadding(pad, pad, pad, pad + Ui.dpi(3f))
        }
    }

    override fun invalidate() { if (!drawing) super.invalidate() }

    override fun onDraw(c: Canvas) {
        if (!outline) { super.onDraw(c); return }
        drawing = true
        val fill = currentTextColor
        val sw = (textSize * 0.16f).coerceIn(Ui.dp(2.5f), Ui.dp(9f))
        paint.strokeJoin = Paint.Join.ROUND
        // pass 1: stroke + hard shadow
        paint.style = Paint.Style.STROKE; paint.strokeWidth = sw
        setTextColor(strokeColor)
        paint.setShadowLayer(0.01f, 0f, sw * 0.55f, strokeColor)
        super.onDraw(c)
        // pass 2: fill
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        setTextColor(fill)
        super.onDraw(c)
        drawing = false
    }
}
