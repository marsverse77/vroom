package com.vroombuddies.game.audio

import kotlin.math.*

/** Melodic instruments for the music and jingles. All synthesised; no samples. */
internal enum class Inst(val perc: Boolean, val decay: Float, val attack: Float = 0.005f, val release: Float = 0.08f) {
    SQUARE(false, 1.2f, 0.008f),       // chirpy chiptune lead
    SAW(false, 0.8f, 0.01f),           // fat synth lead (neon, volcano)
    BRASS(false, 1.0f, 0.03f, 0.1f),   // fanfare horns
    FLUTE(false, 0.3f, 0.05f, 0.12f),  // breathy pipe (meadow, woods)
    ORGAN(false, 0.2f, 0.01f),         // fairground organ (sweets, toy town)
    BELL(true, 2.6f),                  // twinkly FM bell (space, snow, crystal)
    MARIMBA(true, 7f),                 // wooden mallet (jungle, dino)
    STEEL(true, 3.5f),                 // steel drum (beach, reef)
    PLUCK(true, 0f),                   // plucked guitar string (desert, sunset, pirate)
    MUSICBOX(true, 3f),                // music box tines (toy, rainbow, menu)
    STAB(true, 9f),                    // short chord stab
    BASS_SQ(false, 1.5f, 0.004f, 0.04f),
    BASS_SAW(false, 2f, 0.004f, 0.04f),
    BASS_SUB(false, 0.8f, 0.01f, 0.06f),
    BASS_PLUCK(true, 0f),
}

/** One played note of an [Inst]. [len] is how long the key is held, in seconds. */
internal class NoteVoice(private val inst: Inst, private val freq: Float, private val len: Float, private val vol: Float, seed: Int = 7) : Voice() {
    private var t = 0f
    private var ph = 0f
    private var ph2 = 0f
    private var env = 0f
    private var held = 1f
    private val inc = freq * DT
    private val decayMul = Dsp.decayMul(inst.decay)
    private val sagMul = Dsp.decayMul(inst.decay * 0.15f)
    private val relMul = Dsp.decayMul(1f / inst.release)
    private val atkStep = DT / inst.attack
    private var bright = 1f
    private var lp1 = 0f; private var lp2 = 0f
    private val rng = Rng(seed * 7919 + (freq * 13).toInt())
    // Karplus-Strong string for the plucked instruments
    private val ks: FloatArray? = if (inst == Inst.PLUCK || inst == Inst.BASS_PLUCK) FloatArray(max(2, (RATE / freq).roundToInt())) else null
    private var ksI = 0
    private val ksDamp = if (inst == Inst.BASS_PLUCK) 0.994f else 0.997f

    init {
        ks?.let { b ->
            var lp = 0f
            val k = if (inst == Inst.BASS_PLUCK) 0.25f else 0.6f
            for (i in b.indices) { lp += (rng.bip() - lp) * k; b[i] = lp }
        }
        if (inst.perc) env = 0f
    }

    override fun next(): Float {
        t += DT
        // envelope: quick attack, then either a decay (percussive) or a gentle sag (sustained), and a release after the key lifts
        if (env < 1f && t < inst.attack + DT) env = min(1f, env + atkStep)
        else if (inst.perc) env *= decayMul
        else env = max(0.65f, env * sagMul)
        if (t > len) { held *= relMul; if (held < 0.0005f) { done = true; return 0f } }
        if (inst.perc && env < 0.0005f && ks == null) { done = true; return 0f }
        val e = env * held

        val s: Float = when (inst) {
            Inst.SQUARE -> {
                val vib = if (t > 0.12f) 1f + 0.006f * Dsp.sin1(t * 5.5f) else 1f
                ph += inc * vib
                val v = Dsp.pulse(ph, inc, 0.25f)
                lp1 += (v - lp1) * 0.35f; lp1 * 0.55f
            }
            Inst.SAW -> {
                ph += inc * 1.004f; ph2 += inc * 0.996f
                val v = Dsp.saw(ph, inc) + Dsp.saw(ph2, inc)
                lp1 += (v - lp1) * 0.22f; lp2 += (lp1 - lp2) * 0.35f; lp2 * 0.38f
            }
            Inst.BRASS -> {
                ph += inc * (1f + 0.004f * Dsp.sin1(t * 5f) * min(1f, t * 3f))
                bright = max(0.25f, bright * 0.9998f)
                val v = Dsp.saw(ph, inc)
                val k = (0.05f + 0.35f * bright * min(1f, t * 20f))
                lp1 += (v - lp1) * k; lp2 += (lp1 - lp2) * k; lp2 * 0.75f
            }
            Inst.FLUTE -> {
                ph += inc * (1f + 0.005f * Dsp.sin1(t * 5f) * min(1f, t * 2f))
                val breath = rng.bip() * 0.06f
                lp1 += (breath - lp1) * 0.3f
                (Dsp.sin1(ph) + 0.18f * Dsp.sin1(ph * 2f) + lp1) * 0.7f
            }
            Inst.ORGAN -> {
                ph += inc
                (Dsp.sin1(ph) + 0.5f * Dsp.sin1(ph * 2f) + 0.3f * Dsp.sin1(ph * 3f) + 0.15f * Dsp.sin1(ph * 4f)) * 0.42f
            }
            Inst.BELL -> {
                ph += inc
                val idx = 1.6f * env
                Dsp.sin1(ph + idx * Dsp.sin1(ph * 3.5f)) * 0.7f + 0.15f * Dsp.sin1(ph * 2f) * env
            }
            Inst.MARIMBA -> {
                ph += inc
                Dsp.sin1(ph) * 0.8f + 0.35f * Dsp.sin1(ph * 3.93f) * env * env
            }
            Inst.STEEL -> {
                ph += inc
                Dsp.sin1(ph + 0.9f * env * Dsp.sin1(ph * 2f)) * 0.65f + 0.2f * Dsp.sin1(ph * 2.01f) * env
            }
            Inst.MUSICBOX -> {
                ph += inc
                Dsp.sin1(ph) * 0.6f + 0.3f * Dsp.sin1(ph * 2f) * env + 0.12f * Dsp.sin1(ph * 5.43f) * env * env
            }
            Inst.STAB -> {
                ph += inc
                val v = Dsp.pulse(ph, inc, 0.35f)
                lp1 += (v - lp1) * 0.25f; lp1 * 0.5f
            }
            Inst.BASS_SQ -> {
                ph += inc
                val v = Dsp.pulse(ph, inc, 0.5f)
                lp1 += (v - lp1) * 0.12f; lp2 += (lp1 - lp2) * 0.3f; lp2 * 0.9f
            }
            Inst.BASS_SAW -> {
                ph += inc
                bright *= 0.99985f
                val v = Dsp.saw(ph, inc)
                val k = 0.03f + 0.15f * bright
                lp1 += (v - lp1) * k; lp2 += (lp1 - lp2) * k; lp2 * 1.0f
            }
            Inst.BASS_SUB -> {
                ph += inc
                (Dsp.sin1(ph) + 0.25f * Dsp.sin1(ph * 2f)) * 0.9f
            }
            Inst.PLUCK, Inst.BASS_PLUCK -> {
                val b = ks!!
                val j = if (ksI + 1 >= b.size) 0 else ksI + 1
                val out = b[ksI]
                b[ksI] = (b[ksI] + b[j]) * 0.5f * ksDamp
                ksI = j
                if (t > 4f) done = true
                out * (if (inst == Inst.BASS_PLUCK) 1.4f else 1.1f)
            }
        }
        return s * e * vol
    }
}

internal enum class Drum { KICK, SNARE, CLAP, HAT, OPEN_HAT, SHAKER, CRASH, TOM, WOOD, TAMB, ROLL }

/** Drum kit voice. */
internal class DrumVoice(private val kind: Drum, private val vol: Float, seed: Int = 3) : Voice() {
    private var t = 0f
    private var ph = 0f
    private val rng = Rng(seed * 31 + kind.ordinal * 977 + 1)
    private val svf = Svf()
    private var hpPrev = 0f; private var lp = 0f

    private fun hp(x: Float): Float { lp += (x - lp) * 0.35f; return x - lp }

    override fun next(): Float {
        t += DT
        val s: Float = when (kind) {
            Drum.KICK -> {
                if (t > 0.45f) { done = true; 0f } else {
                    val f = 48f + 120f * exp(-t * 32f)
                    ph += f * DT
                    Dsp.sin1(ph) * exp(-t * 8f) * 1.1f + (if (t < 0.004f) rng.bip() * 0.3f else 0f)
                }
            }
            Drum.SNARE -> {
                if (t > 0.3f) { done = true; 0f } else {
                    ph += 185f * DT
                    Dsp.sin1(ph) * exp(-t * 28f) * 0.45f + hp(rng.bip()) * exp(-t * 16f) * 0.6f
                }
            }
            Drum.CLAP -> {
                if (t > 0.35f) { done = true; 0f } else {
                    val n = rng.bip(); svf.run(n, 1300f, 0.9f)
                    val bursts = if (t < 0.03f) exp(-(t % 0.01f) * 300f) else exp(-(t - 0.03f) * 18f)
                    svf.band * bursts * 1.3f
                }
            }
            Drum.HAT -> {
                if (t > 0.08f) { done = true; 0f } else { val n = rng.bip(); svf.run(n, 8000f, 0.6f); (svf.high + svf.band) * exp(-t * 70f) * 0.35f }
            }
            Drum.OPEN_HAT -> {
                if (t > 0.4f) { done = true; 0f } else { val n = rng.bip(); svf.run(n, 7500f, 0.6f); (svf.high + svf.band) * exp(-t * 11f) * 0.25f }
            }
            Drum.SHAKER -> {
                if (t > 0.09f) { done = true; 0f } else { val n = rng.bip(); svf.run(n, 6000f, 0.8f); svf.band * min(1f, t * 150f) * exp(-t * 40f) * 0.5f }
            }
            Drum.CRASH -> {
                if (t > 2.2f) { done = true; 0f } else { val n = rng.bip(); svf.run(n, 6000f, 0.5f); (svf.high * 0.7f + svf.band * 0.5f) * exp(-t * 2.2f) * 0.4f }
            }
            Drum.TOM -> {
                if (t > 0.5f) { done = true; 0f } else { ph += (110f + 60f * exp(-t * 20f)) * DT; Dsp.sin1(ph) * exp(-t * 9f) * 0.8f }
            }
            Drum.WOOD -> {
                if (t > 0.12f) { done = true; 0f } else { ph += 880f * DT; (Dsp.sin1(ph) + 0.4f * Dsp.sin1(ph * 2.7f)) * exp(-t * 45f) * 0.5f }
            }
            Drum.TAMB -> {
                if (t > 0.25f) { done = true; 0f } else {
                    val n = rng.bip(); svf.run(n, 9000f, 0.4f)
                    svf.band * (exp(-t * 14f) + 0.5f * exp(-abs(t - 0.03f) * 120f)) * 0.35f
                }
            }
            Drum.ROLL -> { // snare roll for fanfares: 16 quick hits over 0.5 s
                if (t > 0.7f) { done = true; 0f } else {
                    val local = t % 0.03f
                    val swell = 0.4f + t * 1.2f
                    hp(rng.bip()) * exp(-local * 90f) * 0.5f * swell
                }
            }
        }
        return s * vol
    }
}
