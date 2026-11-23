package com.vroombuddies.game.audio

import kotlin.math.*

/**
 * The player's car: a cartoon engine with gears (it revs up, shifts with a little "brap" and pop), a turbo
 * whine and roar while boosting, free revving in the air, tyre squeal when sliding, and road rumble and wind
 * that grow with speed. Parameters are set from the game thread; [next] runs on the audio thread.
 */
internal class EngineSound {
    @Volatile var on = false
    @Volatile var speed = 0f
    @Volatile var boost = false
    @Volatile var slide = 0f
    @Volatile var grounded = true
    @Volatile var hush = false    // paused: engine fades away

    private val gearTops = floatArrayOf(10f, 19f, 29f, 40f, 53f, 90f)
    private var gear = 0
    private var rpm = 0f           // 0..1 smoothed
    private var level = 0f         // fade in/out
    private var boostLvl = 0f
    private var slideLvl = 0f
    private var speedLvl = 0f
    private var ph = 0f; private var subPh = 0f; private var puttPh = 0f; private var whinePh = 0f; private var squealPh = 0f
    private var lp1 = 0f; private var lp2 = 0f; private var rumble = 0f; private var raspLp = 0f
    private var popT = 1f
    private val windF = Svf(); private val skidF = Svf(); private val roarF = Svf()
    private val rng = Rng(99)
    private var ctl = 0
    private var freq = 40f; private var cut = 0.05f

    private fun control() {
        val k = 64 * DT
        val sp = speed
        // gear from speed, with a little hysteresis so it doesn't flutter at the boundary
        while (gear < gearTops.size - 1 && sp > gearTops[gear]) { gear++; popT = 0f }
        while (gear > 0 && sp < gearTops[gear - 1] * 0.85f) gear--
        val lo = if (gear == 0) 0f else gearTops[gear - 1] * 0.85f
        var target = ((sp - lo) / (gearTops[gear] - lo)).coerceIn(0f, 1f) * 0.75f + 0.18f
        if (gear == 0 && sp < 1f) target = 0.12f
        if (!grounded) target = min(1f, target + 0.3f)
        if (boost) target = min(1f, target + 0.1f)
        val rate = if (target > rpm) 6f else 9f
        rpm += (target - rpm) * (1f - exp(-rate * k))
        val wantLevel = if (on && !hush) 1f else 0f
        level += (wantLevel - level) * (1f - exp(-(if (wantLevel > level) 3f else 6f) * k))
        boostLvl += ((if (boost && on && !hush) 1f else 0f) - boostLvl) * (1f - exp(-(if (boost) 10f else 2.5f) * k))
        val sl = if (grounded && on && !hush) ((slide - 3.5f) / 6f).coerceIn(0f, 1f) else 0f
        slideLvl += (sl - slideLvl) * (1f - exp(-12f * k))
        speedLvl += ((if (on && !hush) (sp / 70f).coerceIn(0f, 1.3f) else 0f) - speedLvl) * (1f - exp(-4f * k))
        freq = 34f + rpm * 118f + boostLvl * 12f
        cut = Dsp.lpK(300f + rpm * 1500f + boostLvl * 900f)
    }

    fun next(): Float {
        if (ctl-- <= 0) { ctl = 64; control() }
        if (level < 0.0005f && speedLvl < 0.001f) return 0f
        val inc = freq * DT
        ph += inc; subPh += inc * 0.5f; puttPh += (5f + rpm * 10f) * DT
        // engine body: saw + half-speed pulse, two low-pass stages that open with revs
        val body = 0.55f * Dsp.saw(ph, inc) + 0.4f * Dsp.pulse(subPh, inc * 0.5f, 0.3f) + 0.35f * Dsp.sin1(subPh)
        lp1 += (body - lp1) * cut; lp2 += (lp1 - lp2) * cut
        // exhaust rasp: noise pulsed by the firing
        val fire = Dsp.sin1(ph).let { if (it > 0f) it * it else 0f }
        raspLp += (rng.bip() * fire - raspLp) * 0.3f
        val putt = 1f + 0.3f * (1f - rpm) * Dsp.sin1(puttPh)
        var s = (lp2 * 0.9f + raspLp * (0.15f + 0.2f * rpm)) * putt * 0.3f * level

        // gear-change pop (cartoon backfire)
        if (popT < 0.09f) { popT += DT; s += rng.bip() * exp(-popT * 60f) * 0.14f * level }

        // boost: turbo whine + low roar
        if (boostLvl > 0.002f) {
            whinePh += (1100f + rpm * 900f) * DT
            roarF.run(rng.bip(), 220f + rpm * 200f, 0.6f)
            s += (Dsp.sin1(whinePh) * 0.035f + roarF.low * 0.3f) * boostLvl
        }
        // tyres: squeal (wobbling tone) + hiss
        if (slideLvl > 0.002f) {
            squealPh += (1150f + 70f * Dsp.sin1(puttPh * 3f)) * DT
            skidF.run(rng.bip(), 2600f, 0.5f)
            s += (Dsp.sin1(squealPh) * 0.05f + skidF.band * 0.12f) * slideLvl
        }
        // road rumble (on the ground) and wind (always)
        if (speedLvl > 0.001f) {
            rumble += (rng.bip() - rumble) * 0.015f
            windF.run(rng.bip(), 900f + speedLvl * 1400f, 0.9f)
            s += (if (grounded) rumble * 0.5f * speedLvl else 0f) + windF.band * 0.06f * speedLvl * speedLvl
        }
        return s
    }
}
