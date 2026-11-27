package com.vroombuddies.game.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.vroombuddies.game.content.Theme
import com.vroombuddies.game.render.*
import com.vroombuddies.game.render.Paint
import com.vroombuddies.game.sim.VehicleParams
import kotlin.math.abs
import kotlin.math.roundToInt

/** Callbacks from menus to the game. */
interface MenuHost {
    fun toCarPicker(); fun toPaintPicker(); fun toTrackPicker(); fun toTitle(); fun toSettings()
    fun pickCar(index: Int); fun pickPaint(index: Int); fun startRace(themeIndex: Int)
    fun applySettings(); fun click()
    val settings: GraphicsSettings
    fun stars(themeIndex: Int): Int
}

object Menus {
    private fun lp(w: Int = -2, h: Int = -2, l: Float = 0f, t: Float = 0f, r: Float = 0f, b: Float = 0f) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(Ui.dpi(l), Ui.dpi(t), Ui.dpi(r), Ui.dpi(b)) }


    /** Title in a leaning ribbon. */
    fun banner(ctx: Context, s: String, color: Int = Ui.ACCENT, sizeSp: Float = 26f): TextView =
        Ui.text(ctx, s.uppercase(), sizeSp, Color.WHITE, slant = true).apply {
            background = BannerDrawable(color)
            setPadding(Ui.dpi(46f), Ui.dpi(6f), Ui.dpi(34f), Ui.dpi(11f))
        }

    private fun checkerEdges(root: FrameLayout) {
        root.addView(CheckerStripView(root.context, 0.6f), Ui.lp(-1, Ui.dpi(5f), Gravity.BOTTOM))
    }

    // ------------------------------------------------------------------ title
    private fun harborCover(ctx: Context) = android.widget.ImageView(ctx).apply {
        setImageResource(com.vroombuddies.game.R.drawable.festival_title)
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun title(ctx: Context, host: MenuHost): View {
        val root = Ui.frame(ctx).apply { setBackgroundColor(Ui.INK) }
        root.addView(harborCover(ctx),FrameLayout.LayoutParams(-1,-1))
        val badge = android.widget.ImageView(ctx).apply {
            setImageResource(com.vroombuddies.game.R.drawable.wordmark)
            contentDescription = "Vroom Buddies"
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        val width = (ctx.resources.configuration.screenWidthDp * 0.34f).coerceIn(155f, 320f)
        root.addView(badge, Ui.lp(Ui.dpi(width), Ui.dpi(width * 0.39f), Gravity.TOP or Gravity.START, l = 16f, t = 8f))
        val play = Ui.button(ctx, "Play", Ui.GREEN, 36f) { host.click(); host.toCarPicker() }.apply {
            background = StartFlagDrawable()
            setPadding(Ui.dpi(45f),0,Ui.dpi(9f),Ui.dpi(6f))
            setTextColor(0xffffef9c.toInt())
        }
        root.addView(play, Ui.lp(Ui.dpi(184f), Ui.dpi(65f), Gravity.BOTTOM or Gravity.END, r = 26f, b = 17f))
        Ui.popIn(play, 120)
        // Grown-ups hold the gear to open settings, so a tap from a small finger does nothing.
        val gear = Ui.roundButton(ctx, "⚙", Ui.PURPLE, 25f) { host.click(); host.toSettings() }
        root.addView(gear, Ui.lp(Ui.dpi(54f), Ui.dpi(54f), Gravity.TOP or Gravity.END, t = 14f, r = 14f))
        gear.contentDescription = "Settings"
        root.addView(Ui.text(ctx, "Settings", 11f, 0xccffffff.toInt()).apply { setOnClickListener { gear.performClick() } }, Ui.lp(Ui.dpi(54f), -2, Gravity.TOP or Gravity.END, t = 67f, r = 14f))
        return root
    }

    /** Swoosh in from the left with a little overshoot, like a car pulling up. */
    private fun zoomIn(v: View, delay: Long) {
        v.translationX = -Ui.dp(500f); v.alpha = 0f; v.scaleX = 1.6f
        v.animate().translationX(0f).alpha(1f).scaleX(1f).setStartDelay(delay).setDuration(420).setInterpolator(OvershootInterpolator(1.6f)).start()
    }

    private fun bob(v: View, i: Int) {
        v.animate().translationY(-Ui.dp(10f)).setStartDelay(900L + i * 90L).setDuration(380).withEndAction {
            v.animate().translationY(0f).setStartDelay(0).setDuration(380).withEndAction { if (v.isAttachedToWindow) bob(v, i) }.start()
        }.start()
    }

    private fun pulse(v: View) {
        v.animate().scaleX(1.07f).scaleY(1.07f).setStartDelay(1100).setDuration(500).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(500).withEndAction { if (v.isAttachedToWindow) pulse(v) }.start()
        }.start()
    }

    /** Back button on the left and a ribbon title in the middle. */
    private fun header(ctx: Context, root: FrameLayout, s: String, color: Int, host: MenuHost, back: () -> Unit) {
        root.addView(Ui.roundButton(ctx, "◀", Ui.STEEL, 30f) { host.click(); back() }, Ui.lp(-2, -2, Gravity.TOP or Gravity.START, l = 14f, t = 12f))
        val b = banner(ctx, s, color, 21f)
        root.addView(b, Ui.lp(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL, t = 6f))
        b.translationY = -Ui.dp(120f)
        b.animate().translationY(0f).setDuration(380).setInterpolator(OvershootInterpolator(2f)).start()
    }

    // ------------------------------------------------------------------ car picker
    @SuppressLint("ClickableViewAccessibility")
    fun carPicker(ctx: Context, host: MenuHost, current: Int): View {
        val root = Ui.frame(ctx)
        checkerEdges(root)
        header(ctx, root, "Pick your car!", Ui.BLUE, host) { host.toTitle() }
        val designs = CarDesigns.all
        var idx = current.coerceIn(0, designs.size - 1)
        val name = Ui.text(ctx, designs[idx].name.uppercase(), 26f, Color.WHITE, slant = true)
        val tag = Ui.text(ctx, designs[idx].tagline, 15f, 0xccffffff.toInt()).apply { typeface = Ui.fontPlain }
        val stats = StatsView(ctx).apply { set(designs[idx].params) }
        val dots = PageDots(ctx, designs.size, idx)
        fun show(dir: Int) {
            name.text = designs[idx].name.uppercase(); tag.text = designs[idx].tagline
            name.translationX = Ui.dp(120f) * dir; name.alpha = 0f
            name.animate().translationX(0f).alpha(1f).setStartDelay(0).setDuration(260).setInterpolator(OvershootInterpolator(2f)).start()
            stats.set(designs[idx].params); dots.current = idx
            host.pickCar(idx)
        }
        fun step(d: Int) { host.click(); idx = (idx + d + designs.size) % designs.size; show(d) }
        root.addView(Ui.roundButton(ctx, "◀", Ui.STEEL, 42f) { step(-1) }, Ui.lp(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL, l = 22f, b = 20f))
        root.addView(Ui.roundButton(ctx, "▶", Ui.STEEL, 42f) { step(1) }, Ui.lp(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL, r = 22f, b = 20f))
        // Swipe anywhere on the car to flip through them too.
        var downX = 0f
        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> downX = e.x
                MotionEvent.ACTION_UP -> { val dx = e.x - downX; if (abs(dx) > Ui.dp(70f)) step(if (dx < 0) 1 else -1) }
            }
            true
        }
        // bottom bar: stats card, name plate, go button
        val bar = Ui.row(ctx).apply { gravity = Gravity.BOTTOM }
        bar.addView(stats, LinearLayout.LayoutParams(Ui.dpi(160f), Ui.dpi(67f)))
        val plate = Ui.col(ctx).apply { background = Ui.panel(); setPadding(Ui.dpi(14f), Ui.dpi(2f), Ui.dpi(14f), Ui.dpi(5f)) }
        plate.addView(dots, lp(w = Ui.dpi(20f) * designs.size + Ui.dpi(10f), h = Ui.dpi(14f), t = 4f))
        plate.addView(name); plate.addView(tag)
        bar.addView(plate, lp(l = 14f, r = 14f))
        val go = Ui.button(ctx, "Next ▶", Ui.GREEN, 28f) { host.click(); host.toPaintPicker() }
        bar.addView(go)
        pulse(go)
        root.addView(bar, Ui.lp(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, b = 7f))
        Ui.popIn(stats, 150); Ui.popIn(plate, 80); Ui.popIn(go, 220)
        return root
    }

    /** Speed / grip / boost as chunky pips. Cars differ by about ±10%, so the pips spread that range out. */
    class StatsView(ctx: Context) : View(ctx) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val txt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font; color = Color.WHITE }
        private val base = VehicleParams()
        private val values = IntArray(3); private val shown = FloatArray(3)
        private val labels = arrayOf("SPEED", "GRIP", "BOOST"); private val colors = intArrayOf(Ui.SKY, Ui.PINK, Ui.GOLD)
        private val bg = Ui.panel(); private val rf = RectF()
        private var first = true
        private fun pips(v: Float, b: Float) = (3 + (v / b - 1f) * 20f).roundToInt().coerceIn(1, 5)
        fun set(v: VehicleParams) {
            values[0] = pips(v.topSpeed, base.topSpeed); values[1] = pips(v.grip, base.grip); values[2] = pips(v.boostSpeed, base.boostSpeed)
            if (first) { first = false; for (i in 0 until 3) shown[i] = values[i].toFloat() }
            invalidate()
        }
        override fun onDraw(c: Canvas) {
            var again = false
            bg.setBounds(0, 0, width, height); bg.draw(c)
            val inner = height - Ui.dp(12f); val rowH = inner / 3f; val pip = Ui.dp(13f); val gap = Ui.dp(3f)
            txt.textSize = Ui.dp(14f)
            for (i in 0 until 3) {
                shown[i] += (values[i] - shown[i]) * 0.25f
                if (abs(values[i] - shown[i]) > 0.02f) again = true
                val cy = Ui.dp(4f) + rowH * (i + 0.5f)
                c.drawText(labels[i], Ui.dp(14f), cy + Ui.dp(5f), txt)
                for (k in 0 until 5) {
                    val x = Ui.dp(69f) + k * (pip + gap)
                    rf.set(x, cy - pip / 2, x + pip, cy + pip / 2)
                    p.color = Ui.INK; c.drawRoundRect(rf.left - Ui.dp(1.5f), rf.top - Ui.dp(1.5f), rf.right + Ui.dp(1.5f), rf.bottom + Ui.dp(2.5f), Ui.dp(6f), Ui.dp(6f), p)
                    p.color = if (k < shown[i] - 0.01f) colors[i] else 0x33ffffff
                    c.drawRoundRect(rf, Ui.dp(5f), Ui.dp(5f), p)
                    if (k < shown[i] - 0.01f) { p.color = 0x55ffffff; c.drawRoundRect(rf.left + Ui.dp(3f), rf.top + Ui.dp(2f), rf.right - Ui.dp(3f), rf.top + Ui.dp(6f), Ui.dp(2f), Ui.dp(2f), p) }
                }
            }
            if (again) postInvalidateOnAnimation()
        }
    }

    /** Little dots that show which car out of how many. */
    class PageDots(ctx: Context, private val n: Int, cur: Int) : View(ctx) {
        var current = cur; set(v) { field = v; invalidate() }
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            if (n <= 1) return
            val step = Ui.dp(20f); val x0 = (width - step * (n - 1)) / 2f; val cy = height / 2f
            for (i in 0 until n) {
                p.color = if (i == current) Ui.ACCENT else 0x88ffffff.toInt()
                c.drawCircle(x0 + i * step, cy, Ui.dp(if (i == current) 7f else 4.5f), p)
            }
        }
    }

    // ------------------------------------------------------------------ paint picker
    fun paintPicker(ctx: Context, host: MenuHost, current: Int): View {
        val root = Ui.frame(ctx)
        checkerEdges(root)
        header(ctx, root, "Pick a colour!", Ui.PINK, host) { host.toCarPicker() }
        val grid = Ui.col(ctx)
        val paints = Paints.all
        val swatches = ArrayList<Swatch>()
        val perRow = (paints.size + 1) / 2
        val swatchSize = ((ctx.resources.configuration.screenWidthDp - 200f) / perRow - 6f).coerceIn(36f, 52f)
        var row: LinearLayout? = null
        paints.forEachIndexed { i, p ->
            if (i % perRow == 0) { row = Ui.row(ctx); grid.addView(row) }
            val sw = Swatch(ctx, p, i == current).apply { contentDescription = p.name }
            sw.setOnClickListener {
                host.click(); swatches.forEach { it.picked = false; it.invalidate() }; sw.picked = true
                sw.scaleX = 0.6f; sw.scaleY = 0.6f
                sw.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(OvershootInterpolator(4f)).start()
                host.pickPaint(i)
            }
            swatches.add(sw)
            row!!.addView(sw, LinearLayout.LayoutParams(Ui.dpi(swatchSize), Ui.dpi(swatchSize)).apply { setMargins(Ui.dpi(3f), Ui.dpi(2f), Ui.dpi(3f), Ui.dpi(2f)) })
            Ui.popIn(sw, 25L * i)
        }
        grid.background = Ui.panel(); grid.setPadding(Ui.dpi(12f), Ui.dpi(8f), Ui.dpi(12f), Ui.dpi(14f))
        // swatches and the go button share one row along the bottom so the car stays visible above them
        val bottom = Ui.row(ctx)
        bottom.addView(grid)
        val go = Ui.button(ctx, "Next ▶", Ui.GREEN, 28f) { host.click(); host.toTrackPicker() }
        bottom.addView(go, lp(l = 16f)); pulse(go)
        root.addView(bottom, Ui.lp(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, b = 22f))
        return root
    }

    /** Glossy paint blob with an ink outline. Rainbow and chrome get special drawing. The picked one gets a tick. */
    class Swatch(ctx: Context, private val paint: Paint, var picked: Boolean) : View(ctx) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val txt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font; textAlign = android.graphics.Paint.Align.CENTER }
        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f - Ui.dp(2f)
            val r = width / 2f - Ui.dp(if (picked) 4f else 8f)
            val base = paint.color or 0xff000000.toInt()
            p.style = android.graphics.Paint.Style.FILL; p.shader = null
            if (picked) { p.color = Color.WHITE; c.drawCircle(cx, cy, r + Ui.dp(4.5f), p) }
            p.color = 0x66000000; c.drawCircle(cx, cy + Ui.dp(3f), r + Ui.dp(1.5f), p)
            p.color = Ui.INK; c.drawCircle(cx, cy, r + Ui.dp(1.5f), p)
            p.color = Color.BLACK // opaque: the shader is drawn with the paint's alpha
            p.shader = when {
                paint.rainbow -> SweepGradient(cx, cy, intArrayOf(0xffff1744.toInt(), 0xffffea00.toInt(), 0xff00e676.toInt(), 0xff2979ff.toInt(), 0xffd500f9.toInt(), 0xffff1744.toInt()), null)
                paint.metallic > 0.5f -> LinearGradient(cx - r, cy - r, cx + r, cy + r, intArrayOf(Color.WHITE, base, Ui.lighten(base, 0.6f), base), null, Shader.TileMode.CLAMP)
                else -> RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.4f, Ui.lighten(base, 0.4f), Ui.darken(base, 0.1f), Shader.TileMode.CLAMP)
            }
            c.drawCircle(cx, cy, r, p); p.shader = null
            // gloss
            p.color = 0x77ffffff; c.drawOval(cx - r * 0.55f, cy - r * 0.8f, cx + r * 0.25f, cy - r * 0.3f, p)
            if (picked) {
                txt.textSize = r * 1.1f; txt.style = android.graphics.Paint.Style.STROKE; txt.strokeWidth = Ui.dp(4f); txt.color = Ui.INK
                c.drawText("✔", cx, cy + txt.textSize * 0.36f, txt)
                txt.style = android.graphics.Paint.Style.FILL; txt.color = Color.WHITE
                c.drawText("✔", cx, cy + txt.textSize * 0.36f, txt)
            }
        }
    }

    // ------------------------------------------------------------------ track picker
    fun trackPicker(ctx: Context, host: MenuHost): View {
        val root = Ui.frame(ctx)
        root.setBackgroundColor(0x883761ad.toInt())
        root.addView(SpeedLinesView(ctx, 0x30ffffff, 18), FrameLayout.LayoutParams(-1, -1))
        checkerEdges(root)
        header(ctx, root, "Where to race?", Ui.PURPLE, host) { host.toPaintPicker() }
        val scroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(Ui.dpi(20f), Ui.dpi(6f), Ui.dpi(20f), Ui.dpi(6f)) }
        val strip = Ui.row(ctx)
        val cardHeight = ((ctx.resources.displayMetrics.heightPixels / Ui.density - 86f) * 0.82f).coerceIn(120f, 370f)
        val posters = BitmapFactory.decodeResource(ctx.resources, com.vroombuddies.game.R.drawable.festival_worlds)
        com.vroombuddies.game.content.Themes.all.forEachIndexed { i, th ->
            val tile = trackTile(ctx, th, i, host.stars(i), posters)
            tile.setOnClickListener { host.click(); tile.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).withEndAction { host.startRace(i) }.start() }
            strip.addView(tile, LinearLayout.LayoutParams(Ui.dpi(cardHeight * 0.82f), Ui.dpi(cardHeight)).apply { setMargins(Ui.dpi(8f), 0, Ui.dpi(8f), 0) })
            Ui.popIn(tile, 40L * i)
        }
        scroll.addView(strip, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START))
        root.addView(scroll, Ui.lp(-1, -1, Gravity.TOP, t = 66f, b = 8f))
        return root
    }

    private fun trackTile(ctx: Context, th: Theme, index: Int, stars: Int, posters: Bitmap): View {
        val card = FrameLayout(ctx)
        card.background = TileDrawable(th.skyTop or 0xff000000.toInt(), th.skyHorizon or 0xff000000.toInt(), (th.ground?.first ?: th.skyBottom) or 0xff000000.toInt())
        card.addView(CoursePostcard(ctx, posters, index), Ui.lp(-1,-1,Gravity.CENTER))
        val map = TrackMapView(ctx, th.shape, Color.WHITE).apply { background = Ui.panel(0xb0265182.toInt(), 12f) }
        card.addView(map, Ui.lp(Ui.dpi(48f), Ui.dpi(48f), Gravity.TOP or Gravity.END,r=7f,t=6f))
        // number badge
        val num = Ui.text(ctx, "%02d".format(index + 1), 15f).apply { background = Ui.ChunkyDrawable(Ui.ACCENT, Ui.dp(4f), lip = Ui.dp(2f), gloss = false, slant = Ui.dp(6f)); setPadding(Ui.dpi(12f), Ui.dpi(1f), Ui.dpi(12f), Ui.dpi(4f)) }
        card.addView(num, Ui.lp(-2, -2, Gravity.TOP or Gravity.START, l = 8f, t = 8f))
        // name band
        val band = Ui.col(ctx).apply { background = Ui.panel(0xf02c5586.toInt(), 16f); setPadding(Ui.dpi(8f), Ui.dpi(4f), Ui.dpi(8f), Ui.dpi(10f)) }
        band.addView(Ui.text(ctx, th.name, 16f, slant = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        band.addView(StarsView(ctx, stars), lp(w = Ui.dpi(120f), h = Ui.dpi(30f)))
        card.addView(band, Ui.lp(-1, -2, Gravity.BOTTOM, l = 10f, r = 10f, b = 16f))
        card.clipChildren = false
        return card
    }

    /** Three fat outlined stars, the earned ones gold. */
    class StarsView(ctx: Context, private val earned: Int, private val count: Int = 3) : View(ctx) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = android.graphics.Paint.Join.ROUND }
        private val path = Path()
        override fun onDraw(c: Canvas) {
            val r = height / 2f - Ui.dp(3f); val step = width / count.toFloat()
            for (i in 0 until count) {
                val cx = step * (i + 0.5f); val cy = height / 2f
                star(cx, cy, r)
                p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = (r * 0.1f).coerceIn(Ui.dp(2f), Ui.dp(4f)); p.color = Ui.INK; c.drawPath(path, p)
                p.style = android.graphics.Paint.Style.FILL; p.color = if (i < earned) Ui.GOLD else 0x40ffffff; c.drawPath(path, p)
                if (i < earned && r > Ui.dp(20f)) { p.color = 0x66ffffff; c.drawCircle(cx - r * 0.2f, cy - r * 0.25f, r * 0.18f, p) }
            }
        }
        private fun star(cx: Float, cy: Float, r: Float) {
            path.reset()
            for (k in 0 until 10) {
                val a = Math.PI * k / 5 - Math.PI / 2; val rr = if (k % 2 == 0) r else r * 0.48f
                val x = cx + (Math.cos(a) * rr).toFloat(); val y = cy + (Math.sin(a) * rr).toFloat()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
    }

    // ------------------------------------------------------------------ settings
    /** Quick presets for grown-ups who don't want to fiddle. Every option stays editable below. */
    enum class Preset(val label: String, val color: Int) { SMOOTH("Smooth", Ui.STEEL), BALANCED("Balanced", Ui.STEEL), PRETTY("Pretty", Ui.STEEL), MAX("Max", Ui.ACCENT) }

    fun applyPreset(s: GraphicsSettings, p: Preset) {
        val lvl = when (p) { Preset.SMOOTH -> Level.LOW; Preset.BALANCED -> Level.MEDIUM; else -> Level.HIGH }
        s.antiAliasing = when (p) { Preset.SMOOTH -> AaMode.FXAA; Preset.MAX -> AaMode.TAA; else -> AaMode.MSAA }
        s.shadows = lvl; s.textureQuality = lvl; s.effects = lvl
        s.ssao = when (p) { Preset.SMOOTH -> Level.OFF; Preset.BALANCED -> Level.LOW; Preset.PRETTY -> Level.MEDIUM; Preset.MAX -> Level.HIGH }
        s.anisotropic = when (p) { Preset.SMOOTH -> Level.LOW; Preset.BALANCED -> Level.MEDIUM; else -> Level.HIGH }
        s.bloom = true
        s.reflections = p == Preset.MAX
        s.motionBlur = p != Preset.SMOOTH
        s.godRays = p != Preset.SMOOTH
        s.depthOfField = p == Preset.PRETTY || p == Preset.MAX
        s.foliageWind = p != Preset.SMOOTH
        s.fog = true
        s.dynamicResolution = true
        s.targetFps = 60
    }

    fun settings(ctx: Context, host: MenuHost): View {
        val s = host.settings
        val root = Ui.frame(ctx)
        root.setBackgroundColor(0xcc1d1b3a.toInt())
        header(ctx, root, "Grown-up settings", Ui.ACCENT, host) { s.save(); host.toTitle() }
        val list = Ui.col(ctx)
        val refreshers = ArrayList<() -> Unit>()

        fun section(title: String) {
            list.addView(Ui.text(ctx, title.uppercase(), 16f, Ui.ACCENT, slant = true).apply { gravity = Gravity.START }, lp(w = Ui.dpi(520f), t = 12f))
        }
        fun <T> option(label: String, hint: String, values: List<T>, get: () -> T, set: (T) -> Unit, name: (T) -> String) {
            val row = Ui.row(ctx)
            val texts = Ui.col(ctx).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
            texts.addView(Ui.text(ctx, label, 16f).apply { gravity = Gravity.START; typeface = Ui.fontPlain })
            texts.addView(Ui.text(ctx, hint, 12f, 0xaaffffff.toInt(), outline = false).apply { gravity = Gravity.START; typeface = Ui.fontPlain; setPadding(Ui.dpi(4f), 0, 0, 0) })
            row.addView(texts, LinearLayout.LayoutParams(Ui.dpi(330f), -2))
            fun colorOf(v: T) = when (v) { true, AaMode.TAA, Level.HIGH, 60 -> Ui.ACCENT; false, AaMode.OFF, Level.OFF -> Ui.OFF; else -> Ui.STEEL }
            lateinit var btn: TextView
            btn = Ui.button(ctx, name(get()), colorOf(get()), 18f) {
                host.click()
                val n = values[(values.indexOf(get()) + 1) % values.size]; set(n)
                btn.text = name(n); Ui.tintButton(btn, colorOf(n))
                host.applySettings()
            }
            refreshers.add { btn.text = name(get()); Ui.tintButton(btn, colorOf(get())) }
            row.addView(btn, LinearLayout.LayoutParams(Ui.dpi(150f), -2).apply { setMargins(0, Ui.dpi(4f), 0, Ui.dpi(4f)) })
            list.addView(row)
        }
        val onOff = listOf(true, false)
        fun b(v: Boolean) = if (v) "On" else "Off"
        val levels = Level.values().toList()

        // Presets
        val presets = Ui.row(ctx)
        Preset.values().forEach { p ->
            presets.addView(Ui.button(ctx, p.label, p.color, 18f) {
                host.click(); applyPreset(s, p); refreshers.forEach { it() }; host.applySettings()
            }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(Ui.dpi(5f), Ui.dpi(2f), Ui.dpi(5f), Ui.dpi(2f)) })
        }
        list.addView(presets)

        section("Edges and detail")
        option("Anti-aliasing", "Smooths jagged edges", AaMode.values().toList(), { s.antiAliasing }, { s.antiAliasing = it }) { it.label }
        option("Texture quality", "Sharpness of surfaces", listOf(Level.LOW, Level.MEDIUM, Level.HIGH), { s.textureQuality }, { s.textureQuality = it }) { it.label }
        option("Anisotropic filtering", "Keeps the road sharp far away", levels, { s.anisotropic }, { s.anisotropic = it }) { when (it) { Level.OFF -> "Off"; Level.LOW -> "2x"; Level.MEDIUM -> "4x"; else -> "16x" } }

        section("Light and shadow")
        option("Shadows", "Soft shadows from the sun", levels, { s.shadows }, { s.shadows = it }) { it.label }
        option("Ambient occlusion (SSAO)", "Soft contact shadows where things meet", levels, { s.ssao }, { s.ssao = it }) { it.label }
        option("Bloom and lens flare", "Glow around bright lights and boosts", onOff, { s.bloom }, { s.bloom = it }, ::b)
        option("God rays", "Sunbeams through the sky", onOff, { s.godRays }, { s.godRays = it }, ::b)
        option("Reflections (SSR)", "Shiny reflections on the road", onOff, { s.reflections }, { s.reflections = it }, ::b)
        option("Fog and atmosphere", "Distance haze", onOff, { s.fog }, { s.fog = it }, ::b)

        section("Camera and effects")
        option("Motion blur", "Speed streaks when going fast", onOff, { s.motionBlur }, { s.motionBlur = it }, ::b)
        option("Depth of field", "Blurry background in jump close-ups", onOff, { s.depthOfField }, { s.depthOfField = it }, ::b)
        option("Particle effects", "Sparks, dust, smoke and confetti", levels, { s.effects }, { s.effects = it }) { it.label }
        option("Foliage wind", "Trees and grass sway", onOff, { s.foliageWind }, { s.foliageWind = it }, ::b)

        section("Sound")
        val audio = com.vroombuddies.game.audio.AudioSettings.of(ctx); val vols = com.vroombuddies.game.audio.Volume.values().toList()
        option("Music", "Songs in menus and races", vols, { audio.music }, { audio.music = it }) { it.label }
        option("Sound effects", "Engine, boosts and crashes", vols, { audio.effects }, { audio.effects = it }) { it.label }

        section("Speed")
        option("Dynamic resolution", "Drops resolution a little to stay smooth", onOff, { s.dynamicResolution }, { s.dynamicResolution = it }, ::b)
        option("Frame rate (V-Sync)", "60 is steady, 120 needs a fast screen, 30 saves battery", listOf(60, 120, 30), { s.targetFps }, { s.targetFps = it }) { if (it == 120) "120 fps (fast screens)" else "$it fps" }
        list.addView(Ui.text(ctx, "Shadow, texture and anisotropy changes apply on the next race.", 12f, 0xccffffff.toInt(), outline = false), lp(t = 10f))
        list.setPadding(Ui.dpi(22f), Ui.dpi(10f), Ui.dpi(22f), Ui.dpi(22f))

        val sc = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; background = Ui.panel(0xef214974.toInt()); clipToPadding = true }
        sc.addView(list)
        root.addView(sc, Ui.lp(-2, -1, Gravity.CENTER_HORIZONTAL, t = 82f, b = 14f))
        return root
    }

    // ------------------------------------------------------------------ pause
    fun pause(ctx: Context, onResume: () -> Unit, onRestart: () -> Unit, onHome: () -> Unit): View {
        val shade = Ui.frame(ctx).apply { setBackgroundColor(0x991d1b3a.toInt()); isClickable = true }
        val col = Ui.col(ctx)
        col.addView(banner(ctx, "Paused", Ui.ACCENT, 34f))
        val row = Ui.row(ctx)
        fun big(label: String, color: Int, size: Float, f: () -> Unit) = Ui.makeButton(ctx, label, color, size, true, f)
        row.addView(big("🔁", Ui.BLUE, 36f, onRestart), lp(w = Ui.dpi(92f), h = Ui.dpi(92f), l = 14f, r = 14f))
        val go = big("▶", Ui.GREEN, 46f, onResume)
        row.addView(go, lp(w = Ui.dpi(116f), h = Ui.dpi(116f), l = 14f, r = 14f))
        row.addView(big("🏠", Ui.ORANGE, 36f, onHome), lp(w = Ui.dpi(92f), h = Ui.dpi(92f), l = 14f, r = 14f))
        row.setPadding(0, Ui.dpi(16f), 0, 0)
        col.addView(row)
        shade.addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        Ui.popIn(col); pulse(go)
        return shade
    }

    // ------------------------------------------------------------------ loading
    fun loading(ctx: Context, msg: String): View {
        val root = Ui.frame(ctx)
        root.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xff278bc2.toInt(), 0xff4252a2.toInt()))
        root.addView(harborCover(ctx), FrameLayout.LayoutParams(-1,-1))
        val col = Ui.col(ctx).apply {
            background = Ui.panel(0xef205585.toInt(),20f)
            setPadding(Ui.dpi(14f),Ui.dpi(7f),Ui.dpi(14f),Ui.dpi(12f))
        }
        val badge = android.widget.ImageView(ctx).apply {
            setImageResource(com.vroombuddies.game.R.drawable.wordmark)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            contentDescription = "Vroom Buddies"
        }
        val width = (ctx.resources.configuration.screenWidthDp * 0.28f).coerceIn(145f, 240f)
        col.addView(badge, lp(Ui.dpi(width), Ui.dpi(width * 0.38f)))
        col.addView(Ui.text(ctx, msg, 19f, outline = false), lp(t = 12f))
        val progress = android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        progress.isIndeterminate = true
        progress.indeterminateTintList = android.content.res.ColorStateList.valueOf(Ui.GOLD)
        col.addView(progress, lp(Ui.dpi(width * 0.68f), Ui.dpi(8f), t = 10f))
        root.addView(col, Ui.lp(-2,-2,Gravity.BOTTOM or Gravity.START,l=16f,b=14f))
        return root
    }
}
