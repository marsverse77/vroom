package com.vroombuddies.game

import com.vroombuddies.game.content.Themes
import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.TrackPath
import org.junit.Assert.assertTrue
import org.junit.Test

/** Track batch D (Snowy Peaks, Frozen Lake, Autumn Woods): models are valid and cheap, themes resolve, tracks are long. */
class ColdCozySceneryTest {
    private val themes = listOf("Snowy Peaks", "Frozen Lake", "Autumn Woods").map { n -> Themes.all.first { it.name == n } }

    private fun backwards(m: MeshData): Int {
        var bad = 0
        for (t in 0 until m.iCount / 3) {
            val a = m.idx[t * 3]; val b = m.idx[t * 3 + 1]; val c = m.idx[t * 3 + 2]
            val ux = m.pos[b * 3] - m.pos[a * 3]; val uy = m.pos[b * 3 + 1] - m.pos[a * 3 + 1]; val uz = m.pos[b * 3 + 2] - m.pos[a * 3 + 2]
            val vx = m.pos[c * 3] - m.pos[a * 3]; val vy = m.pos[c * 3 + 1] - m.pos[a * 3 + 1]; val vz = m.pos[c * 3 + 2] - m.pos[a * 3 + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            // skip slivers: sphere poles leave hair-thin triangles (float sin(PI) != 0) with no visible area
            if (Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()) < 1e-4) continue
            val sx = m.nrm[a * 3] + m.nrm[b * 3] + m.nrm[c * 3]
            val sy = m.nrm[a * 3 + 1] + m.nrm[b * 3 + 1] + m.nrm[c * 3 + 1]
            val sz = m.nrm[a * 3 + 2] + m.nrm[b * 3 + 2] + m.nrm[c * 3 + 2]
            if (nx * sx + ny * sy + nz * sz < 0) bad++
        }
        return bad
    }

    @Test fun modelsAreValidAndCheap() {
        for ((id, build) in ColdCozyScenery.pack) {
            val near = build(0); val far = build(1)
            val nt = near.lit.iCount / 3; val ft = far.lit.iCount / 3
            println("$id: near $nt tris, far $ft tris")
            assertTrue("$id has no triangles", nt > 0 && ft > 0)
            assertTrue("$id near LOD too heavy ($nt)", nt <= 420)
            assertTrue("$id far LOD ($ft) should be lighter than near ($nt)", ft <= nt)
            assertTrue("$id near has backwards triangles", backwards(near.lit) == 0)
            assertTrue("$id far has backwards triangles", backwards(far.lit) == 0)
            near.glow?.let { assertTrue("$id glow has backwards triangles", backwards(it) == 0) }
        }
    }

    @Test fun everyPropResolves() {
        for (t in themes) for (rule in t.props)
            assertTrue("${t.name} wants '${rule.id}' but nothing builds it", SceneryLibrary.builders.containsKey(rule.id))
    }

    @Test fun tracksAreLongWithJumpsAndPads() {
        for (t in themes) {
            val track = TrackPath(t.shape)
            println("${t.name}: ${track.length.toInt()} m, ${track.jumps.size} jumps, ${track.pads.size} boost pads, ${track.knockSpots.size} tire spots")
            assertTrue("${t.name} too short", track.length > 4000f)
            assertTrue("${t.name} needs jumps", track.jumps.size >= 3)
            assertTrue("${t.name} needs boost pads", track.pads.size >= 6)
            assertTrue("${t.name} needs tire stacks", track.knockSpots.isNotEmpty())
        }
    }
}
