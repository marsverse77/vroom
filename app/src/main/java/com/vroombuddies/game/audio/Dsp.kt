package com.vroombuddies.game.audio

import kotlin.math.*

/** Output sample rate of the whole synth. Everything in this package is pure Kotlin, so it runs in JVM tests too. */
internal const val RATE = 44100
internal const val DT = 1f / RATE

/** Small oscillator and filter toolbox. Phases are in cycles (1.0 = one full period). */
internal object Dsp {
    private const val N = 4096
    private val table = FloatArray(N + 1) { sin(it * 2.0 * PI / N).toFloat() }

    fun frac(x: Float) = x - floor(x)

    /** Table sine of a phase in cycles. */
    fun sin1(phase: Float): Float {
        val x = frac(phase) * N
        val i = x.toInt().coerceAtMost(N - 1); val f = x - i
        return table[i] + (table[i + 1] - table[i]) * f
    }

    /** PolyBLEP correction so saws and pulses don't alias (a phone speaker makes aliasing sound fizzy). */
    private fun blep(t: Float, dt: Float): Float = when {
        t < dt -> { val x = t / dt; x + x - x * x - 1f }
        t > 1f - dt -> { val x = (t - 1f) / dt; x * x + x + x + 1f }
        else -> 0f
    }

    /** Band-limited saw; [inc] is the phase step per sample (freq / RATE). */
    fun saw(ph: Float, inc: Float): Float { val t = frac(ph); return 2f * t - 1f - blep(t, inc) }

    /** Band-limited pulse with duty [w]. */
    fun pulse(ph: Float, inc: Float, w: Float): Float {
        val t = frac(ph)
        var v = if (t < w) 1f else -1f
        v += blep(t, inc)
        v -= blep(frac(t + 1f - w), inc)
        return v
    }

    fun tri(ph: Float): Float = 4f * abs(frac(ph) - 0.5f) - 1f

    fun midi(n: Float): Float = 440f * 2f.pow((n - 69f) / 12f)

    /** Per-sample multiplier for an exponential decay of [perSecond] e-folds per second. */
    fun decayMul(perSecond: Float): Float = exp(-perSecond * DT)

    /** One-pole low-pass coefficient for a cutoff in Hz. */
    fun lpK(hz: Float): Float = (1f - exp(-2f * PI.toFloat() * hz * DT)).coerceIn(0.0001f, 1f)
}

/** Fast deterministic noise (xorshift). */
internal class Rng(seed: Int) {
    private var s = if (seed == 0) 0x2545F491 else seed
    fun next(): Float {
        s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
        return (s.toLong() and 0xffffffffL).toFloat() / 4294967296f
    }
    fun bip(): Float = next() * 2f - 1f
    fun int(n: Int): Int = (next() * n).toInt().coerceIn(0, n - 1)
    fun chance(p: Float) = next() < p
}

/** Chamberlin state-variable filter: low, band and high outputs from one update. */
internal class Svf {
    var low = 0f; var band = 0f; var high = 0f
    fun run(x: Float, hz: Float, q: Float = 0.7f) {
        val f = (2f * sin(PI.toFloat() * min(hz, RATE * 0.22f) / RATE))
        high = x - low - q * band
        band += f * high
        low += f * band
    }
}

/** Something that makes sound, one sample at a time, and says when it has finished. */
internal abstract class Voice {
    var done = false
    abstract fun next(): Float
}

/** Starts [inner] after [delay] seconds. */
internal class Delayed(delay: Float, private val inner: Voice) : Voice() {
    private var wait = (delay * RATE).toInt()
    override fun next(): Float {
        if (wait > 0) { wait--; return 0f }
        val s = inner.next(); done = inner.done; return s
    }
}
