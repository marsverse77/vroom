package com.vroombuddies.game.audio

import kotlin.math.*

/** Pitch sweep (exponential glide from [f0] to [f1]). wave: 0 sine, 1 square, 2 saw, 3 triangle. */
internal class Sweep(private val f0: Float, private val f1: Float, private val dur: Float, private val vol: Float, private val wave: Int = 0, private val attack: Float = 0.004f) : Voice() {
    private var t = 0f; private var ph = 0f; private var lp = 0f
    override fun next(): Float {
        t += DT
        if (t >= dur) { done = true; return 0f }
        val u = t / dur
        val f = f0 * (f1 / f0).pow(u)
        val inc = f * DT
        ph += inc
        val w = when (wave) { 1 -> Dsp.pulse(ph, inc, 0.5f); 2 -> Dsp.saw(ph, inc); 3 -> Dsp.tri(ph); else -> Dsp.sin1(ph) }
        lp += (w - lp) * 0.4f
        val env = min(1f, t / attack) * (1f - u) * (1f - u * 0.3f)
        return (if (wave == 0 || wave == 3) w else lp) * env * vol
    }
}

/** Filtered noise whose band moves from [c0] to [c1] Hz. Rises then falls when [swell] is set (a whoosh). */
internal class NoiseSweep(private val dur: Float, private val vol: Float, private val c0: Float, private val c1: Float, private val q: Float = 0.8f, private val swell: Boolean = false, seed: Int = 5) : Voice() {
    private var t = 0f; private val f = Svf(); private val rng = Rng(seed)
    override fun next(): Float {
        t += DT
        if (t >= dur) { done = true; return 0f }
        val u = t / dur
        f.run(rng.bip(), c0 * (c1 / c0).pow(u), q)
        val env = if (swell) sin(u * PI.toFloat()).let { it * it } else (1f - u).pow(2) * min(1f, t * 400f)
        return f.band * env * vol
    }
}

/** Cartoon spring "boing": a tone that wobbles in pitch as it fades. */
internal class Boing(private val freq: Float, private val dur: Float, private val vol: Float) : Voice() {
    private var t = 0f; private var ph = 0f
    override fun next(): Float {
        t += DT
        if (t >= dur) { done = true; return 0f }
        val u = t / dur
        ph += freq * (1f + 0.35f * (1f - u) * Dsp.sin1(t * 14f)) * DT
        return (Dsp.sin1(ph) + 0.2f * Dsp.sin1(ph * 2f)) * exp(-u * 4f) * min(1f, t * 300f) * vol
    }
}

/** Sparkly crackle (firework tail, glitter): sparse random clicks through a bright filter. */
internal class Crackle(private val dur: Float, private val vol: Float, seed: Int) : Voice() {
    private var t = 0f; private val rng = Rng(seed); private val f = Svf(); private var hit = 0f
    override fun next(): Float {
        t += DT
        if (t >= dur) { done = true; return 0f }
        val u = t / dur
        if (rng.chance(0.0025f * (1f - u))) hit = 1f
        hit *= 0.993f
        f.run(rng.bip() * hit, 5000f, 0.5f)
        return f.band * vol
    }
}

/** Metallic clang: a few inharmonic partials. */
internal class Clang(private val base: Float, private val dur: Float, private val vol: Float) : Voice() {
    private var t = 0f; private var p1 = 0f; private var p2 = 0f; private var p3 = 0f
    override fun next(): Float {
        t += DT
        if (t >= dur) { done = true; return 0f }
        p1 += base * DT; p2 += base * 2.76f * DT; p3 += base * 5.4f * DT
        return (Dsp.sin1(p1) * exp(-t * 9f) + 0.6f * Dsp.sin1(p2) * exp(-t * 14f) + 0.3f * Dsp.sin1(p3) * exp(-t * 22f)) * vol
    }
}

/**
 * Sound effect recipes. Every effect is soft-edged and cartoony (bonks, boings and whooshes rather than
 * harsh crashes), with a little random pitch so repeated hits don't sound identical.
 */
internal object Sfx {
    private val rng = Rng(4242)
    private fun vary() = 0.92f + rng.next() * 0.16f

    fun make(name: String, strength: Float): List<Voice> {
        val p = vary(); val seed = rng.int(100000) + 1
        return when (name) {
            "click" -> listOf(Sweep(700f * p, 1100f * p, 0.05f, 0.22f))
            "pop" -> listOf(Sweep(300f * p, 900f * p, 0.11f, 0.3f), NoiseSweep(0.05f, 0.15f, 3000f, 3000f, seed = seed))
            "beep" -> listOf(NoteVoice(Inst.SQUARE, Dsp.midi(72f), 0.2f, 0.35f), NoteVoice(Inst.BELL, Dsp.midi(84f), 0.2f, 0.15f))
            "go" -> listOf(
                NoteVoice(Inst.SQUARE, Dsp.midi(84f), 0.55f, 0.3f), NoteVoice(Inst.SQUARE, Dsp.midi(88f), 0.55f, 0.18f),
                NoteVoice(Inst.SQUARE, Dsp.midi(91f), 0.55f, 0.18f), NoiseSweep(0.8f, 0.35f, 500f, 4000f, swell = true, seed = seed),
                DrumVoice(Drum.CRASH, 0.5f, seed))
            "boost" -> listOf(
                NoiseSweep(1.1f, 0.75f, 350f, 3800f, 0.7f, swell = true, seed = seed),
                Sweep(90f, 420f, 0.55f, 0.35f, 2),
                Delayed(0.05f, NoteVoice(Inst.BELL, Dsp.midi(84f), 0.1f, 0.12f)),
                Delayed(0.11f, NoteVoice(Inst.BELL, Dsp.midi(88f), 0.1f, 0.12f)),
                Delayed(0.17f, NoteVoice(Inst.BELL, Dsp.midi(91f), 0.1f, 0.12f)),
                Delayed(0.23f, NoteVoice(Inst.BELL, Dsp.midi(96f), 0.2f, 0.12f)))
            "pad" -> listOf(
                NoteVoice(Inst.SQUARE, Dsp.midi(76f), 0.05f, 0.18f),
                Delayed(0.04f, NoteVoice(Inst.SQUARE, Dsp.midi(79f), 0.05f, 0.18f)),
                Delayed(0.08f, NoteVoice(Inst.SQUARE, Dsp.midi(84f), 0.08f, 0.18f)),
                Sweep(500f, 1600f, 0.3f, 0.12f, 3),
                NoiseSweep(0.4f, 0.3f, 800f, 3500f, swell = true, seed = seed))
            "whoosh" -> listOf( // take-off from a ramp: whoosh plus a cartoon slide-whistle up
                NoiseSweep(0.6f, 0.45f, 600f, 2600f, swell = true, seed = seed),
                Sweep(500f * p, 1300f * p, 0.4f, 0.09f, 0, 0.03f))
            "land" -> {
                val s = (strength / 15f).coerceIn(0.25f, 1f)
                listOf(DrumVoice(Drum.KICK, 0.9f * s, seed), NoiseSweep(0.25f, 0.5f * s, 300f, 150f, seed = seed), Boing(170f * p, 0.35f, 0.18f * s))
            }
            "bump" -> { // car taps car: a friendly "bonk"
                val s = (strength / 10f).coerceIn(0.3f, 1f)
                val l = mutableListOf<Voice>(Sweep(430f * p, 170f * p, 0.16f, 0.4f * s), NoiseSweep(0.1f, 0.4f * s, 900f, 400f, seed = seed), DrumVoice(Drum.WOOD, 0.4f * s, seed))
                if (strength > 9f) { l += NoiseSweep(0.3f, 0.35f, 1600f, 500f, 0.5f, seed = seed + 1); l += Crackle(0.25f, 0.6f, seed) }
                l
            }
            "scrape" -> { // brushing the wall: soft grind plus a ding
                val s = (strength / 10f).coerceIn(0.3f, 1f)
                val l = mutableListOf<Voice>(NoiseSweep(0.3f, 0.35f * s, 3200f, 1500f, 0.4f, seed = seed), Crackle(0.3f, 0.5f * s, seed))
                if (strength > 7f) l += Clang(520f * p, 0.5f, 0.16f * s)
                l
            }
            "thud" -> listOf( // tyre stacks and props: rubbery boing
                Boing(150f * p, 0.45f, 0.4f), DrumVoice(Drum.TOM, 0.5f, seed), NoiseSweep(0.12f, 0.3f, 700f, 300f, seed = seed))
            "whoops" -> listOf( // wrong way reset: slide whistle down and back up
                Sweep(1200f, 350f, 0.45f, 0.18f, 3, 0.02f), Delayed(0.45f, Sweep(350f, 900f, 0.35f, 0.18f, 3, 0.02f)),
                Delayed(0.4f, Crackle(0.5f, 0.6f, seed)))
            "firework" -> listOf(
                Sweep(700f * p, 2200f * p, 0.45f, 0.06f, 0, 0.05f),
                Delayed(0.45f, DrumVoice(Drum.KICK, 0.5f, seed)),
                Delayed(0.45f, NoiseSweep(0.5f, 0.3f, 2000f, 600f, 0.6f, seed = seed)),
                Delayed(0.5f, Crackle(1.2f, 0.9f, seed)))
            "win" -> fanfare(true)
            "finish" -> fanfare(false)
            else -> emptyList()
        }
    }

    /** Victory fanfare: snare roll, then horns and bells climbing to a big major chord with a cymbal crash. */
    private fun fanfare(first: Boolean): List<Voice> {
        val l = ArrayList<Voice>()
        val beat = 0.16f
        if (first) {
            l += DrumVoice(Drum.ROLL, 0.8f)
            val line = floatArrayOf(67f, 72f, 76f, 79f, 76f, 79f)
            val at = floatArrayOf(0f, 1f, 2f, 3f, 5f, 6f)
            val len = floatArrayOf(0.9f, 0.9f, 0.9f, 1.8f, 0.9f, 6f)
            for (i in line.indices) {
                val t = 0.7f + at[i] * beat
                l += Delayed(t, NoteVoice(Inst.BRASS, Dsp.midi(line[i]), len[i] * beat, 0.32f))
                l += Delayed(t, NoteVoice(Inst.BELL, Dsp.midi(line[i] + 12f), len[i] * beat, 0.1f))
            }
            val end = 0.7f + 6f * beat
            for (n in floatArrayOf(60f, 64f, 67f, 72f)) l += Delayed(end, NoteVoice(Inst.BRASS, Dsp.midi(n), 1.2f, 0.12f))
            l += Delayed(end, DrumVoice(Drum.CRASH, 0.8f)); l += Delayed(end, DrumVoice(Drum.KICK, 0.9f))
            l += Delayed(end + 0.3f, Crackle(1.5f, 0.6f, 77))
        } else {
            // 2nd place and lower: a cheerful "well done" on mallets
            val line = floatArrayOf(72f, 76f, 79f, 84f)
            for ((i, n) in line.withIndex()) {
                l += Delayed(i * beat, NoteVoice(Inst.MARIMBA, Dsp.midi(n), beat, 0.35f))
                l += Delayed(i * beat, NoteVoice(Inst.MUSICBOX, Dsp.midi(n + 12f), beat, 0.12f))
            }
            l += Delayed(4 * beat, DrumVoice(Drum.TAMB, 0.6f)); l += Delayed(4 * beat, NoteVoice(Inst.BELL, Dsp.midi(84f), 0.8f, 0.2f))
        }
        return l
    }

    /** Seconds until the fanfare is done and the victory music can start. */
    fun fanfareLength(first: Boolean) = if (first) 2.4f else 1.3f
}
