package com.vroombuddies.game.sim

import kotlin.math.*

/** Shape settings for one race track. Pure data: no rendering here, so the simulation can be unit tested on the JVM. */
data class TrackShape(
    val seed: Int,
    val radius: Float = 520f,      // average loop radius in metres
    val wobble: Float = 0.25f,     // how far the loop bends in and out (0..0.35)
    val hills: Float = 10f,        // max hill height in metres
    val scaleX: Float = 1.2f,      // stretch the loop
    val scaleZ: Float = 1f,
    val controlPoints: Int = 12,
    val jumps: Int = 3,
    val halfWidth: Float = 13f,    // road half width (metres). Wide on purpose: it's for little kids.
)

class Jump(val s0: Float, val length: Float, val height: Float) {
    val sEnd get() = s0 + length
}

class BoostPad(val s: Float, val x: Float, val halfWidth: Float = 3f, val halfLength: Float = 4.5f)

class KnockSpot(val s: Float, val x: Float)

/** A position along the road: centre point, tangent (horizontal unit vector) and right vector. */
class Frame {
    var x = 0f; var y = 0f; var z = 0f
    var tx = 0f; var tz = 1f        // horizontal unit tangent
    var rx = -1f; var rz = 0f       // horizontal unit right (screen right when looking along the tangent)
    var kappa = 0f                  // curvature dθ/ds, positive = bending right
    var slope = 0f                  // dy/ds of the road centre
    var theta = 0f
}

/**
 * The centre line of a closed loop track, resampled every [DS] metres.
 * Cars live in "track space": s = distance along the loop, x = sideways offset (+ is right).
 */
class TrackPath(val shape: TrackShape) {
    companion object { const val DS = 2f }

    val halfWidth = shape.halfWidth
    val n: Int
    val length: Float
    private val px: FloatArray; private val py: FloatArray; private val pz: FloatArray
    private val th: FloatArray      // heading angle θ of the tangent (unwrapped)
    private val kap: FloatArray
    val jumps = ArrayList<Jump>()
    val chargeGates = ArrayList<BoostPad>()
    val pads = ArrayList<BoostPad>()
    val knockSpots = ArrayList<KnockSpot>()

    init {
        val rng = Rng(shape.seed)
        val m = shape.controlPoints
        val cx = DoubleArray(m); val cy = DoubleArray(m); val cz = DoubleArray(m)
        for (i in 0 until m) {
            val a = i.toDouble() / m * PI * 2 + (rng.f() - 0.5) * 0.25
            val r = shape.radius * (1.0 + shape.wobble * (rng.f() * 2 - 1))
            cx[i] = cos(a) * r * shape.scaleX
            cz[i] = sin(a) * r * shape.scaleZ
            cy[i] = 1.0 + rng.f() * shape.hills
        }
        // keep the start straight-ish and low: flatten the first and last control points
        cy[0] = 1.0; cy[m - 1] = (cy[m - 1] + 1.0) / 2
        // Dense centripetal Catmull-Rom polyline.
        val dense = ArrayList<DoubleArray>()
        val per = 60
        for (i in 0 until m) {
            val p0 = i - 1; val p1 = i; val p2 = i + 1; val p3 = i + 2
            for (k in 0 until per) {
                val t = k.toDouble() / per
                dense.add(doubleArrayOf(
                    cr(cx, p0, p1, p2, p3, t, m), cr(cy, p0, p1, p2, p3, t, m), cr(cz, p0, p1, p2, p3, t, m)))
            }
        }
        // cumulative length
        val cum = DoubleArray(dense.size + 1)
        for (i in 0 until dense.size) {
            val a = dense[i]; val b = dense[(i + 1) % dense.size]
            cum[i + 1] = cum[i] + hypot(b[0] - a[0], b[2] - a[2])
        }
        val total = cum[dense.size]
        n = (total / DS).toInt()
        length = n * DS
        px = FloatArray(n); py = FloatArray(n); pz = FloatArray(n)
        var j = 0
        for (i in 0 until n) {
            val target = i.toDouble() / n * total
            while (cum[j + 1] < target) j++
            val f = (target - cum[j]) / max(1e-9, cum[j + 1] - cum[j])
            val a = dense[j]; val b = dense[(j + 1) % dense.size]
            px[i] = (a[0] + (b[0] - a[0]) * f).toFloat()
            py[i] = (a[1] + (b[1] - a[1]) * f).toFloat()
            pz[i] = (a[2] + (b[2] - a[2]) * f).toFloat()
        }
        // smooth heights so hills are gentle
        repeat(6) {
            val c = py.copyOf()
            for (i in 0 until n) py[i] = (c[(i - 2 + n) % n] + c[(i - 1 + n) % n] + c[i] + c[(i + 1) % n] + c[(i + 2) % n]) / 5f
        }
        th = FloatArray(n)
        for (i in 0 until n) {
            val a = (i - 1 + n) % n; val b = (i + 1) % n
            th[i] = atan2(pz[b] - pz[a], px[b] - px[a])
        }
        kap = FloatArray(n)
        for (i in 0 until n) {
            var d = th[(i + 1) % n] - th[(i - 1 + n) % n]
            while (d > PI) d -= (2 * PI).toFloat(); while (d < -PI) d += (2 * PI).toFloat()
            kap[i] = d / (2 * DS)
        }
        placeJumps(rng)
        placePads()
        placeKnocks(rng)
        for (i in 0..2) {
            val target = length * (0.18f + i * 0.33f)
            val s = (0..12).map { wrap(target + it * 14f) }.firstOrNull { pos ->
                jumps.none { delta(it.s0, pos) in -30f..(it.length + 75f) } &&
                    pads.none { abs(delta(it.s, pos)) < 18f }
            } ?: continue
            val lane = ((i + shape.seed) % 3 - 1) * halfWidth * 0.40f
            chargeGates.add(BoostPad(s, lane, halfWidth = 3.8f, halfLength = 3f))
        }
    }

    private fun cr(v: DoubleArray, i0: Int, i1: Int, i2: Int, i3: Int, t: Double, m: Int): Double {
        val a = v[(i0 + m) % m]; val b = v[i1 % m]; val c = v[i2 % m]; val d = v[i3 % m]
        val t2 = t * t; val t3 = t2 * t
        return 0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
    }

    fun wrap(s: Float): Float { val r = s % length; return if (r < 0) r + length else r }

    /** Signed shortest distance from a to b along the loop. */
    fun delta(a: Float, b: Float): Float { var d = wrap(b) - wrap(a); if (d > length / 2) d -= length; if (d < -length / 2) d += length; return d }

    fun frame(sIn: Float, out: Frame = Frame()): Frame {
        val s = wrap(sIn)
        val fi = s / DS
        val i0 = fi.toInt() % n; val i1 = (i0 + 1) % n
        val f = fi - fi.toInt()
        out.x = px[i0] + (px[i1] - px[i0]) * f
        out.y = py[i0] + (py[i1] - py[i0]) * f
        out.z = pz[i0] + (pz[i1] - pz[i0]) * f
        var dt = th[i1] - th[i0]
        if (dt > PI) dt -= (2 * PI).toFloat(); if (dt < -PI) dt += (2 * PI).toFloat()
        val t = th[i0] + dt * f
        out.theta = t
        out.tx = cos(t); out.tz = sin(t)
        out.rx = -out.tz; out.rz = out.tx
        out.kappa = kap[i0] + (kap[i1] - kap[i0]) * f
        out.slope = (py[i1] - py[i0]) / DS
        return out
    }

    fun centreHeight(s: Float): Float {
        val w = wrap(s); val fi = w / DS; val i0 = fi.toInt() % n; val i1 = (i0 + 1) % n; val f = fi - fi.toInt()
        return py[i0] + (py[i1] - py[i0]) * f
    }

    fun rampHeight(s: Float): Float {
        val w = wrap(s)
        for (j in jumps) {
            val u = (w - j.s0) / j.length
            if (u in 0f..1f) {
                // ease in so wheels roll on smoothly, then a straight lip
                return j.height * if (u < 0.25f) (u * u / 0.5f) * 1f else (0.125f + (u - 0.25f) * (0.875f / 0.75f))
            }
        }
        return 0f
    }

    fun jumpAt(s: Float): Jump? { val w = wrap(s); return jumps.firstOrNull { w >= it.s0 && w <= it.sEnd } }

    fun groundHeight(s: Float): Float = centreHeight(s) + rampHeight(s)

    fun pointAt(s: Float, x: Float, out: FloatArray) {
        val f = frame(s, tmpFrame)
        out[0] = f.x + f.rx * x; out[1] = f.y; out[2] = f.z + f.rz * x
    }
    private val tmpFrame = Frame()

    /** Find (s, x) for a world point, searching near [hint]. Returns s in out[0], x in out[1]. */
    fun locate(wx: Float, wz: Float, hint: Float, out: FloatArray, window: Int = 40) {
        val h = (wrap(hint) / DS).toInt()
        var best = Float.MAX_VALUE; var bi = h
        for (k in -window..window) {
            val i = ((h + k) % n + n) % n
            val dx = wx - px[i]; val dz = wz - pz[i]
            val d = dx * dx + dz * dz
            if (d < best) { best = d; bi = i }
        }
        val tx = cos(th[bi]); val tz = sin(th[bi])
        val dx = wx - px[bi]; val dz = wz - pz[bi]
        out[0] = wrap(bi * DS + dx * tx + dz * tz)
        out[1] = dx * -tz + dz * tx
    }

    private fun headingChange(s0: Float, s1: Float): Float {
        var d = frame(s1).theta - frame(s0).theta
        while (d > PI) d -= (2 * PI).toFloat(); while (d < -PI) d += (2 * PI).toFloat()
        return abs(d)
    }

    private fun placeJumps(rng: Rng) {
        if (shape.jumps <= 0) return
        val cands = ArrayList<Pair<Float, Float>>()
        var s = 300f
        while (s < length - 300f) { cands.add(s to headingChange(s - 40f, s + 160f)); s += 20f }
        cands.sortBy { it.second }
        val minGap = length / (shape.jumps + 1) * 0.7f
        for ((cs, curv) in cands) {
            if (jumps.size >= shape.jumps) break
            if (curv > 0.5f) break
            if (jumps.any { abs(delta(it.s0, cs)) < minGap }) continue
            jumps.add(Jump(cs, 22f, 3.2f + rng.f() * 0.8f))
        }
        jumps.sortBy { it.s0 }
    }

    private fun nearJump(s: Float, before: Float, after: Float) = jumps.any { val d = delta(it.s0, s); d > -before && d < it.length + after }

    private fun placePads() {
        // one centred pad before every jump so cars arrive fast
        for (j in jumps) pads.add(BoostPad(wrap(j.s0 - 45f), 0f))
        val lanes = floatArrayOf(-7f, 0f, 7f, 0f)
        var s = 160f; var k = 0
        while (s < length - 80f) {
            if (!nearJump(s, 90f, 60f)) { pads.add(BoostPad(s, lanes[k % lanes.size])); k++ }
            s += 210f
        }
    }

    private fun placeKnocks(rng: Rng) {
        // stacks on the outside of bends and a few on straights, never on pads or jumps
        var s = 120f
        val f = Frame()
        while (s < length - 60f) {
            frame(s, f)
            val onPad = pads.any { abs(delta(it.s, s)) < 14f }
            if (!onPad && !nearJump(s, 40f, 90f)) {
                val side = if (abs(f.kappa) > 0.002f) -sign(f.kappa) else if (rng.f() < 0.5f) 1f else -1f
                knockSpots.add(KnockSpot(s, side * (halfWidth - 2.2f)))
                if (rng.f() < 0.25f) knockSpots.add(KnockSpot(s + 6f, side * (halfWidth - 2.2f)))
            }
            s += 85f + rng.f() * 60f
        }
    }
}

/** Small deterministic random generator (mulberry32) so every track is the same each time it's played. */
class Rng(seed: Int) {
    private var a = seed
    fun f(): Float {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toFloat() / 4294967296f
    }
    fun range(a: Float, b: Float) = a + (b - a) * f()
    fun int(n: Int) = (f() * n).toInt().coerceAtMost(n - 1)
}
