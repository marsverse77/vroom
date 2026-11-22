package com.vroombuddies.game.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.vroombuddies.game.content.Theme
import com.vroombuddies.game.render.*
import com.vroombuddies.game.render.Paint

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
        val col = Ui.col(ctx)
        val logoRow = Ui.row(ctx)
        val colors = intArrayOf(Ui.PINK, Ui.ORANGE, Ui.YELLOW, Ui.GREEN, Ui.BLUE, Ui.PURPLE)
        "VROOM".forEachIndexed { i, ch -> logoRow.addView(Ui.text(ctx, ch.toString(), 76f, colors[i % colors.size]).also { Ui.popIn(it, 80L * i); bob(it, i) }) }
        val logo2 = Ui.text(ctx, "BUDDIES!", 52f, Color.WHITE).also { Ui.popIn(it, 500) }
        col.addView(logoRow); col.addView(logo2)
        val play = Ui.button(ctx, "▶  PLAY", Ui.GREEN, 40f) { host.click(); host.toCarPicker() }
        col.addView(play, lp(t = 24f)); Ui.popIn(play, 700); pulse(play)
        root.addView(col, Ui.lp(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START, l = 48f))
        root.addView(Ui.button(ctx, "⚙", Ui.PURPLE, 26f) { host.click(); host.toSettings() }, Ui.lp(-2, -2, Gravity.TOP or Gravity.END, t = 20f, r = 20f))
        return root
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

    fun carPicker(ctx: Context, host: MenuHost, current: Int): View {
        val root = Ui.frame(ctx)
        root.addView(header(ctx, "Pick your car!", host) { host.toTitle() }, Ui.lp(-1, -2, Gravity.TOP, 20f, 16f, 20f))
        val designs = CarDesigns.all
        var idx = current
        val name = Ui.text(ctx, designs[idx].name, 48f, Ui.YELLOW)
        val tag = Ui.text(ctx, designs[idx].tagline, 22f)
        fun show() { name.text = designs[idx].name; tag.text = designs[idx].tagline; Ui.popIn(name); host.pickCar(idx) }
        root.addView(Ui.button(ctx, "◀", Ui.BLUE, 44f) { host.click(); idx = (idx - 1 + designs.size) % designs.size; show() }, Ui.lp(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL, l = 28f))
        root.addView(Ui.button(ctx, "▶", Ui.BLUE, 44f) { host.click(); idx = (idx + 1) % designs.size; show() }, Ui.lp(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL, r = 28f))
        val bottom = Ui.col(ctx)
        bottom.addView(name); bottom.addView(tag)
        bottom.addView(Ui.button(ctx, "✔  This one!", Ui.GREEN, 30f) { host.click(); host.toPaintPicker() }, lp(t = 10f))
        root.addView(bottom, Ui.lp(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, b = 18f))
        return root
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
        col.addView(Ui.text(ctx, th.emoji, 72f, outline = false))
        col.addView(Ui.text(ctx, th.name, 22f).apply { setPadding(Ui.dpi(8f), Ui.dpi(6f), Ui.dpi(8f), 0) })
        col.addView(Ui.text(ctx, "★".repeat(stars) + "☆".repeat(3 - stars), 26f, Ui.YELLOW))
        col.addView(Ui.text(ctx, "#${index + 1}", 16f, 0xddffffff.toInt()))
        return col
    }

    fun settings(ctx: Context, host: MenuHost): View {
        val s = host.settings
        val root = Ui.frame(ctx)
        root.setBackgroundColor(0xcc1d1b3a.toInt())
        root.addView(header(ctx, "Grown-up settings", host) { s.save(); host.toTitle() }, Ui.lp(-1, -2, Gravity.TOP, 20f, 16f, 20f))
        val list = Ui.col(ctx)
        fun <T> option(label: String, values: List<T>, get: () -> T, set: (T) -> Unit, name: (T) -> String) {
            val row = Ui.row(ctx)
            row.addView(Ui.text(ctx, label, 22f).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(Ui.dpi(300f), -2))
            lateinit var btn: TextView
            btn = Ui.button(ctx, name(get()), Ui.BLUE, 20f) {
                host.click()
                val n = values[(values.indexOf(get()) + 1) % values.size]; set(n); btn.text = name(n); host.applySettings()
            }
            row.addView(btn, LinearLayout.LayoutParams(Ui.dpi(220f), -2).apply { setMargins(0, Ui.dpi(5f), 0, Ui.dpi(5f)) })
            list.addView(row)
        }
        val onOff = listOf(true, false)
        fun b(v: Boolean) = if (v) "On" else "Off"
        option("Anti-aliasing", AaMode.values().toList(), { s.antiAliasing }, { s.antiAliasing = it }) { it.label }
        option("Shadows", Level.values().toList(), { s.shadows }, { s.shadows = it }) { it.label }
        option("SSAO", Level.values().toList(), { s.ssao }, { s.ssao = it }) { it.label }
        option("Texture quality", listOf(Level.LOW, Level.MEDIUM, Level.HIGH), { s.textureQuality }, { s.textureQuality = it }) { it.label }
        option("Anisotropic filtering", Level.values().toList(), { s.anisotropic }, { s.anisotropic = it }) { if (it == Level.OFF) "Off" else "${s.anisotropy.toInt()}x".let { _ -> when (it) { Level.LOW -> "2x"; Level.MEDIUM -> "4x"; else -> "16x" } } }
        option("Bloom", onOff, { s.bloom }, { s.bloom = it }, ::b)
        option("Reflections (SSR)", onOff, { s.reflections }, { s.reflections = it }, ::b)
        option("Motion blur (speed lines)", onOff, { s.motionBlur }, { s.motionBlur = it }, ::b)
        option("Depth of field", onOff, { s.depthOfField }, { s.depthOfField = it }, ::b)
        option("Effects quality", Level.values().toList(), { s.effects }, { s.effects = it }) { it.label }
        option("Foliage wind", onOff, { s.foliageWind }, { s.foliageWind = it }, ::b)
        option("Fog", onOff, { s.fog }, { s.fog = it }, ::b)
        option("Dynamic resolution", onOff, { s.dynamicResolution }, { s.dynamicResolution = it }, ::b)
        val audio = com.vroombuddies.game.audio.AudioSettings.of(ctx); val vols = com.vroombuddies.game.audio.Volume.values().toList()
        option("Music", vols, { audio.music }, { audio.music = it }) { it.label }
        option("Sound effects", vols, { audio.effects }, { audio.effects = it }) { it.label }
        option("Frame rate (V-Sync)", listOf(60, 30), { s.targetFps }, { s.targetFps = it }) { "$it fps" }
        val sc = ScrollView(ctx); sc.addView(list)
        root.addView(sc, Ui.lp(-2, -1, Gravity.CENTER_HORIZONTAL, t = 100f, b = 10f))
        root.addView(Ui.text(ctx, "Shadow, texture and anisotropy changes apply on the next race.", 14f, 0xccffffff.toInt()), Ui.lp(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, b = 6f))
        return root
    }

    fun loading(ctx: Context, msg: String): View {
        val root = Ui.frame(ctx)
        root.setBackgroundColor(0xff2a7bff.toInt())
        val col = Ui.col(ctx)
        val wheel = Ui.text(ctx, "🚗", 90f, outline = false)
        col.addView(wheel); col.addView(Ui.text(ctx, msg, 34f))
        root.addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        fun spin() { wheel.animate().translationX(Ui.dp(60f)).setDuration(500).withEndAction { wheel.animate().translationX(-Ui.dp(60f)).setDuration(500).withEndAction { if (wheel.isAttachedToWindow) spin() }.start() }.start() }
        spin()
        return root
    }
}
