package com.vroombuddies.game

import com.vroombuddies.game.content.Themes
import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.TrackPath
import org.junit.Assert.assertTrue
import org.junit.Test

/** Track batch B: every space scenery model builds, stays cheap, and every id the space themes ask for exists. */
class SpaceSceneryTest {
    private val big = setOf("planet", "station")

    @Test fun modelsBuildWithinBudget() {
        for ((id, b) in SpaceScenery.pack) {
            val near = b(0); val far = b(1)
            val nt = (near.lit.iCount + (near.glow?.iCount ?: 0)) / 3
            val ft = (far.lit.iCount + (far.glow?.iCount ?: 0)) / 3
            assertTrue("$id has no triangles", nt > 0)
            val limit = if (id in big) 2600 else 320
            assertTrue("$id near LOD has $nt triangles (limit $limit)", nt <= limit)
            assertTrue("$id far LOD ($ft) should be simpler than near ($nt)", ft <= nt)
        }
    }

    @Test fun spaceThemesHaveAllTheirScenery() {
        val names = listOf("Space Station", "Moon Base", "Galaxy Grand Prix", "Crystal Canyon")
        for (n in names) {
            val t = Themes.all.first { it.name == n }
            val missing = t.props.map { it.id }.filter { it !in SceneryLibrary.builders }
            assertTrue("$n is missing scenery: $missing", missing.isEmpty())
            val track = TrackPath(t.shape)
            assertTrue("$n should be a long track (${track.length} m)", track.length > 4000f)
            assertTrue("$n should have jumps", track.jumps.size >= 3)
            assertTrue("$n should have boost pads", track.pads.size >= 10)
            assertTrue("$n should be crowded", t.props.sumOf { it.count } >= 700)
        }
    }
}
