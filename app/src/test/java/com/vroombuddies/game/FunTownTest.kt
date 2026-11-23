package com.vroombuddies.game

import com.vroombuddies.game.content.Knock
import com.vroombuddies.game.content.Themes
import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Track batch E (Neon City, Sweet Treats, Toy Town, Rainbow Sky): scenery models and full races. */
class FunTownTest {
    private val themes = listOf("Neon City", "Sweet Treats", "Toy Town", "Rainbow Sky").map { n -> Themes.all.first { it.name == n } }

    private fun backwards(m: MeshData): Int {
        var bad = 0
        for (t in 0 until m.iCount / 3) {
            val a = m.idx[t * 3]; val b = m.idx[t * 3 + 1]; val c = m.idx[t * 3 + 2]
            val ux = m.pos[b * 3] - m.pos[a * 3]; val uy = m.pos[b * 3 + 1] - m.pos[a * 3 + 1]; val uz = m.pos[b * 3 + 2] - m.pos[a * 3 + 2]
            val vx = m.pos[c * 3] - m.pos[a * 3]; val vy = m.pos[c * 3 + 1] - m.pos[a * 3 + 1]; val vz = m.pos[c * 3 + 2] - m.pos[a * 3 + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            if (Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()) < 1e-4) continue   // skip invisible slivers at sphere poles
            val sx = m.nrm[a * 3] + m.nrm[b * 3] + m.nrm[c * 3]; val sy = m.nrm[a * 3 + 1] + m.nrm[b * 3 + 1] + m.nrm[c * 3 + 1]; val sz = m.nrm[a * 3 + 2] + m.nrm[b * 3 + 2] + m.nrm[c * 3 + 2]
            if (nx * sx + ny * sy + nz * sz < 0) bad++
        }
        return bad
    }

    @Test fun everySceneryIdIsBuiltAndCheap() {
        for (th in themes) for (r in th.props) assertNotNull("${th.name} needs ${r.id}", SceneryLibrary.builders[r.id])
        for ((id, b) in FunTownScenery.pack) {
            val hi = b(0); val lo = b(1)
            for ((name, m) in listOf("near" to hi.lit, "far" to lo.lit, "glow" to (hi.glow ?: MeshData()))) assertEquals("$id $name winding", 0, backwards(m))
            val nearT = hi.lit.iCount / 3; val farT = lo.lit.iCount / 3; val glowT = (hi.glow?.iCount ?: 0) / 3
            println("%-12s near=%4d far=%4d glow=%4d".format(id, nearT, farT, glowT))
            assertTrue("$id far LOD should be simpler", farT <= nearT)
        }
        // whole-track budget: near batches + glow, if every prop were in view at once
        for (th in themes) {
            var near = 0L; var glow = 0L
            for (r in th.props) { val m = SceneryLibrary.builders[r.id]!!(0); near += r.count.toLong() * m.lit.iCount / 3; glow += r.count.toLong() * ((m.glow?.iCount ?: 0) / 3) }
            println("${th.name}: scenery near tris=$near glow tris=$glow")
            assertTrue("${th.name} scenery too heavy: $near", near < 600_000)
        }
    }

    @Test fun tracksAreLongAndRaceable() {
        for (th in themes) {
            val track = TrackPath(th.shape)
            val race = Race(track, th.gravity, 2, th.ground != null, Knock.kinds(th.knock), VehicleParams(grip = VehicleParams().grip * th.grip / 2f), 0, 0,
                List(5) { it + 1 to VehicleParams(mass = 0.9f + it * 0.1f) }, listOf(1, 2, 3), th.shape.seed)
            var t = 0f; var maxX = 0f; var launches = 0; var resets = 0
            while (race.phase != RacePhase.FINISHED && t < 500f) {
                race.steerInput = 0f
                if ((t * 10).toInt() % 50 == 0) race.boostPressed = true
                race.update(1 / 60f); t += 1 / 60f
                for (e in race.events) when (e.type) {
                    EventType.LAUNCH -> if (e.car === race.playerVehicle) launches++
                    EventType.RESET -> if (e.car === race.playerVehicle) resets++
                    else -> {}
                }
                maxX = maxOf(maxX, abs(race.playerVehicle.x))
            }
            println("${th.name}: lap=${track.length.toInt()} m jumps=${track.jumps.size} pads=${track.pads.size} tireSpots=${track.knockSpots.size} race=${race.raceTime.toInt()} s place=${race.player.place} launches=$launches resets=$resets maxX=$maxX")
            assertTrue("${th.name} lap ${track.length}", track.length in 3500f..6500f)
            assertEquals(th.name, RacePhase.FINISHED, race.phase)
            assertTrue(maxX < track.halfWidth + 0.5f)
            assertTrue("${th.name} resets $resets", resets <= 1)
            assertTrue(track.jumps.size >= 3 && track.pads.size > 12 && track.knockSpots.size > 15)
        }
    }
}
