package com.vroombuddies.game

import com.vroombuddies.game.content.Knock
import com.vroombuddies.game.content.Themes
import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Batch C (water and tropics): scenery models are well formed and cheap, and the four tracks race cleanly. */
class TropicsTest {
    private val names = listOf("Sunny Beach", "Jungle Jam", "Pirate Cove", "Bubble Reef")

    private fun backwards(m: MeshData): Int {
        var bad = 0
        for (t in 0 until m.iCount / 3) {
            val a = m.idx[t * 3]; val b = m.idx[t * 3 + 1]; val c = m.idx[t * 3 + 2]
            val ux = m.pos[b * 3] - m.pos[a * 3]; val uy = m.pos[b * 3 + 1] - m.pos[a * 3 + 1]; val uz = m.pos[b * 3 + 2] - m.pos[a * 3 + 2]
            val vx = m.pos[c * 3] - m.pos[a * 3]; val vy = m.pos[c * 3 + 1] - m.pos[a * 3 + 1]; val vz = m.pos[c * 3 + 2] - m.pos[a * 3 + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            if (nx * nx + ny * ny + nz * nz < 1e-12f) continue
            val sx = m.nrm[a * 3] + m.nrm[b * 3] + m.nrm[c * 3]
            val sy = m.nrm[a * 3 + 1] + m.nrm[b * 3 + 1] + m.nrm[c * 3 + 1]
            val sz = m.nrm[a * 3 + 2] + m.nrm[b * 3 + 2] + m.nrm[c * 3 + 2]
            if (nx * sx + ny * sy + nz * sz < 0) bad++
        }
        return bad
    }

    @Test fun spherePatchWinding() {
        val m = MeshData()
        TropicsScenery.spherePatch(m, 1f, 0.3f, 1.4f, Rgba.WHITE, ws = 3, hs = 6)
        assertEquals(0, backwards(m))
    }

    @Test fun sceneryIsWellFormedAndCheap() {
        val bigSetPieces = setOf("pirateShip", "sunkenShip", "islet", "jungleRuin", "sailboat", "lifeguardTower", "tikiTotem", "sandcastle")
        for ((id, build) in TropicsScenery.pack) {
            val near = build(0); val far = build(1)
            val nt = near.lit.iCount / 3; val ft = far.lit.iCount / 3
            println("$id near=$nt far=$ft glow=${(near.glow?.iCount ?: 0) / 3}")
            assertEquals("$id near winding", 0, backwards(near.lit))
            assertEquals("$id far winding", 0, backwards(far.lit))
            assertTrue("$id far LOD should be cheaper ($ft vs $nt)", ft <= nt)
            val budget = if (id in bigSetPieces) 1000 else 360
            assertTrue("$id near has $nt triangles", nt <= budget)
            for (i in 0 until near.lit.vCount * 3) assertFalse("$id NaN", near.lit.pos[i].isNaN())
        }
    }

    @Test fun everySceneryIdHasABuilder() {
        val known = TropicsScenery.pack.keys + setOf("roundTree", "pine", "flower", "bush", "rock", "windmill", "balloon", "mountain", "grandstand", "flagRow", "fern")
        for (t in Themes.all.filter { it.name in names }) for (r in t.props) assertTrue("${t.name}: ${r.id}", r.id in known)
    }

    @Test fun tropicalTracksRaceCleanly() {
        for (t in Themes.all.filter { it.name in names }) {
            val track = TrackPath(t.shape)
            assertTrue("${t.name} length ${track.length}", track.length in 3000f..7000f)
            assertTrue("${t.name} jumps ${track.jumps.size}", track.jumps.size >= 3)
            assertTrue(track.pads.size > 10); assertTrue(track.knockSpots.size > 15)
            val r = Race(track, t.gravity, 2, t.ground != null, Knock.kinds(t.knock), VehicleParams(grip = VehicleParams().grip * t.grip / 2f), 0, 0,
                List(5) { it + 1 to VehicleParams(grip = VehicleParams().grip * t.grip / 2f) }, listOf(1, 2, 3), t.shape.seed)
            var time = 0f; var maxX = 0f; var resets = 0
            while (r.phase != RacePhase.FINISHED && time < 500f) {
                r.steerInput = 0f
                if ((time * 10).toInt() % 50 == 0) r.boostPressed = true
                r.update(1 / 60f); time += 1 / 60f
                for (e in r.events) if (e.type == EventType.RESET && e.car === r.playerVehicle) resets++
                for (rc in r.racers) maxX = maxOf(maxX, abs(rc.vehicle.x))
            }
            println("${t.name}: len=${track.length} jumps=${track.jumps.size} pads=${track.pads.size} tires=${track.knockSpots.size} time=${r.raceTime} resets=$resets")
            assertEquals(t.name, RacePhase.FINISHED, r.phase)
            assertTrue("${t.name} race ${r.raceTime}s", r.raceTime in 60f..300f)
            assertTrue("${t.name} stayed inside walls", maxX < track.halfWidth + 0.5f)
            assertTrue("${t.name} resets $resets", resets <= 1)
        }
    }
}
