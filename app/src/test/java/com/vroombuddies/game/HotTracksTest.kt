package com.vroombuddies.game

import com.vroombuddies.game.content.Knock
import com.vroombuddies.game.content.Themes
import com.vroombuddies.game.render.HotScenery
import com.vroombuddies.game.render.MeshData
import com.vroombuddies.game.render.SceneryLibrary
import com.vroombuddies.game.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Track batch A (Hot & dry): track layouts, scenery models and a hands-off race on each track. */
class HotTracksTest {
    private val names = listOf("Desert Canyon", "Sunset Highway", "Volcano Run", "Dino Valley")
    private val themes = names.map { n -> Themes.all.first { it.name == n } }
    /** Ids other batches own; until they land the game skips them. */
    private val foreign = setOf("lamp", "palm")
    /** Big one-off landmarks: few instances, a single cheap LOD. */
    private val landmarks = setOf("mesa", "volcano")

    @Test fun tracksAreLongWithJumpsPadsAndTires() {
        for (t in themes) {
            val p = TrackPath(t.shape)
            println("${t.name}: ${p.length.toInt()} m, jumps=${p.jumps.size}, pads=${p.pads.size}, tire spots=${p.knockSpots.size}")
            assertTrue("${t.name} length ${p.length}", p.length in 3500f..7000f)
            assertTrue("${t.name} jumps ${p.jumps.size}", p.jumps.size >= 4)
            assertTrue("${t.name} pads", p.pads.size >= 14)
            assertTrue("${t.name} tire spots", p.knockSpots.size >= 20)
        }
    }

    @Test fun everySceneryIdHasABuilder() {
        for (t in themes) for (r in t.props) if (r.id !in foreign)
            assertNotNull("${t.name} wants '${r.id}'", SceneryLibrary.builders[r.id])
    }

    private fun tris(m: MeshData) = m.iCount / 3

    private fun windingErrors(m: MeshData): Int {
        var bad = 0
        for (t in 0 until m.iCount / 3) {
            val a = m.idx[t * 3]; val b = m.idx[t * 3 + 1]; val c = m.idx[t * 3 + 2]
            val ux = m.pos[b * 3] - m.pos[a * 3]; val uy = m.pos[b * 3 + 1] - m.pos[a * 3 + 1]; val uz = m.pos[b * 3 + 2] - m.pos[a * 3 + 2]
            val vx = m.pos[c * 3] - m.pos[a * 3]; val vy = m.pos[c * 3 + 1] - m.pos[a * 3 + 1]; val vz = m.pos[c * 3 + 2] - m.pos[a * 3 + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            if (nx * nx + ny * ny + nz * nz < 1e-8f * (ux * ux + uy * uy + uz * uz) * (vx * vx + vy * vy + vz * vz)) continue  // sliver at a sphere pole
            val sx = m.nrm[a * 3] + m.nrm[b * 3] + m.nrm[c * 3]
            val sy = m.nrm[a * 3 + 1] + m.nrm[b * 3 + 1] + m.nrm[c * 3 + 1]
            val sz = m.nrm[a * 3 + 2] + m.nrm[b * 3 + 2] + m.nrm[c * 3 + 2]
            if (nx * sx + ny * sy + nz * sz < 0) bad++
        }
        return bad
    }

    @Test fun modelsAreCheapAndWoundOutward() {
        for ((id, build) in HotScenery.pack) {
            val near = build(0); val far = build(1)
            val glow = near.glow?.let { tris(it) } ?: 0
            println("$id: near=${tris(near.lit)} far=${tris(far.lit)} glow=$glow")
            assertTrue("$id is empty", tris(near.lit) > 0)
            if (id !in landmarks) {
                assertTrue("$id near LOD ${tris(near.lit)} > 340", tris(near.lit) <= 340)
                assertTrue("$id glow $glow > 120", glow <= 120)
            }
            assertTrue("$id far LOD heavier than near", tris(far.lit) <= tris(near.lit))
            assertEquals("$id lit winding", 0, windingErrors(near.lit))
            near.glow?.let { assertEquals("$id glow winding", 0, windingErrors(it)) }
            val b = near.lit.bounds()
            assertTrue("$id sinks below the ground (${b[1]})", b[1] > -1f)  // rocks may sit a little buried
        }
    }

    @Test fun handsOffRaceFinishesOnEveryTrack() {
        for (t in themes) {
            val track = TrackPath(t.shape)
            val r = Race(track, t.gravity, 2, t.ground != null, Knock.kinds(t.knock),
                VehicleParams().let { it.copy(grip = it.grip * t.grip / 2f) }, 0, 0,
                List(5) { it + 1 to VehicleParams(mass = 0.9f + it * 0.1f) }, listOf(1, 2, 3, 4, 5), t.shape.seed)
            var time = 0f; var maxX = 0f; var launches = 0
            while (r.phase != RacePhase.FINISHED && time < 600f) {
                r.steerInput = 0f
                if ((time * 10).toInt() % 50 == 0) r.boostPressed = true
                r.update(1 / 60f); time += 1 / 60f
                for (e in r.events) if (e.type == EventType.LAUNCH && e.car === r.playerVehicle) launches++
                for (rc in r.racers) { val v = rc.vehicle; assertFalse("NaN", v.x.isNaN() || v.progress.isNaN()); maxX = maxOf(maxX, abs(v.x)) }
            }
            println("${t.name}: finished in ${r.raceTime.toInt()} s, place ${r.player.place}, jumps taken $launches, maxX $maxX")
            assertEquals("${t.name} finished", RacePhase.FINISHED, r.phase)
            assertTrue("${t.name} left the road ($maxX)", maxX < track.halfWidth + 0.5f)
            assertTrue("${t.name} jumps taken $launches", launches >= 6)
        }
    }
}
