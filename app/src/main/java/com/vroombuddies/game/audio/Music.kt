package com.vroombuddies.game.audio

/** Drum and bass feel of a song. */
internal enum class Groove { ROCK, DISCO, BOUNCE, ISLAND, SPACE, MARCH, SHUFFLE, JUNGLE }

/**
 * Recipe for one original, procedurally composed song. The same recipe always gives the same tune
 * (it is seeded), so each track keeps its own theme song.
 */
internal class Style(
    val bpm: Float,
    val root: Int,                 // MIDI note of the key's tonic (the lead plays an octave or so above)
    val minor: Boolean,
    val prog: IntArray,            // 8 chords as scale degrees: 4 for the A section, 4 for the B section
    val lead: Inst,
    val bass: Inst,
    val chords: Inst?,
    val arp: Inst?,
    val groove: Groove,
    val seed: Int,
    val swing: Float = 0f,
    val counter: Inst? = null,     // second melody that answers the lead in the B section
)

internal object Songs {
    private val P1 = intArrayOf(0, 4, 5, 3, 3, 4, 0, 4)   // I V vi IV | IV V I V
    private val P2 = intArrayOf(0, 3, 4, 3, 5, 3, 0, 4)   // I IV V IV | vi IV I V
    private val P3 = intArrayOf(0, 5, 3, 4, 3, 0, 3, 4)   // I vi IV V | IV I IV V
    private val P4 = intArrayOf(0, 3, 0, 4, 5, 3, 1, 4)   // I IV I V | vi IV ii V
    private val PM = intArrayOf(0, 5, 2, 6, 0, 5, 3, 4)   // minor: i VI III VII | i VI iv v
    private val PX = intArrayOf(0, 6, 3, 0, 0, 6, 4, 4)   // mixolydian-ish: I bVII IV I

    val menu = Style(150f, 60, false, P1, Inst.SQUARE, Inst.BASS_SQ, Inst.STAB, Inst.MUSICBOX, Groove.BOUNCE, 101, counter = Inst.BELL)
    val victory = Style(156f, 62, false, P3, Inst.BRASS, Inst.BASS_SQ, Inst.STAB, Inst.BELL, Groove.DISCO, 202, counter = Inst.SQUARE)

    private val byTheme: Map<String, Style> = mapOf(
        "Sunny Meadow" to Style(146f, 62, false, P1, Inst.FLUTE, Inst.BASS_SQ, Inst.STAB, null, Groove.BOUNCE, 1, counter = Inst.MARIMBA),
        "Desert Canyon" to Style(138f, 57, false, PX, Inst.PLUCK, Inst.BASS_PLUCK, null, null, Groove.SHUFFLE, 2, swing = 0.3f, counter = Inst.FLUTE),
        "Space Station" to Style(132f, 57, true, PM, Inst.BELL, Inst.BASS_SUB, null, Inst.SQUARE, Groove.SPACE, 3, counter = Inst.SAW),
        "Neon City" to Style(128f, 57, true, PM, Inst.SAW, Inst.BASS_SAW, Inst.STAB, Inst.BELL, Groove.DISCO, 4),
        "Snowy Peaks" to Style(140f, 64, false, P2, Inst.BELL, Inst.BASS_SUB, Inst.STAB, Inst.MUSICBOX, Groove.BOUNCE, 5, counter = Inst.FLUTE),
        "Sunny Beach" to Style(124f, 60, false, P4, Inst.STEEL, Inst.BASS_SUB, Inst.STAB, null, Groove.ISLAND, 6, swing = 0.18f, counter = Inst.MARIMBA),
        "Jungle Jam" to Style(136f, 62, false, P3, Inst.MARIMBA, Inst.BASS_PLUCK, null, null, Groove.JUNGLE, 7, counter = Inst.FLUTE),
        "Volcano Run" to Style(152f, 52, true, PM, Inst.SAW, Inst.BASS_SAW, Inst.STAB, null, Groove.ROCK, 8, counter = Inst.BRASS),
        "Sweet Treats" to Style(150f, 65, false, P1, Inst.ORGAN, Inst.BASS_SQ, null, Inst.MUSICBOX, Groove.BOUNCE, 9, counter = Inst.BELL),
        "Moon Base" to Style(118f, 55, true, PM, Inst.BELL, Inst.BASS_SUB, null, Inst.MUSICBOX, Groove.SPACE, 10, counter = Inst.FLUTE),
        "Autumn Woods" to Style(132f, 60, false, P2, Inst.FLUTE, Inst.BASS_PLUCK, null, Inst.PLUCK, Groove.SHUFFLE, 11, swing = 0.25f, counter = Inst.MARIMBA),
        "Rainbow Sky" to Style(154f, 64, false, P1, Inst.MUSICBOX, Inst.BASS_SQ, Inst.STAB, Inst.BELL, Groove.DISCO, 12, counter = Inst.SQUARE),
        "Frozen Lake" to Style(138f, 62, false, P3, Inst.BELL, Inst.BASS_SUB, Inst.STAB, null, Groove.MARCH, 13, counter = Inst.FLUTE),
        "Pirate Cove" to Style(144f, 57, true, PM, Inst.PLUCK, Inst.BASS_SQ, Inst.STAB, null, Groove.MARCH, 14, swing = 0.15f, counter = Inst.ORGAN),
        "Dino Valley" to Style(140f, 55, false, PX, Inst.MARIMBA, Inst.BASS_SAW, null, null, Groove.JUNGLE, 15, counter = Inst.BRASS),
        "Toy Town" to Style(148f, 60, false, P4, Inst.MUSICBOX, Inst.BASS_SQ, Inst.STAB, null, Groove.MARCH, 16, counter = Inst.ORGAN),
        "Sunset Highway" to Style(142f, 59, false, P2, Inst.PLUCK, Inst.BASS_SAW, Inst.STAB, null, Groove.ROCK, 17, counter = Inst.SAW),
        "Crystal Canyon" to Style(134f, 61, true, PM, Inst.BELL, Inst.BASS_SAW, null, Inst.MUSICBOX, Groove.SPACE, 18, counter = Inst.SQUARE),
        "Bubble Reef" to Style(126f, 63, false, P3, Inst.STEEL, Inst.BASS_SUB, null, Inst.BELL, Groove.ISLAND, 19, swing = 0.2f, counter = Inst.FLUTE),
        "Galaxy Grand Prix" to Style(156f, 58, true, PM, Inst.SQUARE, Inst.BASS_SAW, Inst.STAB, Inst.BELL, Groove.DISCO, 20, counter = Inst.BRASS),
    )

    private val fallbacks = byTheme.values.toList()

    /** The theme's song, or a stable pick for a theme that has no song of its own yet. */
    fun forTheme(name: String): Style = byTheme[name] ?: fallbacks[Math.floorMod(name.hashCode(), fallbacks.size)]
}

/** A scheduled note or drum hit. */
internal class Ev(val step: Int, val inst: Inst?, val drum: Drum?, val midi: Int, val lenSteps: Float, val vol: Float)

/** A composed loop: 16 bars of 16 sixteenth-note steps. */
internal class Song(val style: Style, val events: List<Ev>) {
    val steps = 16 * BARS
    companion object { const val BARS = 16 }
}

/** Writes catchy, repetitive kid-friendly tunes from a [Style]: A A' B B' with call-and-response phrases. */
internal object Composer {
    private val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)

    // Melody rhythms: pairs of (start step, length in steps) inside one bar.
    private val RHYTHMS = arrayOf(
        intArrayOf(0, 2, 2, 2, 4, 4, 8, 2, 10, 2, 12, 4),
        intArrayOf(0, 3, 3, 3, 6, 2, 8, 4, 12, 2, 14, 2),
        intArrayOf(0, 2, 2, 1, 3, 1, 4, 2, 6, 2, 8, 6, 14, 2),
        intArrayOf(0, 4, 4, 4, 8, 4, 12, 4),
        intArrayOf(0, 1, 1, 1, 2, 2, 4, 2, 6, 2, 8, 2, 10, 2, 12, 4),
        intArrayOf(0, 2, 2, 2, 4, 2, 6, 2, 8, 8),
        intArrayOf(2, 2, 4, 2, 6, 2, 8, 3, 11, 3, 14, 2),
    )
    private val CADENCE = intArrayOf(0, 4, 4, 4, 8, 8)

    fun compose(st: Style): Song {
        val rng = Rng(st.seed * 92821 + 17)
        val scale = if (st.minor) MINOR else MAJOR
        fun note(deg: Int, base: Int): Int { val o = Math.floorDiv(deg, 7); return base + o * 12 + scale[Math.floorMod(deg, 7)] }
        fun chordTones(chord: Int) = intArrayOf(chord, chord + 2, chord + 4)
        val ev = ArrayList<Ev>()
        val leadBase = st.root + 12
        val chordAt = IntArray(Song.BARS) { b -> st.prog[(if (b < 8) 0 else 4) + b % 4] }

        // ---- melody: write a 4-bar phrase per section, then repeat it with a new ending
        fun nearestChordDeg(pos: Int, chord: Int): Int {
            var best = pos; var bd = 99
            for (c in chordTones(chord)) for (o in -2..2) { val d = c + o * 7; val dist = kotlin.math.abs(d - pos); if (dist < bd) { bd = dist; best = d } }
            return best
        }
        fun nearestDeg(pos: Int, target: Int): Int {
            var best = target; for (o in -2..2) { val d = target + o * 7; if (kotlin.math.abs(d - pos) < kotlin.math.abs(best - pos)) best = d }
            return best
        }
        fun phrase(chords: IntArray, startPos: Int, finalDeg: Int): List<Triple<Int, Int, Int>> { // (stepInPhrase, degree, len)
            val out = ArrayList<Triple<Int, Int, Int>>()
            val r1 = RHYTHMS[rng.int(RHYTHMS.size)]
            var r2 = RHYTHMS[rng.int(RHYTHMS.size)]
            if (r2 === r1) r2 = RHYTHMS[(RHYTHMS.indexOf(r1) + 3) % RHYTHMS.size]
            var pos = startPos
            var dir = if (rng.chance(0.5f)) 1 else -1
            val motif = ArrayList<Int>() // intervals of bar 0, reused in bar 2
            for (bar in 0 until 4) {
                val chord = chords[bar]
                val rh = when (bar) { 0, 2 -> r1; 1 -> r2; else -> CADENCE }
                var k = 0
                while (k < rh.size) {
                    val st0 = rh[k]; val len = rh[k + 1]; val idx = k / 2
                    val strong = st0 % 8 == 0
                    if (bar == 2 && idx < motif.size) {
                        pos = if (idx == 0) nearestChordDeg(pos + motif[0], chord) else pos + motif[idx]
                    } else if (bar == 3 && idx == rh.size / 2 - 1) {
                        pos = nearestDeg(pos, finalDeg)
                    } else {
                        val prev = pos
                        pos = if (strong) nearestChordDeg(pos + dir * rng.int(2), chord)
                        else pos + dir * (1 + if (rng.chance(0.25f)) 1 else 0)
                        if (rng.chance(0.2f)) dir = -dir
                        if (bar == 0) motif.add(pos - prev)
                    }
                    if (pos > 11) { pos -= 2; dir = -1 }
                    if (pos < 0) { pos += 2; dir = 1 }
                    out.add(Triple(bar * 16 + st0, pos, len))
                    k += 2
                }
            }
            return out
        }

        val aChords = IntArray(4) { st.prog[it] }; val bChords = IntArray(4) { st.prog[4 + it] }
        val aPhrase = phrase(aChords, 4, st.prog[3].let { if (it == 4) 4 else 2 })
        val aEnd = phrase(aChords, 4, 0)
        val bPhrase = phrase(bChords, 7, 4)
        val bEnd = phrase(bChords, 7, 4)
        fun put(ph: List<Triple<Int, Int, Int>>, barOffset: Int, onlyBars: IntRange, inst: Inst, vol: Float, octave: Int = 0) {
            for ((s, d, l) in ph) { val bar = s / 16; if (bar in onlyBars) ev.add(Ev(barOffset * 16 + s, inst, null, note(d, leadBase) + octave, l * 0.9f, vol)) }
        }
        put(aPhrase, 0, 0..3, st.lead, 0.32f)
        put(aPhrase, 4, 0..2, st.lead, 0.32f); put(aEnd, 4, 3..3, st.lead, 0.32f)
        put(bPhrase, 8, 0..3, st.lead, 0.3f)
        put(aPhrase, 12, 0..1, st.lead, 0.3f); put(bEnd, 12, 2..3, st.lead, 0.3f)
        // counter-melody answers the lead an octave up, in the gaps of the B section
        st.counter?.let { c ->
            for ((s, d, l) in bPhrase) if (s % 16 >= 8) ev.add(Ev(8 * 16 + 4 * 16 + s, c, null, note(d + 2, leadBase) + 12, l * 0.8f, 0.14f))
            for ((s, d, l) in aPhrase) if (s / 16 == 1) ev.add(Ev(4 * 16 + s, c, null, note(d + 4, leadBase), l * 0.8f, 0.12f))
        }

        // ---- bass, chords, arpeggio
        val bassBase = st.root - 12
        for (bar in 0 until Song.BARS) {
            val ch = chordAt[bar]; val o = bar * 16
            val r = note(ch, bassBase); val fifth = note(ch + 4, bassBase); val oct = r + 12
            val pat: List<Pair<Int, Int>> = when (st.groove) {  // (step, midi)
                Groove.ROCK -> (0 until 16 step 2).map { it to (if (it == 12) fifth else r) }
                Groove.DISCO -> (0 until 16 step 2).map { it to (if (it % 4 == 2) oct else r) }
                Groove.BOUNCE -> listOf(0 to r, 4 to r, 6 to fifth, 8 to r, 12 to fifth, 14 to oct)
                Groove.ISLAND -> listOf(0 to r, 3 to r, 6 to fifth, 8 to r, 11 to fifth, 14 to oct)
                Groove.SPACE -> listOf(0 to r, 6 to r, 8 to fifth, 14 to oct)
                Groove.MARCH -> listOf(0 to r, 4 to fifth, 8 to r, 12 to fifth)
                Groove.SHUFFLE -> listOf(0 to r, 4 to fifth, 6 to oct, 8 to r, 12 to fifth, 14 to note(ch + 5, bassBase))
                Groove.JUNGLE -> listOf(0 to r, 3 to r, 6 to oct, 8 to fifth, 10 to r, 14 to fifth)
            }
            for ((i, p) in pat.withIndex()) {
                val next = if (i + 1 < pat.size) pat[i + 1].first else 16
                ev.add(Ev(o + p.first, st.bass, null, p.second, (next - p.first) * 0.8f, 0.42f))
            }
            st.chords?.let { ci ->
                val tones = chordTones(ch).map { note(it, st.root) }
                val hits = when (st.groove) {
                    Groove.ISLAND, Groove.BOUNCE, Groove.JUNGLE -> listOf(2, 6, 10, 14)
                    Groove.DISCO -> listOf(2, 6, 10, 14)
                    Groove.ROCK -> listOf(0, 3, 8, 11)
                    Groove.MARCH -> listOf(0, 4, 8, 12)
                    else -> listOf(0, 8)
                }
                for (h in hits) for (tn in tones) ev.add(Ev(o + h, ci, null, tn, 1.5f, 0.07f))
            }
            st.arp?.let { ai ->
                if (bar >= 4) { // arpeggio joins on the second time round, so the song builds
                    val tones = chordTones(ch).map { note(it, st.root + 12) } + note(ch + 7, st.root + 12)
                    for (k in 0 until 16 step 2) ev.add(Ev(o + k, ai, null, tones[(k / 2) % tones.size], 1.6f, 0.07f))
                }
            }
        }

        // ---- drums
        for (bar in 0 until Song.BARS) {
            val o = bar * 16
            fun d(step: Int, k: Drum, v: Float) = ev.add(Ev(o + step, null, k, 0, 0f, v))
            val fill = bar == 7 || bar == 15
            if (bar == 0 || bar == 8) d(0, Drum.CRASH, 0.5f)
            when (st.groove) {
                Groove.ROCK -> { d(0, Drum.KICK, 0.9f); d(8, Drum.KICK, 0.9f); d(10, Drum.KICK, 0.6f); d(4, Drum.SNARE, 0.7f); if (!fill) d(12, Drum.SNARE, 0.7f); for (k in 0 until 16 step 2) d(k, Drum.HAT, 0.5f) }
                Groove.DISCO -> { for (k in 0 until 16 step 4) d(k, Drum.KICK, 0.9f); d(4, Drum.CLAP, 0.6f); if (!fill) d(12, Drum.CLAP, 0.6f); for (k in 2 until 16 step 4) d(k, Drum.OPEN_HAT, 0.5f); for (k in 0 until 16) if (k % 2 == 1) d(k, Drum.HAT, 0.3f) }
                Groove.BOUNCE -> { d(0, Drum.KICK, 0.9f); d(6, Drum.KICK, 0.6f); d(8, Drum.KICK, 0.8f); d(4, Drum.SNARE, 0.65f); if (!fill) d(12, Drum.SNARE, 0.65f); for (k in 0 until 16 step 2) d(k, Drum.HAT, 0.4f); d(14, Drum.TAMB, 0.5f) }
                Groove.ISLAND -> { d(0, Drum.KICK, 0.7f); d(8, Drum.KICK, 0.9f); d(4, Drum.WOOD, 0.4f); d(12, Drum.WOOD, 0.4f); for (k in 0 until 16) d(k, Drum.SHAKER, if (k % 2 == 0) 0.45f else 0.25f) }
                Groove.SPACE -> { d(0, Drum.KICK, 0.9f); d(10, Drum.KICK, 0.6f); d(4, Drum.CLAP, 0.45f); if (!fill) d(12, Drum.CLAP, 0.45f); for (k in 0 until 16) d(k, Drum.SHAKER, if (k % 4 == 2) 0.45f else 0.18f) }
                Groove.MARCH -> { d(0, Drum.KICK, 0.9f); d(8, Drum.KICK, 0.9f); d(4, Drum.SNARE, 0.6f); if (!fill) { d(12, Drum.SNARE, 0.6f); d(14, Drum.SNARE, 0.3f); d(15, Drum.SNARE, 0.35f) }; d(2, Drum.WOOD, 0.35f); d(10, Drum.WOOD, 0.35f) }
                Groove.SHUFFLE -> { d(0, Drum.KICK, 0.85f); d(8, Drum.KICK, 0.85f); d(4, Drum.SNARE, 0.6f); if (!fill) d(12, Drum.SNARE, 0.6f); for (k in 0 until 16 step 2) d(k, Drum.HAT, 0.4f); d(10, Drum.TAMB, 0.35f) }
                Groove.JUNGLE -> { d(0, Drum.KICK, 0.85f); d(7, Drum.KICK, 0.5f); d(8, Drum.KICK, 0.7f); d(4, Drum.TOM, 0.55f); d(11, Drum.TOM, 0.45f); if (!fill) d(12, Drum.WOOD, 0.45f); for (k in 0 until 16 step 2) d(k, Drum.SHAKER, 0.4f); d(6, Drum.WOOD, 0.3f) }
            }
            if (fill) { d(12, Drum.TOM, 0.6f); d(13, Drum.SNARE, 0.5f); d(14, Drum.TOM, 0.6f); d(15, Drum.SNARE, 0.6f) }
        }
        ev.sortBy { it.step }
        return Song(st, ev)
    }
}

/**
 * Plays [Song]s in a loop with crossfades. Runs inside the audio thread; [next] is called once per sample.
 */
internal class MusicPlayer {
    private val voices = ArrayList<Voice>(48)
    private var song: Song? = null
    private var stepLen = 1.0      // samples per 16th
    private var pos = 0.0          // samples into the loop
    private var evIndex = 0
    private var loops = 0
    private var gain = 1f
    private var fadeTarget = 1f
    private var fadeStep = 0f
    private var pending: Song? = null
    private var startDelay = 0

    val playing get() = song != null

    /** Switch to [s] (null = silence) after fading the current song out over [fade] seconds, then waiting [delay] seconds. */
    fun change(s: Song?, fade: Float = 0.6f, delay: Float = 0f) {
        pending = s
        startDelay = (delay * RATE).toInt()
        if (song == null) { fadeStep = 1f; gain = 0f; fadeTarget = 0f } else { fadeTarget = 0f; fadeStep = DT / fade.coerceAtLeast(0.01f) }
    }

    fun stopNow() { song = null; pending = null; voices.clear() }

    private fun start(s: Song) {
        song = s; pos = 0.0; evIndex = 0; loops = 0
        stepLen = RATE * 60.0 / s.style.bpm / 4.0
        gain = 1f; fadeTarget = 1f
    }

    fun next(): Float {
        // fade handling
        if (gain > fadeTarget) gain = maxOf(fadeTarget, gain - fadeStep)
        if (gain <= 0f && fadeTarget == 0f) {
            if (song != null) { song = null; voices.clear() }
            val p = pending
            if (p != null) { if (startDelay > 0) startDelay-- else { pending = null; start(p) } }
        }
        val s = song
        if (s != null) {
            val evs = s.events
            while (evIndex < evs.size) {
                val e = evs[evIndex]
                val swingOff = when { e.step % 4 == 2 -> s.style.swing * stepLen; e.step % 2 == 1 -> s.style.swing * 0.5 * stepLen; else -> 0.0 }
                if (e.step * stepLen + swingOff > pos) break
                trigger(e, s)
                evIndex++
            }
            pos += 1.0
            if (pos >= s.steps * stepLen) { pos -= s.steps * stepLen; evIndex = 0; loops++ }
        }
        var out = 0f
        var i = 0
        while (i < voices.size) {
            val v = voices[i]
            out += v.next()
            if (v.done) { voices[i] = voices[voices.size - 1]; voices.removeAt(voices.size - 1) } else i++
        }
        return out * gain
    }

    private fun trigger(e: Ev, s: Song) {
        if (voices.size >= 40) return
        // a little variety on repeats: the lead jumps up an octave on every third pass
        if (e.drum != null) voices.add(DrumVoice(e.drum, e.vol, e.step + loops))
        else if (e.inst != null) {
            val lift = if (e.inst == s.style.lead && loops % 3 == 2 && e.step >= 8 * 16) 12 else 0
            voices.add(NoteVoice(e.inst, Dsp.midi((e.midi + lift).toFloat()), (e.lenSteps * stepLen / RATE).toFloat(), e.vol, e.step))
        }
    }
}
