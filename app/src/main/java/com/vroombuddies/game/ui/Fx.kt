package com.vroombuddies.game.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.vroombuddies.game.sim.Frame
import com.vroombuddies.game.sim.TrackPath
import com.vroombuddies.game.sim.TrackShape
import kotlin.math.*
import kotlin.random.Random

/** Views that animate themselves every frame while attached (postInvalidateOnAnimation). */
abstract class AnimatedView(ctx: Context) : View(ctx) {
    private var last = 0L
    protected var time = 0f
    abstract fun draw(c: Canvas, dt: Float)
    final override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
        last = now; time += dt
        draw(c, dt)
        if (isAttachedToWindow && visibility == VISIBLE) postInvalidateOnAnimation()
    }
}

/** Streaks rushing past behind the menus so even standing still feels fast. */
class SpeedLinesView(ctx: Context, private val color: Int = 0x55ffffff, private val count: Int = 26, private val speed: Float = 1f) : AnimatedView(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val rnd = Random(7)
    private val ys = FloatArray(count) { rnd.nextFloat() }
    private val xs = FloatArray(count) { rnd.nextFloat() * 1.6f - 0.3f }
    private val lens = FloatArray(count) { 0.08f + rnd.nextFloat() * 0.22f }
    private val vs = FloatArray(count) { 0.6f + rnd.nextFloat() * 1.4f }
    private val ws = FloatArray(count) { 2f + rnd.nextFloat() * 5f }
    init { isClickable = false; isFocusable = false }
    override fun draw(c: Canvas, dt: Float) {
        val w = width.toFloat(); val h = height.toFloat()
        p.color = color
        for (i in 0 until count) {
            xs[i] -= vs[i] * dt * 1.4f * speed
            if (xs[i] + lens[i] < -0.05f) { xs[i] = 1.05f + rnd.nextFloat() * 0.3f; ys[i] = rnd.nextFloat() }
            p.strokeWidth = Ui.dp(ws[i])
            val x = xs[i] * w; val y = ys[i] * h
            c.drawLine(x, y, x + lens[i] * w, y, p)
        }
    }
}

/** Rotating cartoon sun rays behind a trophy. */
class SunburstView(ctx: Context, private val c1: Int, private val c2: Int, private val rays: Int = 16) : AnimatedView(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var glow: Shader? = null; private var gw = 0; private var gh = 0
    override fun draw(c: Canvas, dt: Float) {
        val cx = width / 2f; val cy = height / 2f; val r = hypot(cx, cy) * 1.1f
        if (glow == null || gw != width || gh != height) {
            gw = width; gh = height
            glow = RadialGradient(cx, cy, r * 0.6f, intArrayOf(0xccffffff.toInt(), 0x66ffe14d, 0x00ffe14d), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        }
        val step = 360f / rays
        c.save(); c.rotate(time * 18f, cx, cy)
        for (i in 0 until rays) {
            p.color = if (i % 2 == 0) c1 else c2
            val a0 = Math.toRadians((i * step).toDouble()); val a1 = Math.toRadians(((i + 1) * step).toDouble())
            path.reset(); path.moveTo(cx, cy)
            path.lineTo(cx + cos(a0).toFloat() * r, cy + sin(a0).toFloat() * r)
            path.lineTo(cx + cos(a1).toFloat() * r, cy + sin(a1).toFloat() * r); path.close()
            c.drawPath(path, p)
        }
        c.restore()
        p.shader = glow; c.drawCircle(cx, cy, r, p); p.shader = null
    }
}

/** Confetti rain plus bursts. Pieces flutter (squash on one axis) as they fall. */
class ConfettiView(ctx: Context, private val amount: Int = 160) : AnimatedView(ctx) {
    private class Bit(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, var spin: Float, val color: Int, val size: Float, val round: Boolean, var flip: Float)
    private val bits = ArrayList<Bit>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(Ui.PINK, Ui.YELLOW, Ui.BLUE, Ui.GREEN, Ui.ORANGE, Ui.PURPLE, Color.WHITE)
    private var started = false
    init { isClickable = false }

    /** Pop a fan of confetti from a point (0..1 view coords). */
    fun burst(fx: Float, fy: Float, n: Int = 70) {
        if (width == 0) { post { burst(fx, fy, n) }; return }
        repeat(n) {
            val a = Random.nextFloat() * PI.toFloat() * 2; val sp = Ui.dp(300f + Random.nextFloat() * 700f)
            bits.add(Bit(fx * width, fy * height, cos(a) * sp, sin(a) * sp - Ui.dp(400f), Random.nextFloat() * 360, (Random.nextFloat() - 0.5f) * 900, colors.random(), Ui.dp(7f + Random.nextFloat() * 8f), Random.nextInt(4) == 0, Random.nextFloat() * 6f))
        }
    }

    override fun draw(c: Canvas, dt: Float) {
        val w = width.toFloat(); val h = height.toFloat()
        if (!started && w > 0) {
            started = true
            repeat(amount) { bits.add(Bit(Random.nextFloat() * w, -Random.nextFloat() * h, (Random.nextFloat() - 0.5f) * Ui.dp(80f), Ui.dp(120f + Random.nextFloat() * 160f), Random.nextFloat() * 360, (Random.nextFloat() - 0.5f) * 500, colors.random(), Ui.dp(7f + Random.nextFloat() * 8f), Random.nextInt(4) == 0, Random.nextFloat() * 6f)) }
        }
        val g = Ui.dp(900f); val it = bits.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.vy = min(b.vy + g * dt, Ui.dp(260f)); b.vx *= (1f - 2.5f * dt)
            b.x += (b.vx + sin(time * 3f + b.flip) * Ui.dp(40f)) * dt; b.y += b.vy * dt
            b.rot += b.spin * dt; b.flip += dt * 9f
            if (b.y > h + b.size * 2) {
                if (bits.size > amount) { it.remove(); continue }
                b.y = -b.size * 2; b.x = Random.nextFloat() * w
            }
            p.color = b.color
            c.save(); c.translate(b.x, b.y); c.rotate(b.rot); c.scale(1f, abs(cos(b.flip)) * 0.8f + 0.2f)
            if (b.round) c.drawCircle(0f, 0f, b.size * 0.5f, p) else c.drawRect(-b.size * 0.5f, -b.size * 0.3f, b.size * 0.5f, b.size * 0.3f, p)
            c.restore()
        }
    }
}

/** Track outlines for map previews and the in-race mini-map, normalised to fit a unit box. */
object TrackMaps {
    class Map(val xs: FloatArray, val ys: FloatArray, val scale: Float, val cx: Float, val cz: Float, val length: Float, val flipX: Boolean) {
        /** Point for distance [s] along the loop, in 0..1 box coordinates. */
        fun at(path: TrackPath, s: Float, f: Frame, out: FloatArray) {
            path.frame(s, f)
            out[0] = 0.5f + (if (flipX) -1 else 1) * (f.x - cx) * scale
            out[1] = 0.5f + (f.z - cz) * scale
        }
    }
    private val cache = HashMap<TrackShape, Map>()
    private val paths = HashMap<TrackShape, TrackPath>()

    fun path(shape: TrackShape) = synchronized(this) { paths.getOrPut(shape) { TrackPath(shape) } }

    fun of(path: TrackPath, points: Int = 96): Map = synchronized(this) {
        cache.getOrPut(path.shape) {
            val f = Frame(); val xs = FloatArray(points); val zs = FloatArray(points)
            for (i in 0 until points) { path.frame(path.length * i / points, f); xs[i] = f.x; zs[i] = f.z }
            val minX = xs.min(); val maxX = xs.max(); val minZ = zs.min(); val maxZ = zs.max()
            val scale = 0.86f / max(maxX - minX, maxZ - minZ)
            val cx = (minX + maxX) / 2; val cz = (minZ + maxZ) / 2
            // Seen from above (right-handed, y up): screen x = world x, screen y (down) = world z.
            val flip = false
            val nx = FloatArray(points) { 0.5f + (if (flip) -1 else 1) * (xs[it] - cx) * scale }
            val ny = FloatArray(points) { 0.5f + (zs[it] - cz) * scale }
            Map(nx, ny, scale, cx, cz, path.length, flip)
        }
    }

    fun drawOutline(c: Canvas, m: Map, l: Float, t: Float, size: Float, p: Paint, path: Path, roadColor: Int, edgeColor: Int, width: Float) {
        path.reset()
        for (i in m.xs.indices) { val x = l + m.xs[i] * size; val y = t + m.ys[i] * size; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        path.close()
        p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = width * 1.8f; p.color = edgeColor; c.drawPath(path, p)
        p.strokeWidth = width; p.color = roadColor; c.drawPath(path, p)
        // start / finish chequer
        p.style = Paint.Style.FILL
        val x0 = l + m.xs[0] * size; val y0 = t + m.ys[0] * size
        p.color = Color.WHITE; c.drawCircle(x0, y0, width * 0.9f, p)
        p.color = Ui.INK; c.drawCircle(x0, y0, width * 0.45f, p)
    }
}

/** A little map of a track's loop for the track picker tiles. */
class TrackMapView(ctx: Context, shape: TrackShape, private val road: Int) : View(ctx) {
    private val map = TrackMaps.of(TrackMaps.path(shape))
    private val p = Paint(Paint.ANTI_ALIAS_FLAG); private val path = Path()
    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        TrackMaps.drawOutline(c, map, (width - s) / 2, (height - s) / 2, s, p, path, road, Ui.INK, Ui.dp(5f))
    }
}

/** A button grown-ups must hold for a moment: a ring fills up, then it fires. Keeps little fingers out of settings. */
@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class HoldButton(ctx: Context, private val label: String, private val color: Int, private val holdSeconds: Float = 1.6f, private val onDone: () -> Unit) : AnimatedView(ctx) {
    private var holding = false; private var fill = 0f; private var fired = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font; textAlign = Paint.Align.CENTER; color = Color.WHITE }
    init {
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { holding = true; fired = false; animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { holding = false; animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(3f)).setDuration(200).start() }
            }
            true
        }
    }
    override fun draw(c: Canvas, dt: Float) {
        fill = if (holding) min(1f, fill + dt / holdSeconds) else max(0f, fill - dt * 3f)
        if (fill >= 1f && !fired) { fired = true; holding = false; fill = 0f; onDone() }
        val cx = width / 2f; val cy = height / 2f; val r = min(cx, cy) - Ui.dp(8f)
        p.style = Paint.Style.FILL; p.color = 0x55000000; c.drawCircle(cx, cy + Ui.dp(4f), r, p)
        p.color = color; c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dp(4f); p.color = Color.WHITE; c.drawCircle(cx, cy, r, p)
        if (fill > 0f) {
            p.strokeWidth = Ui.dp(7f); p.color = Ui.YELLOW; p.strokeCap = Paint.Cap.ROUND
            c.drawArc(cx - r + Ui.dp(7f), cy - r + Ui.dp(7f), cx + r - Ui.dp(7f), cy + r - Ui.dp(7f), -90f, 360f * fill, false, p)
        }
        txt.textSize = r * 0.95f
        c.drawText(label, cx, cy + txt.textSize * 0.35f, txt)
    }
}
