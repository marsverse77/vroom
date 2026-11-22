package com.vroombuddies.game

import com.vroombuddies.game.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SimTest {
    private val tires = KnockKind("tires", 1.1f, 0.76f, 0.5f, 3, 6)
    private val cone = KnockKind("cone", 0.7f, 1.6f, 0.2f, 1, 1, row = 5)

    private fun race(seed: Int = 11, gravity: Float = 30f) = Race(
        TrackPath(TrackShape(seed, jumps = 3)), gravity, 2, true, listOf(tires, cone),
        VehicleParams(), 0, 0, List(5) { it + 1 to VehicleParams(mass = 0.9f + it * 0.1f) }, listOf(1, 2, 3), seed,
    )

    @Test fun trackIsLongAndClosed() {
        val t = TrackPath(TrackShape(11))
        assertTrue("track ${t.length} m", t.length in 2500f..6000f)
        val a = t.frame(0f); val b = t.frame(t.length - 0.01f)
        assertEquals(a.x, b.x, 3f); assertEquals(a.z, b.z, 3f)
        assertTrue(t.pads.size > 8)
        assertTrue(t.knockSpots.size > 10)
        val loc = FloatArray(2)
        val p = FloatArray(3)
        t.pointAt(500f, 5f, p)
        t.locate(p[0], p[2], 490f, loc)
        assertEquals(500f, loc[0], 1f); assertEquals(5f, loc[1], 0.3f)
    }

    @Test fun handsOffRaceFinishesInsideWalls() {
        val r = race()
        var t = 0f
        var maxX = 0f; var launches = 0; var propHits = 0; var resets = 0
        while (r.phase != RacePhase.FINISHED && t < 400f) {
            r.steerInput = 0f
            if ((t * 10).toInt() % 50 == 0) r.boostPressed = true
            r.update(1 / 60f); t += 1 / 60f
            if ((t*60).toInt() % 600 == 0) { val v = r.playerVehicle; println("t=$t s=${v.progress} x=${v.x} vf=${v.vf} vl=${v.vl} head=${v.heading} boost=${v.boostTimer} ground=${v.onGround}") }
            for (e in r.events) when (e.type) {
                EventType.LAUNCH -> if (e.car === r.playerVehicle) launches++
                EventType.PROP_HIT -> propHits++
                EventType.RESET -> if (e.car === r.playerVehicle) resets++
                else -> {}
            }
            for (rc in r.racers) {
                val v = rc.vehicle
                assertFalse("NaN", v.x.isNaN() || v.progress.isNaN() || v.vf.isNaN())
                maxX = maxOf(maxX, abs(v.x))
            }
        }
        for (rc in r.racers) println("car ${rc.carIndex} player=${rc.isPlayer} place=${rc.place} avg=${rc.vehicle.progress / r.raceTime} finished=${rc.vehicle.finished}")
        println("len=${r.track.length} finished in ${r.raceTime}s place=${r.player.place} launches=$launches propHits=$propHits resets=$resets maxX=$maxX")
        assertEquals(RacePhase.FINISHED, r.phase)
        assertTrue("race time ${r.raceTime}", r.raceTime in 60f..240f)
        assertTrue(maxX < r.track.halfWidth + 0.5f)
        assertTrue("jumps taken $launches", launches >= 3)
        assertTrue("player should not need resets when hands off: $resets", resets <= 1)
    }

    @Test fun steeringHardStaysOnTrackAndRecovers() {
        val r = race(23)
        var t = 0f
        while (r.phase != RacePhase.FINISHED && t < 400f) {
            r.steerInput = if ((t / 3).toInt() % 2 == 0) 1f else -1f
            r.update(1 / 60f); t += 1 / 60f
        }
        println("zigzag finished in ${r.raceTime}s place=${r.player.place}")
        assertEquals(RacePhase.FINISHED, r.phase)
    }

    @Test fun tiresFlyWhenHit() {
        val r = race()
        val body = r.props.bodies.first()
        val v = r.playerVehicle
        val s = body.sHint
        v.placeAt(r.track, s - 30f, r.track.knockSpots.first().x)
        v.vf = 40f
        r.phase = RacePhase.RACING
        var maxY = body.y
        repeat(120) {
            r.steerInput = 0f
            r.update(1 / 60f)
            for (b in r.props.bodies) if (b.stackId == body.stackId) maxY = maxOf(maxY, b.y)
        }
        val moved = r.props.bodies.filter { it.stackId == body.stackId }.count { abs(it.x - it.homeX) + abs(it.z - it.homeZ) > 1f }
        println("stack moved=$moved maxY=$maxY startY=${body.homeY}")
        assertTrue(moved >= 2)
        assertTrue(maxY > body.homeY + 1f)
    }
}
