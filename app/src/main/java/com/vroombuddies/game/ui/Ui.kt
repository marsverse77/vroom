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
 * Shared racing look: carbon and steel surfaces, slanted buttons, italic white type and one red accent.
 * Controls stay big and simple for small hands; the styling is what makes it feel like a racing game.
 */
object Ui {
    var density = 1f
    fun dp(v: Float) = v * density
    fun dpi(v: Float) = (v * density).toInt()

    /** Sporty display font (Exo 2 ExtraBold Italic, SIL OFL), plus a bold upright cut for small print. */
    var font: Typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC); private set
    var fontPlain: Typeface = Typeface.create("sans-serif", Typeface.BOLD); private set

    fun init(ctx: Context) {
        density = ctx.resources.displayMetrics.density
        com.vroombuddies.game.render.Textures.loadRoadDetail(ctx.resources)
        font = try { ctx.resources.getFont(R.font.exo2_extrabold_italic) } catch (t: Throwable) { font }
        fontPlain = try { ctx.resources.getFont(R.font.exo2_bold) } catch (t: Throwable) { fontPlain }
    }

    // Racing palette: carbon and steel surfaces, white type, one hot red accent and gold for wins/boost.
    // The old names are kept so every screen picks the palette up from one place.
    const val ACCENT = 0xffc94b1e.toInt()      // racing red: primary actions
    const val GOLD = 0xffffb81c.toInt()        // boost, stars, first place
    const val STEEL = 0xff176773.toInt()       // secondary buttons
    const val CARBON = 0xff103441.toInt()      // panels
    const val YELLOW = GOLD
    const val ORANGE = STEEL
    const val PINK = ACCENT
    const val BLUE = STEEL
    const val SKY = STEEL
    const val GREEN = ACCENT
    const val PURPLE = STEEL
    const val RED = ACCENT
    const val INK = 0xff0a0b0e.toInt()
    const val NAVY = CARBON
    const val OFF = 0xff5a606c.toInt()

    /** Race controls (dp): still big for small hands. */
    const val STEER_SIZE = 122f
    const val BOOST_SIZE = 146f

    /** The italic font already leans; this adds a touch more for big titles. */
    const val SLANT = -0.06f

    fun text(ctx: Context, s: String, sizeSp: Float, color: Int = Color.WHITE, outline: Boolean = true, slant: Boolean = false): TextView =
        OutlinedText(ctx, outline).apply {
            text = s; textSize = sizeSp; typeface = if (slant) font else fontPlain; setTextColor(color); gravity = Gravity.CENTER
            includeFontPadding = false; letterSpacing = 0.02f
            if (slant) { paint.textSkewX = SLANT; setPadding(paddingLeft, paddingTop, paddingRight + (textSize * 0.12f).toInt(), paddingBottom) }
        }

    fun lighten(c: Int, k: Float): Int {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return Color.argb(Color.alpha(c), (r + (255 - r) * k).toInt(), (g + (255 - g) * k).toInt(), (b + (255 - b) * k).toInt())
    }
    fun darken(c: Int, k: Float): Int =
        Color.argb(Color.alpha(c), (Color.red(c) * (1 - k)).toInt(), (Color.green(c) * (1 - k)).toInt(), (Color.blue(c) * (1 - k)).toInt())

    private val chunkyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()

    private val path = Path()

    /**
     * Draw a sleek racing-style shape into [r]: soft drop shadow ([lip] px), thin dark edge, a brushed
     * gradient body and a fine highlight along the top edge. With [slant] > 0 it becomes a parallelogram
     * leaning forward. Buttons, panels and HUD parts all use this so they match.
     */
    fun drawChunky(c: Canvas, r: RectF, radius: Float, color: Int, lip: Float = dp(4f), outline: Float = dp(1.5f), gloss: Boolean = true, p: Paint = chunkyPaint, slant: Float = 0f) {
        p.shader = null; p.style = Paint.Style.FILL; p.pathEffect = null
        val bodyBottom = r.bottom - lip
        fun shape(l: Float, t: Float, rr: Float, b: Float, rad: Float) {
            path.reset()
            if (slant > 0f) {
                p.pathEffect = CornerPathEffect(rad)
                path.moveTo(l + slant, t); path.lineTo(rr, t); path.lineTo(rr - slant, b); path.lineTo(l, b); path.close()
            } else { p.pathEffect = null; tmp.set(l, t, rr, b); path.addRoundRect(tmp, rad, rad, Path.Direction.CW) }
        }
        // shadow
        p.color = 0x66000000; shape(r.left, r.top + lip, r.right, r.bottom, radius); c.drawPath(path, p)
        // edge
        p.color = INK; shape(r.left, r.top, r.right, bodyBottom, radius); c.drawPath(path, p)
        // body
        shape(r.left + outline, r.top + outline, r.right - outline, bodyBottom - outline, radius - outline / 2)
        p.shader = LinearGradient(0f, r.top, 0f, bodyBottom, lighten(color, 0.14f), darken(color, 0.12f), Shader.TileMode.CLAMP)
        c.drawPath(path, p); p.shader = null
        if (gloss) {
            // fine highlight on the top edge and a soft sheen over the upper half
            p.color = 0x22ffffff
            shape(r.left + outline, r.top + outline, r.right - outline, r.top + (bodyBottom - r.top) * 0.48f, radius - outline / 2)
            c.drawPath(path, p)
            p.color = 0x66ffffff; p.style = Paint.Style.STROKE; p.strokeWidth = dp(1f)
            val inset = radius.coerceAtMost((r.width()) * 0.3f)
            c.drawLine(r.left + inset + slant * 0.9f, r.top + outline + dp(0.5f), r.right - inset, r.top + outline + dp(0.5f), p)
            p.style = Paint.Style.FILL
        }
        p.pathEffect = null
    }
    private fun min(a: Float, b: Float) = if (a < b) a else b

    /** Drawable version of [drawChunky] for view backgrounds. */
    class ChunkyDrawable(var color: Int, private val radius: Float, val lip: Float = dp(4f), private val outline: Float = dp(1.5f), private val gloss: Boolean = true, private val slant: Float = 0f) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG); private val r = RectF()
        override fun draw(c: Canvas) { r.set(bounds); drawChunky(c, r, kotlin.math.min(radius, r.height() / 2), color, lip, outline, gloss, p, slant) }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Dark see-through card: for name plates, stats and settings. */
    fun panel(color: Int = 0xee123847.toInt(), radius: Float = 10f) = ChunkyDrawable(color, dp(radius), lip = dp(3f), gloss = false)

    /** Slanted racing button: dips when pressed, springs back. */
    @SuppressLint("ClickableViewAccessibility")
    fun button(ctx: Context, label: String, color: Int, sizeSp: Float = 26f, onClick: () -> Unit): TextView = makeButton(ctx, label, color, sizeSp, false, onClick)

    @SuppressLint("ClickableViewAccessibility")
    fun makeButton(ctx: Context, label: String, color: Int, sizeSp: Float, round: Boolean, onClick: () -> Unit): TextView =
        text(ctx, label, sizeSp * 0.86f, outline = false).apply {
            (this as OutlinedText).square = round
            typeface = fontPlain
            minHeight = dpi(48f)
            minWidth = dpi(48f)
            isFocusable = true
            setOnClickListener { onClick() }
            val bg = ChunkyDrawable(color, dp(if (round) 200f else 20f), lip = dp(4f))
            background = bg
            // round buttons get slim vertical padding: a TextView whose text overflows scrolls, and that
            // would shift the background up and clip the bottom of the circle
            if (round) setPadding(dpi(10f), dpi(3f), dpi(10f), dpi(3f) + bg.lip.toInt())
            else setPadding(dpi(28f), dpi(7f), dpi(28f), dpi(9f) + bg.lip.toInt())
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.94f).scaleY(0.94f).translationY(dp(2f)).setDuration(60).start()
                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1f).scaleY(1f).translationY(0f).setInterpolator(OvershootInterpolator(2f)).setDuration(200).start()
                        if (e.x in 0f..v.width.toFloat() && e.y in 0f..v.height.toFloat()) v.performClick()
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
 * Display text: a thin dark edge and a soft shadow under the colour, so it reads over any 3D background.
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
        val sw = (textSize * 0.06f).coerceIn(Ui.dp(1.2f), Ui.dp(4f))
        paint.strokeJoin = Paint.Join.ROUND
        // pass 1: thin dark edge + soft shadow, so white type reads over a bright 3D scene
        paint.style = Paint.Style.STROKE; paint.strokeWidth = sw
        setTextColor(strokeColor)
        paint.setShadowLayer(Ui.dp(4f), 0f, Ui.dp(2f), 0xaa000000.toInt())
        super.onDraw(c)
        // pass 2: fill
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        setTextColor(fill)
        super.onDraw(c)
        drawing = false
    }
}
