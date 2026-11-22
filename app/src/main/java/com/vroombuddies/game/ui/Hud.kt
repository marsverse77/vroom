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
import com.vroombuddies.game.sim.RacePhase
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
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = min(w, h) / 2 - Ui.dp(4f)
        p.style = Paint.Style.FILL
        p.color = 0x55000000; c.drawCircle(w / 2, h / 2 + Ui.dp(5f), r, p)
        p.shader = RadialGradient(w / 2, h / 2 - r * 0.4f, r * 1.4f, if (held) 0xffffe14d.toInt() else 0xffffffff.toInt(), if (held) 0xffff9a1a.toInt() else 0xffb8d8ff.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(w / 2, h / 2, r, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dp(4f); p.color = Ui.INK; c.drawCircle(w / 2, h / 2, r, p)
        p.style = Paint.Style.FILL; p.color = Ui.INK
        val a = r * 0.5f
        path.reset()
        path.moveTo(w / 2 + dir * a, h / 2); path.lineTo(w / 2 - dir * a * 0.6f, h / 2 - a); path.lineTo(w / 2 - dir * a * 0.6f, h / 2 + a); path.close()
        c.drawPath(path, p)
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
        val w = width.toFloat(); val h = height.toFloat(); val r = min(w, h) / 2 - Ui.dp(10f)
        val ready = energy >= 25f
        val wob = if (ready && !boosting) sin(t * 8f) * Ui.dp(3f) else 0f
        p.style = Paint.Style.FILL
        if (ready) { p.color = 0x66ffb300; c.drawCircle(w / 2, h / 2, r + Ui.dp(8f) + abs(wob) * 2, p) }
        p.color = 0x55000000; c.drawCircle(w / 2, h / 2 + Ui.dp(6f), r, p)
        val key = (if (ready) 1 else 0) + 2 * width + 100000 * height
        if (key != gradKey || grad == null) {
            gradKey = key
            grad = RadialGradient(w / 2, h / 2 - r * 0.4f, r * 1.4f, if (ready) 0xffff7ab8.toInt() else 0xff9e9eb8.toInt(), if (ready) 0xffff2d6f.toInt() else 0xff5e5e78.toInt(), Shader.TileMode.CLAMP)
        }
        p.shader = grad
        c.drawCircle(w / 2, h / 2 + wob, r, p); p.shader = null
        // charge ring
        p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dp(10f); p.strokeCap = Paint.Cap.ROUND
        p.color = 0x55ffffff; c.drawCircle(w / 2, h / 2 + wob, r - Ui.dp(9f), p)
        p.color = if (boosting) 0xffffffff.toInt() else 0xffffe14d.toInt()
        val rr = r - Ui.dp(9f)
        c.drawArc(w / 2 - rr, h / 2 - rr + wob, w / 2 + rr, h / 2 + rr + wob, -90f, 360f * (energy / 100f).coerceIn(0f, 1f), false, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dp(4f); p.color = Ui.INK; c.drawCircle(w / 2, h / 2 + wob, r, p)
        // lightning bolt
        p.style = Paint.Style.FILL; p.color = Color.WHITE
        val s = r * 0.5f
        val bolt = boltPath.apply {
            reset()
            moveTo(w / 2 + s * 0.15f, h / 2 - s + wob); lineTo(w / 2 - s * 0.45f, h / 2 + s * 0.15f + wob); lineTo(w / 2 - s * 0.02f, h / 2 + s * 0.15f + wob)
            lineTo(w / 2 - s * 0.2f, h / 2 + s + wob); lineTo(w / 2 + s * 0.45f, h / 2 - s * 0.2f + wob); lineTo(w / 2 + s * 0.02f, h / 2 - s * 0.2f + wob); close()
        }
        c.drawPath(bolt, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dp(3f); p.color = Ui.INK; c.drawPath(bolt, p)
    }
}

/** Top bar: place badge, lap flags and a race strip with every car as a dot. Also letterbox, flash, countdown. */
class HudOverlay(ctx: Context) : View(ctx) {
    var state: HudState? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font; textAlign = Paint.Align.CENTER }
    private val dotColors = intArrayOf(0xffff5252.toInt(), 0xffffd740.toInt(), 0xff40c4ff.toInt(), 0xff69f0ae.toInt(), 0xffff80ab.toInt(), 0xffb388ff.toInt())
    private var lastCount = -1; private var countPop = 0f
    private var goTimer = 0f; private var lastPhase = RacePhase.COUNTDOWN

    private fun ordinal(n: Int) = n.toString() + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

    private fun outlined(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        txt.textSize = size
        txt.style = Paint.Style.STROKE; txt.strokeWidth = size * 0.16f; txt.strokeJoin = Paint.Join.ROUND; txt.color = Ui.INK
        c.drawText(s, x, y, txt)
        txt.style = Paint.Style.FILL; txt.color = color
        c.drawText(s, x, y, txt)
    }

    fun tick(dt: Float) {
        countPop = max(0f, countPop - dt * 2.5f); goTimer = max(0f, goTimer - dt)
        state?.let { if (it.phase == RacePhase.RACING && lastPhase == RacePhase.COUNTDOWN) goTimer = 0.9f; lastPhase = it.phase }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = state ?: return
        val w = width.toFloat(); val h = height.toFloat()
        // letterbox bars for cinematic jumps
        if (s.letterbox > 0.01f) { p.color = 0xff000000.toInt(); val bh = h * 0.11f * s.letterbox; c.drawRect(0f, 0f, w, bh, p); c.drawRect(0f, h - bh, w, h, p) }
        // flash
        if (s.flash > 0f) { p.color = Color.argb((s.flash * 120).toInt().coerceIn(0, 255), 255, 255, 255); c.drawRect(0f, 0f, w, h, p) }
        if (s.letterbox > 0.5f) return

        // place badge
        val medal = when (s.place) { 1 -> 0xffffc61a.toInt(); 2 -> 0xffd6dde6.toInt(); 3 -> 0xffe0915a.toInt(); else -> 0xff7a8cff.toInt() }
        val bx = Ui.dp(70f); val by = Ui.dp(64f); val br = Ui.dp(50f)
        p.color = 0x55000000; c.drawCircle(bx, by + Ui.dp(5f), br, p)
        p.color = medal; c.drawCircle(bx, by, br, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dp(4f); p.color = Ui.INK; c.drawCircle(bx, by, br, p); p.style = Paint.Style.FILL
        outlined(c, ordinal(s.place), bx, by + Ui.dp(14f), Ui.dp(38f), Color.WHITE)

        // lap flags
        outlined(c, "🏁 ${s.lap}/${s.laps}", w - Ui.dp(90f), Ui.dp(56f), Ui.dp(32f), Color.WHITE)

        // race strip
        val x0 = Ui.dp(150f); val x1 = w - Ui.dp(200f); val y = Ui.dp(42f)
        p.color = 0x88000000.toInt(); c.drawRoundRect(x0 - Ui.dp(10f), y - Ui.dp(10f), x1 + Ui.dp(10f), y + Ui.dp(10f), Ui.dp(10f), Ui.dp(10f), p)
        p.color = 0xffffffff.toInt(); c.drawRect(x1 - Ui.dp(4f), y - Ui.dp(12f), x1 + Ui.dp(4f), y + Ui.dp(12f), p)
        for (i in s.progress.indices) {
            if (i == s.playerIndex) continue
            p.color = dotColors[i % dotColors.size]
            c.drawCircle(x0 + (x1 - x0) * s.progress[i], y, Ui.dp(8f), p)
        }
        val px = x0 + (x1 - x0) * s.progress.getOrElse(s.playerIndex) { 0f }
        p.color = Color.WHITE; c.drawCircle(px, y, Ui.dp(14f), p)
        p.color = 0xffff2d6f.toInt(); c.drawCircle(px, y, Ui.dp(10f), p)

        // speed
        outlined(c, "${s.speedKmh.roundToInt()}", w / 2, h - Ui.dp(40f), Ui.dp(40f), if (s.boosting) 0xffffe14d.toInt() else Color.WHITE)
        outlined(c, "km/h", w / 2, h - Ui.dp(16f), Ui.dp(16f), Color.WHITE)

        // countdown
        if (s.phase == RacePhase.COUNTDOWN) {
            val n = ceil(s.countdown - 0.6f).toInt()
            if (n != lastCount) { lastCount = n; countPop = 1f }
            val label = if (n >= 1) n.toString() else "GO!"
            val col = when (n) { 3 -> 0xffff5252.toInt(); 2 -> 0xffffd740.toInt(); 1 -> 0xffffd740.toInt(); else -> 0xff69f0ae.toInt() }
            outlined(c, label, w / 2, h * 0.45f, Ui.dp(120f) * (1f + countPop * 0.5f), col)
        } else if (goTimer > 0f) {
            outlined(c, "GO!", w / 2, h * 0.45f, Ui.dp(130f) * (1f + (0.9f - goTimer) * 0.6f), 0xff69f0ae.toInt())
        }
        if (s.whoops > 0f) outlined(c, "Whoops! 🔄", w / 2, h * 0.4f, Ui.dp(60f) * (1f + max(0f, s.whoops - 1.3f)), 0xffffe14d.toInt())
    }
}

/** End of race: big bouncing result with stars, and three picture buttons. */
class ResultView(ctx: Context, place: Int, onAgain: () -> Unit, onNext: () -> Unit, onHome: () -> Unit) : FrameLayout(ctx) {
    init {
        setBackgroundColor(0x33000000)
        val win = place == 1
        val col = Ui.col(ctx)
        val trophy = Ui.text(ctx, when (place) { 1 -> "🏆"; 2 -> "🥈"; 3 -> "🥉"; else -> "⭐" }, 96f, outline = false)
        val title = Ui.text(ctx, if (win) "YOU WIN!" else "${place}${if (place == 2) "nd" else if (place == 3) "rd" else "th"} place!", 64f, if (win) Ui.YELLOW else Color.WHITE)
        val sub = Ui.text(ctx, if (win) "Super speedy racing!" else "Great job! Let's try again!", 26f)
        col.addView(trophy); col.addView(title); col.addView(sub)
        val row = Ui.row(ctx)
        row.addView(Ui.button(ctx, "🔁", Ui.BLUE, 40f, onAgain), android.widget.LinearLayout.LayoutParams(-2, -2).apply { setMargins(Ui.dpi(14f), Ui.dpi(24f), Ui.dpi(14f), 0) })
        row.addView(Ui.button(ctx, "▶", Ui.GREEN, 40f, onNext), android.widget.LinearLayout.LayoutParams(-2, -2).apply { setMargins(Ui.dpi(14f), Ui.dpi(24f), Ui.dpi(14f), 0) })
        row.addView(Ui.button(ctx, "🏠", Ui.ORANGE, 40f, onHome), android.widget.LinearLayout.LayoutParams(-2, -2).apply { setMargins(Ui.dpi(14f), Ui.dpi(24f), Ui.dpi(14f), 0) })
        col.addView(row)
        addView(col, LayoutParams(-2, -2, Gravity.CENTER))
        Ui.popIn(trophy, 100); Ui.popIn(title, 250); Ui.popIn(sub, 450); Ui.popIn(row, 650)
        if (win) title.animate().rotationBy(0f).setStartDelay(700).withEndAction { wiggle(title) }.start()
    }
    private fun wiggle(v: TextView) {
        v.animate().rotation(-6f).scaleX(1.08f).scaleY(1.08f).setDuration(300).withEndAction {
            v.animate().rotation(6f).scaleX(1f).scaleY(1f).setDuration(300).withEndAction { if (isAttachedToWindow) wiggle(v) }.start()
        }.start()
    }
}
