package com.vroombuddies.game.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.view.View
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

    fun title(ctx: Context, host: MenuHost): View {
        val root = Ui.frame(ctx)
        root.addView(SpeedLinesView(ctx), FrameLayout.LayoutParams(-1, -1))
        val col = Ui.col(ctx)
        val logoRow = Ui.row(ctx)
        val colors = intArrayOf(Ui.PINK, Ui.ORANGE, Ui.YELLOW, Ui.GREEN, Ui.BLUE, Ui.PURPLE)
        "VROOM".forEachIndexed { i, ch -> logoRow.addView(Ui.text(ctx, ch.toString(), 80f, colors[i % colors.size]).also { zoomIn(it, 70L * i); bob(it, i) }) }
        logoRow.rotation = -4f
        val logo2 = Ui.text(ctx, "BUDDIES!", 54f, Color.WHITE).also { zoomIn(it, 450); it.rotation = -4f }
        col.addView(logoRow); col.addView(logo2)
        val play = Ui.button(ctx, "▶  PLAY", Ui.GREEN, 44f) { host.click(); host.toCarPicker() }
        col.addView(play, lp(t = 26f)); Ui.popIn(play, 750); pulse(play)
        root.addView(col, Ui.lp(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START, l = 48f))
        // Grown-ups hold the gear to open settings, so a tap from a small finger does nothing.
        val gear = HoldButton(ctx, "⚙", Ui.PURPLE) { host.click(); host.toSettings() }
        root.addView(gear, Ui.lp(Ui.dpi(76f), Ui.dpi(76f), Gravity.TOP or Gravity.END, t = 16f, r = 16f))
        root.addView(Ui.text(ctx, "hold", 13f, 0xccffffff.toInt()), Ui.lp(Ui.dpi(76f), -2, Gravity.TOP or Gravity.END, t = 94f, r = 16f))
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

    private fun header(ctx: Context, s: String, host: MenuHost, back: () -> Unit): FrameLayout {
        val bar = FrameLayout(ctx)
        bar.addView(Ui.button(ctx, "◀", Ui.ORANGE, 28f) { host.click(); back() }, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        bar.addView(Ui.text(ctx, s, 40f).also { Ui.popIn(it) }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        return bar
    }

    @SuppressLint("ClickableViewAccessibility")
    fun carPicker(ctx: Context, host: MenuHost, current: Int): View {
        val root = Ui.frame(ctx)
        root.addView(header(ctx, "Pick your car!", host) { host.toTitle() }, Ui.lp(-1, -2, Gravity.TOP, 20f, 16f, 20f))
        val designs = CarDesigns.all
        var idx = current.coerceIn(0, designs.size - 1)
        val name = Ui.text(ctx, designs[idx].name, 48f, Ui.YELLOW)
        val tag = Ui.text(ctx, designs[idx].tagline, 22f)
        val stats = StatsView(ctx).apply { set(designs[idx].params) }
        val dots = PageDots(ctx, designs.size, idx)
        fun show(dir: Int) {
            name.text = designs[idx].name; tag.text = designs[idx].tagline
            name.translationX = Ui.dp(120f) * dir; name.alpha = 0f
            name.animate().translationX(0f).alpha(1f).setStartDelay(0).setDuration(260).setInterpolator(OvershootInterpolator(2f)).start()
            stats.set(designs[idx].params); dots.current = idx
            host.pickCar(idx)
        }
        fun step(d: Int) { host.click(); idx = (idx + d + designs.size) % designs.size; show(d) }
        root.addView(Ui.button(ctx, "◀", Ui.BLUE, 44f) { step(-1) }, Ui.lp(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL, l = 28f))
        root.addView(Ui.button(ctx, "▶", Ui.BLUE, 44f) { step(1) }, Ui.lp(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL, r = 28f))
        root.addView(stats, Ui.lp(Ui.dpi(210f), Ui.dpi(120f), Gravity.END or Gravity.TOP, t = 96f, r = 24f))
        Ui.popIn(stats, 200)
        // Swipe anywhere on the car to flip through them too.
        var downX = 0f
        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> downX = e.x
                MotionEvent.ACTION_UP -> { val dx = e.x - downX; if (abs(dx) > Ui.dp(70f)) step(if (dx < 0) 1 else -1) }
            }
            true
        }
        val bottom = Ui.col(ctx)
        bottom.addView(dots, lp(w = Ui.dpi(20f) * designs.size + Ui.dpi(10f), h = Ui.dpi(22f)))
        bottom.addView(name); bottom.addView(tag)
        bottom.addView(Ui.button(ctx, "✔  This one!", Ui.GREEN, 30f) { host.click(); host.toPaintPicker() }, lp(t = 10f))
        root.addView(bottom, Ui.lp(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, b = 18f))
        return root
    }

    /** Speed / grip / boost as chunky pips. Cars differ by about ±10%, so the pips spread that range out. */
    class StatsView(ctx: Context) : View(ctx) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val txt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font; color = Color.WHITE }
        private val base = VehicleParams()
        private val values = IntArray(3); private val shown = FloatArray(3)
        private val labels = arrayOf("⚡", "🛞", "🚀"); private val colors = intArrayOf(Ui.ORANGE, Ui.GREEN, Ui.PINK)
        private fun pips(v: Float, b: Float) = (3 + (v / b - 1f) * 20f).roundToInt().coerceIn(1, 5)
        fun set(v: VehicleParams) {
            values[0] = pips(v.topSpeed, base.topSpeed); values[1] = pips(v.grip, base.grip); values[2] = pips(v.boostSpeed, base.boostSpeed)
            invalidate()
        }
        override fun onDraw(c: Canvas) {
            var again = false
            p.color = 0x881d1b3a.toInt(); c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), Ui.dp(22f), Ui.dp(22f), p)
            val rowH = height / 3f; val pip = Ui.dp(22f); val gap = Ui.dp(6f)
            txt.textSize = Ui.dp(22f)
            for (i in 0 until 3) {
                shown[i] += (values[i] - shown[i]) * 0.25f
                if (abs(values[i] - shown[i]) > 0.02f) again = true
                val cy = rowH * (i + 0.5f)
                c.drawText(labels[i], Ui.dp(12f), cy + Ui.dp(8f), txt)
                for (k in 0 until 5) {
                    val x = Ui.dp(52f) + k * (pip + gap)
                    p.color = if (k < shown[i] - 0.01f) colors[i] else 0x44ffffff
                    c.drawRoundRect(x, cy - pip / 2, x + pip, cy + pip / 2, Ui.dp(6f), Ui.dp(6f), p)
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
            val step = Ui.dp(20f); val x0 = (width - step * (n - 1)) / 2f; val cy = height / 2f
            for (i in 0 until n) {
                p.color = if (i == current) Ui.YELLOW else 0x88ffffff.toInt()
                c.drawCircle(x0 + i * step, cy, Ui.dp(if (i == current) 8f else 5f), p)
            }
        }
    }

    fun paintPicker(ctx: Context, host: MenuHost, current: Int): View {
        val root = Ui.frame(ctx)
        root.addView(header(ctx, "Pick a colour!", host) { host.toCarPicker() }, Ui.lp(-1, -2, Gravity.TOP, 20f, 16f, 20f))
        val grid = Ui.col(ctx)
        val paints = Paints.all
        val swatches = ArrayList<View>()
        val perRow = 7
        var row: LinearLayout? = null
        paints.forEachIndexed { i, p ->
            if (i % perRow == 0) { row = Ui.row(ctx); grid.addView(row) }
            val sw = Swatch(ctx, p, i == current)
            sw.setOnClickListener { host.click(); swatches.forEach { (it as Swatch).picked = false; it.invalidate() }; sw.picked = true; Ui.popIn(sw); host.pickPaint(i) }
            swatches.add(sw)
            row!!.addView(sw, LinearLayout.LayoutParams(Ui.dpi(74f), Ui.dpi(74f)).apply { setMargins(Ui.dpi(6f), Ui.dpi(6f), Ui.dpi(6f), Ui.dpi(6f)) })
            Ui.popIn(sw, 30L * i)
        }
        val bottom = Ui.col(ctx)
        bottom.addView(grid)
        bottom.addView(Ui.button(ctx, "✔  Let's race!", Ui.GREEN, 30f) { host.click(); host.toTrackPicker() }, lp(t = 8f))
        root.addView(bottom, Ui.lp(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, b = 14f))
        return root
    }

    /** Round paint blob. Rainbow and chrome get special drawing. */
    class Swatch(ctx: Context, private val paint: Paint, var picked: Boolean) : View(ctx) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val r = width / 2f - Ui.dp(6f)
            p.style = android.graphics.Paint.Style.FILL
            p.color = 0x55000000; c.drawCircle(width / 2f, height / 2f + Ui.dp(4f), r, p)
            p.shader = when {
                paint.rainbow -> SweepGradient(width / 2f, height / 2f, intArrayOf(0xffff1744.toInt(), 0xffffea00.toInt(), 0xff00e676.toInt(), 0xff2979ff.toInt(), 0xffd500f9.toInt(), 0xffff1744.toInt()), null)
                paint.metallic > 0.5f -> LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), intArrayOf(Color.WHITE, paint.color or 0xff000000.toInt(), Ui.lighten(paint.color or 0xff000000.toInt(), 0.6f)), null, Shader.TileMode.CLAMP)
                else -> RadialGradient(width * 0.4f, height * 0.35f, r * 1.3f, Ui.lighten(paint.color or 0xff000000.toInt(), 0.45f), paint.color or 0xff000000.toInt(), Shader.TileMode.CLAMP)
            }
            c.drawCircle(width / 2f, height / 2f, r, p); p.shader = null
            p.style = android.graphics.Paint.Style.STROKE
            p.strokeWidth = Ui.dp(if (picked) 6f else 3f); p.color = if (picked) Ui.YELLOW else Ui.INK
            c.drawCircle(width / 2f, height / 2f, r, p)
        }
    }

    fun trackPicker(ctx: Context, host: MenuHost): View {
        val root = Ui.frame(ctx)
        root.setBackgroundColor(0x66000000)
        root.addView(SpeedLinesView(ctx, 0x33ffffff, 18), FrameLayout.LayoutParams(-1, -1))
        root.addView(header(ctx, "Where to race?", host) { host.toPaintPicker() }, Ui.lp(-1, -2, Gravity.TOP, 20f, 16f, 20f))
        val scroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(Ui.dpi(24f), 0, Ui.dpi(24f), 0) }
        val strip = Ui.row(ctx)
        com.vroombuddies.game.content.Themes.all.forEachIndexed { i, th ->
            val tile = trackTile(ctx, th, i, host.stars(i))
            tile.setOnClickListener { host.click(); tile.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).withEndAction { host.startRace(i) }.start() }
            strip.addView(tile, LinearLayout.LayoutParams(Ui.dpi(200f), Ui.dpi(240f)).apply { setMargins(Ui.dpi(10f), 0, Ui.dpi(10f), 0) })
            Ui.popIn(tile, 40L * i)
        }
        scroll.addView(strip)
        root.addView(scroll, Ui.lp(-1, -2, Gravity.CENTER_VERTICAL, t = 40f))
        return root
    }

    private fun trackTile(ctx: Context, th: Theme, index: Int, stars: Int): View {
        val col = Ui.col(ctx)
        col.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(th.skyTop or 0xff000000.toInt(), th.skyHorizon or 0xff000000.toInt(), (th.ground?.first ?: th.skyBottom) or 0xff000000.toInt())).apply {
            cornerRadius = Ui.dp(30f); setStroke(Ui.dpi(5f), Color.WHITE)
        }
        col.elevation = Ui.dp(8f)
        val top = Ui.row(ctx)
        top.addView(Ui.text(ctx, th.emoji, 54f, outline = false))
        top.addView(TrackMapView(ctx, th.shape, th.road or 0xff000000.toInt()), LinearLayout.LayoutParams(Ui.dpi(92f), Ui.dpi(92f)).apply { setMargins(Ui.dpi(4f), 0, 0, 0) })
        col.addView(top)
        col.addView(Ui.text(ctx, th.name, 22f).apply { setPadding(Ui.dpi(8f), Ui.dpi(6f), Ui.dpi(8f), 0) })
        col.addView(Ui.text(ctx, "★".repeat(stars) + "☆".repeat(3 - stars), 26f, Ui.YELLOW))
        col.addView(Ui.text(ctx, "#${index + 1}", 16f, 0xddffffff.toInt()))
        return col
    }

    /** Quick presets for grown-ups who don't want to fiddle. Every option stays editable below. */
    enum class Preset(val label: String, val color: Int) { SMOOTH("Smooth", Ui.GREEN), BALANCED("Balanced", Ui.BLUE), PRETTY("Pretty", Ui.PURPLE), MAX("Max", Ui.PINK) }

    fun applyPreset(s: GraphicsSettings, p: Preset) {
        val lvl = when (p) { Preset.SMOOTH -> Level.LOW; Preset.BALANCED -> Level.MEDIUM; else -> Level.HIGH }
        s.antiAliasing = when (p) { Preset.SMOOTH -> AaMode.FXAA; Preset.MAX -> AaMode.TAA; else -> AaMode.MSAA }
        s.shadows = lvl; s.textureQuality = lvl; s.effects = lvl
        s.ssao = when (p) { Preset.SMOOTH -> Level.OFF; Preset.BALANCED -> Level.LOW; Preset.PRETTY -> Level.MEDIUM; Preset.MAX -> Level.HIGH }
        s.anisotropic = when (p) { Preset.SMOOTH -> Level.LOW; Preset.BALANCED -> Level.MEDIUM; else -> Level.HIGH }
        s.bloom = true
        s.reflections = p == Preset.MAX
        s.motionBlur = p != Preset.SMOOTH
        s.depthOfField = p == Preset.PRETTY || p == Preset.MAX
        s.foliageWind = p != Preset.SMOOTH
        s.fog = true
        s.dynamicResolution = p != Preset.MAX
        s.targetFps = 60
    }

    fun settings(ctx: Context, host: MenuHost): View {
        val s = host.settings
        val root = Ui.frame(ctx)
        root.setBackgroundColor(0xdd1d1b3a.toInt())
        root.addView(header(ctx, "Grown-up settings", host) { s.save(); host.toTitle() }, Ui.lp(-1, -2, Gravity.TOP, 20f, 16f, 20f))
        val list = Ui.col(ctx)
        val refreshers = ArrayList<() -> Unit>()

        fun section(title: String) {
            list.addView(Ui.text(ctx, title, 26f, Ui.YELLOW).apply { gravity = Gravity.START }, lp(w = Ui.dpi(540f), t = 14f, b = 2f))
        }
        fun <T> option(label: String, hint: String, values: List<T>, get: () -> T, set: (T) -> Unit, name: (T) -> String) {
            val row = Ui.row(ctx)
            val texts = Ui.col(ctx).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
            texts.addView(Ui.text(ctx, label, 21f).apply { gravity = Gravity.START })
            texts.addView(Ui.text(ctx, hint, 13f, 0xbbffffff.toInt(), outline = false).apply { gravity = Gravity.START })
            row.addView(texts, LinearLayout.LayoutParams(Ui.dpi(320f), -2))
            lateinit var btn: TextView
            btn = Ui.button(ctx, name(get()), Ui.BLUE, 20f) {
                host.click()
                val n = values[(values.indexOf(get()) + 1) % values.size]; set(n); btn.text = name(n); host.applySettings()
            }
            refreshers.add { btn.text = name(get()) }
            row.addView(btn, LinearLayout.LayoutParams(Ui.dpi(220f), -2).apply { setMargins(0, Ui.dpi(5f), 0, Ui.dpi(5f)) })
            list.addView(row)
        }
        val onOff = listOf(true, false)
        fun b(v: Boolean) = if (v) "On" else "Off"
        val levels = Level.values().toList()

        // Presets
        val presets = Ui.row(ctx)
        Preset.values().forEach { p ->
            presets.addView(Ui.button(ctx, p.label, p.color, 20f) {
                host.click(); applyPreset(s, p); refreshers.forEach { it() }; host.applySettings()
            }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(Ui.dpi(6f), Ui.dpi(4f), Ui.dpi(6f), Ui.dpi(4f)) })
        }
        list.addView(presets)

        section("Edges and detail")
        option("Anti-aliasing", "Smooths jagged edges", AaMode.values().toList(), { s.antiAliasing }, { s.antiAliasing = it }) { it.label }
        option("Texture quality", "Sharpness of surfaces", listOf(Level.LOW, Level.MEDIUM, Level.HIGH), { s.textureQuality }, { s.textureQuality = it }) { it.label }
        option("Anisotropic filtering", "Keeps the road sharp far away", levels, { s.anisotropic }, { s.anisotropic = it }) { when (it) { Level.OFF -> "Off"; Level.LOW -> "2x"; Level.MEDIUM -> "4x"; else -> "16x" } }

        section("Light and shadow")
        option("Shadows", "Soft shadows from the sun", levels, { s.shadows }, { s.shadows = it }) { it.label }
        option("Ambient occlusion (SSAO)", "Soft contact shadows; High adds bent normals and sun cone tracing", levels, { s.ssao }, { s.ssao = it }) { it.label }
        option("Bloom and lens flare", "Glow around bright lights and boosts", onOff, { s.bloom }, { s.bloom = it }, ::b)
        option("Reflections (SSR)", "Shiny reflections on the road", onOff, { s.reflections }, { s.reflections = it }, ::b)
        option("Fog and atmosphere", "Distance haze", onOff, { s.fog }, { s.fog = it }, ::b)

        section("Camera and effects")
        option("Motion blur", "Speed streaks when going fast", onOff, { s.motionBlur }, { s.motionBlur = it }, ::b)
        option("Depth of field", "Blurry background in jump close-ups", onOff, { s.depthOfField }, { s.depthOfField = it }, ::b)
        option("Particle effects", "Sparks, dust, smoke and confetti", levels, { s.effects }, { s.effects = it }) { it.label }
        option("Foliage wind", "Trees and grass sway", onOff, { s.foliageWind }, { s.foliageWind = it }, ::b)

        section("Speed")
        option("Dynamic resolution", "Drops resolution a little to stay smooth", onOff, { s.dynamicResolution }, { s.dynamicResolution = it }, ::b)
        option("Frame rate (V-Sync)", "60 is smoother, 30 saves battery", listOf(60, 30), { s.targetFps }, { s.targetFps = it }) { "$it fps" }
        list.setPadding(0, 0, 0, Ui.dpi(40f))

        val sc = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false }; sc.addView(list)
        root.addView(sc, Ui.lp(-2, -1, Gravity.CENTER_HORIZONTAL, t = 96f, b = 24f))
        root.addView(Ui.text(ctx, "Shadow, texture and anisotropy changes apply on the next race.", 14f, 0xccffffff.toInt()), Ui.lp(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, b = 6f))
        return root
    }

    fun loading(ctx: Context, msg: String): View {
        val root = Ui.frame(ctx)
        root.setBackgroundColor(0xff2a7bff.toInt())
        root.addView(SpeedLinesView(ctx, 0x44ffffff, 30, 1.6f), FrameLayout.LayoutParams(-1, -1))
        val col = Ui.col(ctx)
        val wheel = Ui.text(ctx, "🚗", 90f, outline = false)
        col.addView(wheel); col.addView(Ui.text(ctx, msg, 34f))
        root.addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        fun spin() { wheel.animate().translationX(Ui.dp(60f)).setDuration(500).withEndAction { wheel.animate().translationX(-Ui.dp(60f)).setDuration(500).withEndAction { if (wheel.isAttachedToWindow) spin() }.start() }.start() }
        spin()
        return root
    }
}
