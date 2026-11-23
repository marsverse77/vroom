package com.vroombuddies.game.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.vroombuddies.game.render.HudState
import com.vroombuddies.game.sim.Frame
import com.vroombuddies.game.sim.RacePhase
import com.vroombuddies.game.sim.TrackPath
import kotlin.math.*

/** Round arrow pad. Held = steering that way. */
@SuppressLint("ClickableViewAccessibility")
class SteerButton(ctx: Context, private val dir: Int) : View(ctx) {
    var held = false; private set
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    init {
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> { held = true; animate().scaleX(0.9f).scaleY(0.9f).setDuration(60).start() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> { held = false; animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(OvershootInterpolator(3f)).start() }
            }
            invalidate(); true
        }
    }
    private val rf = RectF()
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val d = min(w, h) - Ui.dp(6f)
        val lip = if (held) Ui.dp(2f) else Ui.dp(5f)
        val top = (h - d) / 2 + (if (held) Ui.dp(6f) else 0f)
        rf.set((w - d) / 2, top, (w + d) / 2, top + d - (if (held) Ui.dp(6f) else 0f))
        Ui.drawChunky(c, rf, d / 2, if (held) Ui.ACCENT else 0xd92c313b.toInt(), lip = lip, outline = Ui.dp(1.5f), p = p)
        // arrow: white with an ink outline
        val cx = w / 2; val cy = rf.top + (rf.height() - lip) / 2; val a = d * 0.22f
        path.reset()
        path.moveTo(cx + dir * a * 1.05f, cy); path.lineTo(cx - dir * a * 0.65f, cy - a); path.lineTo(cx - dir * a * 0.65f, cy + a); path.close()
        p.shader = null; p.pathEffect = CornerPathEffect(Ui.dp(5f))
        p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dp(3f); p.strokeJoin = Paint.Join.ROUND; p.color = Ui.INK; c.drawPath(path, p)
        p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawPath(path, p)
        p.pathEffect = null
    }
}

/** Big boost button with a charge ring. Glows and wobbles when ready. */
@SuppressLint("ClickableViewAccessibility")
class BoostButton(ctx: Context) : View(ctx) {
    var energy = 0f
    var boosting = false
    var tapped = false
    private var grad: Shader? = null
    private var gradKey = -1
    private val boltPath = Path()
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font; textAlign = Paint.Align.CENTER; color = Color.WHITE }
    init {
        setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) { tapped = true; animate().scaleX(0.88f).scaleY(0.88f).setDuration(60).withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(3f)).start() }.start() }
            true
        }
    }
    fun tick(dt: Float) { t += dt; invalidate() }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = min(w, h) / 2 - Ui.dp(12f)
        val ready = energy >= 25f
        val wob = if (ready && !boosting) sin(t * 8f) * Ui.dp(3f) else 0f
        val cx = w / 2; val cy = h / 2 - Ui.dp(3f) + wob; val lip = Ui.dp(8f)
        p.style = Paint.Style.FILL; p.shader = null
        if (ready) { p.color = if (boosting) 0x88ffffff.toInt() else 0x66ffb300; c.drawCircle(cx, cy + lip / 2, r + Ui.dp(9f) + abs(wob) * 2, p) }
        // ink outline + lip + body
        p.color = Ui.INK; c.drawCircle(cx, cy, r + Ui.dp(2f), p)
        p.color = 0x66000000; c.drawCircle(cx, cy + lip, r, p)
        val key = (if (ready) 1 else 0) + 2 * width + 100000 * height
        if (key != gradKey || grad == null) {
            gradKey = key
            grad = RadialGradient(w / 2, h / 2 - r * 0.5f, r * 1.5f, if (ready) 0xffff5a5f.toInt() else 0xff4a505c.toInt(), if (ready) 0xffb3121a.toInt() else 0xff23262d.toInt(), Shader.TileMode.CLAMP)
        }
        p.color = Color.BLACK; p.shader = grad
        c.drawCircle(cx, cy, r, p); p.shader = null
        // charge ring
        val rr = r - Ui.dp(11f)
        p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dp(12f); p.strokeCap = Paint.Cap.ROUND
        p.color = 0x44000000; c.drawCircle(cx, cy, rr, p)
        p.color = if (boosting) 0xffffffff.toInt() else 0xffffe14d.toInt()
        c.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, -90f, 360f * (energy / 100f).coerceIn(0f, 1f), false, p)
        // gloss
        p.style = Paint.Style.FILL; p.color = 0x55ffffff
        c.drawOval(cx - r * 0.55f, cy - r * 0.85f, cx + r * 0.55f, cy - r * 0.35f, p)
        // lightning bolt
        val s = r * 0.52f
        val bolt = boltPath.apply {
            reset()
            moveTo(cx + s * 0.15f, cy - s); lineTo(cx - s * 0.45f, cy + s * 0.15f); lineTo(cx - s * 0.02f, cy + s * 0.15f)
            lineTo(cx - s * 0.2f, cy + s); lineTo(cx + s * 0.45f, cy - s * 0.2f); lineTo(cx + s * 0.02f, cy - s * 0.2f); close()
        }
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dp(3f); p.color = Ui.INK; c.drawPath(bolt, p)
        p.style = Paint.Style.FILL; p.color = if (ready) 0xffffe14d.toInt() else Color.WHITE; c.drawPath(bolt, p)
    }
}

/** Top bar: place badge, lap flags and a race strip with every car as a dot. Also letterbox, flash, countdown. */
class HudOverlay(ctx: Context) : View(ctx) {
    var state: HudState? = null
    /** Set by the activity so the mini-map can draw the loop. */
    var track: TrackPath? = null
    private var map: TrackMaps.Map? = null
    private val mapPath = Path(); private val frame = Frame(); private val pt = FloatArray(2)
    private var edgeGlow: Shader? = null; private var edgeKey = 0
    private var boostT = 0f; private var t = 0f
    private var lastLap = 1; private var lapBanner = 0f; private var bannerText = ""
    private var wasFull = false; private var fullPop = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font; textAlign = Paint.Align.CENTER }
    private val dotColors = intArrayOf(0xffff5252.toInt(), 0xffffd740.toInt(), 0xff40c4ff.toInt(), 0xff69f0ae.toInt(), 0xffff80ab.toInt(), 0xffb388ff.toInt())
    private var lastCount = -1; private var countPop = 0f
    private var goTimer = 0f; private var lastPhase = RacePhase.COUNTDOWN

    private fun ordinal(n: Int) = n.toString() + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

    private fun outlined(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        txt.textSize = size
        txt.style = Paint.Style.STROKE; txt.strokeWidth = (size * 0.07f).coerceIn(Ui.dp(1.2f), Ui.dp(5f)); txt.strokeJoin = Paint.Join.ROUND; txt.color = Ui.INK
        txt.setShadowLayer(Ui.dp(4f), 0f, Ui.dp(2f), 0xaa000000.toInt())
        c.drawText(s, x, y, txt)
        txt.clearShadowLayer()
        txt.style = Paint.Style.FILL; txt.color = color
        c.drawText(s, x, y, txt)
    }

    fun tick(dt: Float) {
        t += dt
        state?.let { st ->
            boostT = if (st.boosting) min(1f, boostT + dt * 5f) else max(0f, boostT - dt * 2.5f)
            if (st.lap != lastLap && st.phase == RacePhase.RACING) {
                lastLap = st.lap; lapBanner = 1.8f
                bannerText = if (st.lap >= st.laps) "LAST LAP!" else "LAP ${st.lap}!"
            }
            val full = st.energy >= 99.5f
            if (full && !wasFull) fullPop = 1f
            wasFull = full
        }
        lapBanner = max(0f, lapBanner - dt); fullPop = max(0f, fullPop - dt * 2f)
        countPop = max(0f, countPop - dt * 2.5f); goTimer = max(0f, goTimer - dt)
        state?.let { if (it.phase == RacePhase.RACING && lastPhase == RacePhase.COUNTDOWN) goTimer = 0.9f; lastPhase = it.phase }
        invalidate()
    }

    private val rf = RectF()

    private fun drawFlag(c: Canvas, cx: Float, cy: Float, size: Float) {
        val sq = size / 4
        p.color = Ui.INK; c.drawRect(cx - size / 2 - Ui.dp(2f), cy - size / 2 - Ui.dp(2f), cx + size / 2 + Ui.dp(2f), cy + size * 0.25f + Ui.dp(2f), p)
        for (r in 0 until 3) for (k in 0 until 4) { p.color = if ((r + k) % 2 == 0) Color.WHITE else Ui.INK; c.drawRect(cx - size / 2 + k * sq, cy - size / 2 + r * sq, cx - size / 2 + (k + 1) * sq, cy - size / 2 + (r + 1) * sq, p) }
        p.color = Ui.INK; c.drawRect(cx - size / 2 - Ui.dp(2f), cy - size / 2, cx - size / 2 + Ui.dp(2f), cy + size * 0.7f, p)
    }

    /** Speed dial: an arc that fills green to yellow to red, with the number leaning forward. */
    private fun drawSpeedo(c: Canvas, s: HudState, w: Float, h: Float) {
        val r = Ui.dp(46f); val cx = w / 2; val cy = h - Ui.dp(22f)
        rf.set(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = Ui.dp(16f); p.color = Ui.INK; c.drawArc(rf, 180f, 180f, false, p)
        p.strokeWidth = Ui.dp(9f); p.color = 0x66ffffff; c.drawArc(rf, 182f, 176f, false, p)
        val f = (s.speedKmh / 300f).coerceIn(0f, 1f)
        if (f > 0.01f) {
            p.color = when { s.boosting -> Ui.GOLD; f < 0.8f -> Color.WHITE; else -> Ui.ACCENT }
            c.drawArc(rf, 182f, 176f * f, false, p)
        }
        p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.BUTT
        txt.textSkewX = Ui.SLANT
        outlined(c, "${s.speedKmh.roundToInt()}", cx, cy - Ui.dp(4f), Ui.dp(34f), if (s.boosting) 0xffffe14d.toInt() else Color.WHITE)
        txt.textSkewX = 0f
        outlined(c, "km/h", cx, cy + Ui.dp(14f), Ui.dp(13f), Color.WHITE)
    }

    /** Hot orange/blue glow around the screen edges while boosting, plus a few streaks. */
    private fun drawBoostGlow(c: Canvas, w: Float, h: Float) {
        if (boostT <= 0.01f) return
        p.style = Paint.Style.FILL
        val key = width * 31 + height
        if (edgeGlow == null || edgeKey != key) {
            edgeKey = key
            edgeGlow = RadialGradient(w / 2, h / 2, hypot(w, h) * 0.55f, intArrayOf(0x00ff9a1a, 0x00ff9a1a, 0x88ff5a1a.toInt(), 0xddff2d6f.toInt()), floatArrayOf(0f, 0.55f, 0.85f, 1f), Shader.TileMode.CLAMP)
        }
        p.shader = edgeGlow; p.alpha = (boostT * (200 + 55 * sin(t * 25f))).toInt().coerceIn(0, 255)
        c.drawRect(0f, 0f, w, h, p); p.shader = null; p.alpha = 255
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 14) {
            val a = i * 2.39996f
            val ph = ((t * 2.2f + i * 0.37f) % 1f)
            val r0 = hypot(w, h) * (0.32f + ph * 0.4f); val r1 = r0 + Ui.dp(90f) * (0.4f + ph)
            p.strokeWidth = Ui.dp(3f + (i % 3) * 2f)
            p.color = Color.argb((boostT * 170 * (1f - ph)).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawLine(w / 2 + cos(a) * r0, h / 2 + sin(a) * r0 * 0.6f, w / 2 + cos(a) * r1, h / 2 + sin(a) * r1 * 0.6f, p)
        }
    }

    /** Small map of the loop, top right, with every racer as a coloured dot. */
    private fun drawMiniMap(c: Canvas, s: HudState, w: Float) {
        val tr = track ?: return
        val m = map ?: TrackMaps.of(tr).also { map = it }
        val size = Ui.dp(84f); val l = Ui.dp(28f); val tp = Ui.dp(128f) // under the place badge, above the steering pads
        rf.set(l - Ui.dp(8f), tp - Ui.dp(8f), l + size + Ui.dp(8f), tp + size + Ui.dp(12f))
        Ui.drawChunky(c, rf, Ui.dp(8f), 0xc015171c.toInt(), lip = Ui.dp(3f), outline = Ui.dp(1.5f), gloss = false, p = p)
        TrackMaps.drawOutline(c, m, l, tp, size, p, mapPath, 0xffeeeeff.toInt(), Ui.INK, Ui.dp(4f))
        p.style = Paint.Style.FILL
        val lapLen = s.laps * tr.length
        for (i in s.progress.indices) {
            if (i == s.playerIndex) continue
            m.at(tr, s.progress[i] * lapLen, frame, pt)
            p.color = dotColors[i % dotColors.size]; c.drawCircle(l + pt[0] * size, tp + pt[1] * size, Ui.dp(5f), p)
        }
        m.at(tr, s.progress.getOrElse(s.playerIndex) { 0f } * lapLen, frame, pt)
        val pulse = 1f + 0.2f * sin(t * 8f)
        p.color = Color.WHITE; c.drawCircle(l + pt[0] * size, tp + pt[1] * size, Ui.dp(8f) * pulse, p)
        p.color = 0xffff2d6f.toInt(); c.drawCircle(l + pt[0] * size, tp + pt[1] * size, Ui.dp(5.5f) * pulse, p)
    }

    /** Four chunky cells above the boost button. Each full cell is one boost. */
    private fun drawEnergyBar(c: Canvas, s: HudState, w: Float, h: Float) {
        val cells = 4; val cw = Ui.dp(34f); val gap = Ui.dp(6f); val ch = Ui.dp(24f)
        val total = cells * cw + (cells - 1) * gap
        val cx = w - Ui.dp(18f) - Ui.dp(Ui.BOOST_SIZE / 2); val x0 = cx - total / 2; val y0 = h - Ui.dp(14f) - Ui.dp(Ui.BOOST_SIZE) - ch - Ui.dp(12f)
        rf.set(x0 - Ui.dp(10f), y0 - Ui.dp(9f), x0 + total + Ui.dp(10f), y0 + ch + Ui.dp(13f))
        Ui.drawChunky(c, rf, Ui.dp(6f), 0xd915171c.toInt(), lip = Ui.dp(3f), outline = Ui.dp(1.5f), gloss = false, p = p, slant = Ui.dp(8f))
        val e = s.energy.coerceIn(0f, 100f) / 25f
        for (i in 0 until cells) {
            val x = x0 + i * (cw + gap)
            val f = (e - i).coerceIn(0f, 1f)
            p.color = 0x33000000; c.drawRoundRect(x, y0, x + cw, y0 + ch, Ui.dp(8f), Ui.dp(8f), p)
            if (f > 0f) {
                p.color = if (f >= 1f) (if (s.boosting) Color.WHITE else 0xffffe14d.toInt()) else 0xffff9a1a.toInt()
                c.drawRoundRect(x, y0, x + cw * f, y0 + ch, Ui.dp(8f), Ui.dp(8f), p)
                p.color = 0x66ffffff; c.drawRoundRect(x + Ui.dp(4f), y0 + Ui.dp(3f), x + max(Ui.dp(8f), cw * f - Ui.dp(4f)), y0 + Ui.dp(8f), Ui.dp(3f), Ui.dp(3f), p)
            }
        }
        if (s.energy >= 99.5f && !s.boosting) {
            val k = 1f + fullPop * 0.6f + 0.06f * sin(t * 10f)
            outlined(c, "FULL!", cx, y0 - Ui.dp(10f), Ui.dp(26f) * k, Ui.YELLOW)
        }
    }

    override fun onDraw(c: Canvas) {
        val s = state ?: return
        val w = width.toFloat(); val h = height.toFloat()
        drawBoostGlow(c, w, h)
        // letterbox bars for cinematic jumps
        if (s.letterbox > 0.01f) { p.color = 0xff000000.toInt(); val bh = h * 0.11f * s.letterbox; c.drawRect(0f, 0f, w, bh, p); c.drawRect(0f, h - bh, w, h, p) }
        // flash
        if (s.flash > 0f) { p.color = Color.argb((s.flash * 120).toInt().coerceIn(0, 255), 255, 255, 255); c.drawRect(0f, 0f, w, h, p) }
        if (s.letterbox > 0.5f) return

        // place badge: chunky medal, big number with a small suffix
        val medal = when (s.place) { 1 -> 0xffffc61a.toInt(); 2 -> 0xffd6dde6.toInt(); 3 -> 0xffe0915a.toInt(); else -> Ui.STEEL }
        val bx = Ui.dp(62f); val by = Ui.dp(58f); val br = Ui.dp(46f)
        rf.set(bx - br, by - br, bx + br, by + br + Ui.dp(6f))
        Ui.drawChunky(c, rf, br, medal, lip = Ui.dp(4f), outline = Ui.dp(1.5f), p = p)
        val num = s.place.toString(); val suf = ordinal(s.place).removePrefix(num)
        txt.textSkewX = Ui.SLANT
        outlined(c, num, bx - Ui.dp(9f), by + Ui.dp(17f), Ui.dp(50f), Color.WHITE)
        outlined(c, suf, bx + Ui.dp(22f), by - Ui.dp(2f), Ui.dp(20f), Color.WHITE)
        txt.textSkewX = 0f

        // lap pill
        val lw = Ui.dp(118f); val lx = w - lw - Ui.dp(16f); val ly = Ui.dp(14f)
        rf.set(lx, ly, lx + lw, ly + Ui.dp(50f))
        Ui.drawChunky(c, rf, Ui.dp(6f), 0xd915171c.toInt(), lip = Ui.dp(3f), outline = Ui.dp(1.5f), gloss = false, p = p, slant = Ui.dp(10f))
        drawFlag(c, lx + Ui.dp(24f), ly + Ui.dp(22f), Ui.dp(22f))
        outlined(c, "${s.lap}/${s.laps}", lx + lw * 0.62f, ly + Ui.dp(33f), Ui.dp(27f), Color.WHITE)

        drawMiniMap(c, s, w)
        drawEnergyBar(c, s, w, h)

        // race strip: a chunky road with a chequered finish and every racer as a dot
        val x0 = Ui.dp(136f); val x1 = w - Ui.dp(156f); val y = Ui.dp(38f)
        rf.set(x0 - Ui.dp(14f), y - Ui.dp(14f), x1 + Ui.dp(28f), y + Ui.dp(17f))
        Ui.drawChunky(c, rf, Ui.dp(6f), 0xd915171c.toInt(), lip = Ui.dp(3f), gloss = false, p = p, slant = Ui.dp(10f))
        // dashed centre line
        p.color = 0x55ffffff
        var dx = x0; while (dx < x1) { c.drawRoundRect(dx, y - Ui.dp(1.5f), dx + Ui.dp(10f), y + Ui.dp(1.5f), Ui.dp(1.5f), Ui.dp(1.5f), p); dx += Ui.dp(22f) }
        // finish chequer
        val fs = Ui.dp(6f); val fx = x1 + Ui.dp(4f)
        for (row in 0 until 4) for (col in 0 until 3) { p.color = if ((row + col) % 2 == 0) Color.WHITE else Ui.INK; c.drawRect(fx + col * fs, y - Ui.dp(11f) + row * fs, fx + (col + 1) * fs, y - Ui.dp(11f) + (row + 1) * fs, p) }
        for (i in s.progress.indices) {
            if (i == s.playerIndex) continue
            val xx = x0 + (x1 - x0) * s.progress[i]
            p.color = Ui.INK; c.drawCircle(xx, y, Ui.dp(9f), p)
            p.color = dotColors[i % dotColors.size]; c.drawCircle(xx, y, Ui.dp(6.5f), p)
        }
        val px = x0 + (x1 - x0) * s.progress.getOrElse(s.playerIndex) { 0f }
        val pr = Ui.dp(13f) * (1f + 0.08f * sin(t * 8f))
        p.color = Ui.INK; c.drawCircle(px, y, pr + Ui.dp(3f), p)
        p.color = Color.WHITE; c.drawCircle(px, y, pr, p)
        p.color = 0xffff2d6f.toInt(); c.drawCircle(px, y, pr - Ui.dp(4f), p)

        drawSpeedo(c, s, w, h)

        // countdown
        if (s.phase == RacePhase.COUNTDOWN) {
            val n = ceil(s.countdown - 0.6f).toInt()
            if (n != lastCount) { lastCount = n; countPop = 1f }
            val label = if (n >= 1) n.toString() else "GO!"
            val col = when (n) { 3 -> 0xffff5252.toInt(); 2 -> 0xffffd740.toInt(); 1 -> 0xffffd740.toInt(); else -> 0xff69f0ae.toInt() }
            txt.textSkewX = Ui.SLANT
            outlined(c, label, w / 2, h * 0.5f, Ui.dp(130f) * (1f + countPop * 0.5f), col)
            txt.textSkewX = 0f
        } else if (goTimer > 0f) {
            txt.textSkewX = Ui.SLANT
            outlined(c, "GO!", w / 2, h * 0.5f, Ui.dp(130f) * (1f + (0.9f - goTimer) * 0.6f), 0xff69f0ae.toInt())
            txt.textSkewX = 0f
        }
        if (lapBanner > 0f) {
            // slides in fast from the right, holds, then zooms off to the left
            val k = 1.8f - lapBanner
            val x = when { k < 0.25f -> w * (1.5f - k / 0.25f); lapBanner < 0.3f -> w * 0.5f - (0.3f - lapBanner) / 0.3f * w; else -> w * 0.5f }
            val big = bannerText.startsWith("LAST")
            outlined(c, bannerText, x, h * 0.3f, Ui.dp(if (big) 72f else 58f), if (big) 0xffff5252.toInt() else Ui.YELLOW)
        }
        if (s.whoops > 0f) outlined(c, "Whoops! 🔄", w / 2, h * 0.4f, Ui.dp(60f) * (1f + max(0f, s.whoops - 1.3f)), 0xffffe14d.toInt())
    }
}

/**
 * End of race. A win gets soft light rays, gold and red confetti, letters that drop in and stars that
 * stamp in one by one. Other places still get a happy screen, because every race should feel good.
 */
class ResultView(
    ctx: Context, place: Int, stars: Int, private val sfx: (String) -> Unit,
    onAgain: () -> Unit, onNext: () -> Unit, onHome: () -> Unit,
) : FrameLayout(ctx) {
    init {
        val win = place == 1
        setBackgroundColor(if (win) 0x22000000 else 0x55000000)
        if (place <= 3) addView(SunburstView(ctx, if (win) 0x30ffd27a else 0x22ffffff, if (win) 0x10ffffff else 0x0affffff), LayoutParams(-1, -1).apply { gravity = Gravity.CENTER })
        val confetti = if (place <= 3) ConfettiView(ctx, if (win) 170 else 70) else null
        val col = Ui.col(ctx)
        val trophy = Ui.text(ctx, when (place) { 1 -> "🏆"; 2 -> "🥈"; 3 -> "🥉"; else -> "⭐" }, 64f, outline = false)
        col.addView(trophy)
        val titleText = if (win) "YOU WIN!" else "${place}${if (place == 2) "ND" else if (place == 3) "RD" else "TH"} PLACE"
        val titleRow = Ui.row(ctx)
        val letters = titleText.mapIndexed { i, ch ->
            Ui.text(ctx, ch.toString(), 58f, if (win && i >= 4) Ui.GOLD else Color.WHITE, slant = true).also { titleRow.addView(it, android.widget.LinearLayout.LayoutParams(-2, -2).apply { rightMargin = when { i == titleText.length - 1 -> 0; ch == ' ' -> Ui.dpi(6f); else -> -Ui.dpi(10f) } }) }
        }
        titleRow.setPadding(Ui.dpi(6f), 0, Ui.dpi(34f), 0)
        col.addView(titleRow)
        val starRow = Ui.row(ctx)
        val starViews = (0 until 3).map { i ->
            val sz = if (i == 1) 64f else 52f
            Menus.StarsView(ctx, if (i < stars) 1 else 0, 1).also { it.alpha = 0f; starRow.addView(it, android.widget.LinearLayout.LayoutParams(Ui.dpi(sz), Ui.dpi(sz)).apply { setMargins(Ui.dpi(4f), if (i == 1) 0 else Ui.dpi(12f), Ui.dpi(4f), 0) }) }
        }
        col.addView(starRow)
        val sub = Menus.banner(ctx, if (win) "Champion!" else "Great race!", Ui.ACCENT, 22f)
        col.addView(sub)
        val row = Ui.row(ctx)
        fun lpb(sz: Float) = android.widget.LinearLayout.LayoutParams(Ui.dpi(sz), Ui.dpi(sz)).apply { setMargins(Ui.dpi(14f), 0, Ui.dpi(14f), 0) }
        row.addView(Ui.roundButton(ctx, "🔁", Ui.BLUE, 30f, onAgain), lpb(80f))
        val next = Ui.roundButton(ctx, "▶", Ui.GREEN, 38f, onNext)
        row.addView(next, lpb(96f))
        row.addView(Ui.roundButton(ctx, "🏠", Ui.ORANGE, 30f, onHome), lpb(80f))
        row.setPadding(0, Ui.dpi(8f), 0, 0)
        col.addView(row)
        addView(col, LayoutParams(-2, -2, Gravity.CENTER))
        confetti?.let { addView(it, LayoutParams(-1, -1)) }

        // Timeline
        trophy.scaleX = 0f; trophy.scaleY = 0f; trophy.rotation = -180f
        trophy.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(80).setDuration(650).setInterpolator(OvershootInterpolator(2.2f)).start()
        letters.forEachIndexed { i, v ->
            v.translationY = -Ui.dp(300f); v.alpha = 0f
            v.animate().translationY(0f).alpha(1f).setStartDelay(250L + i * 55L).setDuration(420).setInterpolator(OvershootInterpolator(3f)).start()
        }
        val starStart = 450L + letters.size * 55L
        starViews.forEachIndexed { i, v ->
            v.scaleX = 3f; v.scaleY = 3f
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(starStart + i * 320L).setDuration(300).setInterpolator(OvershootInterpolator(3f))
                .withStartAction { if (i < stars) { sfx("pop"); confetti?.burst(v.let { sv -> (starRow.left + col.left + sv.left + sv.width / 2f) / max(1, width) }, (col.top + starRow.top + v.height / 2f) / max(1, height), 30) } }.start()
        }
        val after = starStart + 3 * 320L
        Ui.popIn(sub, after); Ui.popIn(row, after + 150)
        if (win) {
            postDelayed({ confetti?.burst(0.15f, 0.9f, 80); confetti?.burst(0.85f, 0.9f, 80); sfx("firework") }, 200)
            postDelayed({ letters.forEachIndexed { i, v -> wave(v, i) } }, after)
            postDelayed({ pulse(next) }, after + 600)
        }
        postDelayed({ trophyBob(trophy) }, 900)
    }

    private fun wave(v: View, i: Int) {
        v.animate().translationY(-Ui.dp(18f)).setStartDelay(i * 70L).setDuration(260).withEndAction {
            v.animate().translationY(0f).setStartDelay(0).setDuration(260).withEndAction { if (isAttachedToWindow) postDelayed({ wave(v, 0) }, 900) }.start()
        }.start()
    }
    private fun trophyBob(v: View) {
        v.animate().rotation(-8f).scaleX(1.08f).scaleY(1.08f).setDuration(420).withEndAction {
            v.animate().rotation(8f).scaleX(1f).scaleY(1f).setDuration(420).withEndAction { if (isAttachedToWindow) trophyBob(v) }.start()
        }.start()
    }
    private fun pulse(v: View) {
        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(380).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(380).withEndAction { if (isAttachedToWindow) pulse(v) }.start()
        }.start()
    }
}
