package com.vroombuddies.game

import com.vroombuddies.game.render.*
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every primitive must wind its triangles counter-clockwise when seen from outside, or the GPU culls the wrong side. */
class PrimsTest {
    private fun check(name: String, build: (MeshData) -> Unit) {
        val m = MeshData(); build(m)
        var bad = 0; var total = 0
        for (t in 0 until m.iCount / 3) {
            val a = m.idx[t * 3]; val b = m.idx[t * 3 + 1]; val c = m.idx[t * 3 + 2]
            val ux = m.pos[b * 3] - m.pos[a * 3]; val uy = m.pos[b * 3 + 1] - m.pos[a * 3 + 1]; val uz = m.pos[b * 3 + 2] - m.pos[a * 3 + 2]
            val vx = m.pos[c * 3] - m.pos[a * 3]; val vy = m.pos[c * 3 + 1] - m.pos[a * 3 + 1]; val vz = m.pos[c * 3 + 2] - m.pos[a * 3 + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            val area = Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble())
            if (area < 1e-7) continue
            total++
            val sx = m.nrm[a * 3] + m.nrm[b * 3] + m.nrm[c * 3]
            val sy = m.nrm[a * 3 + 1] + m.nrm[b * 3 + 1] + m.nrm[c * 3 + 1]
            val sz = m.nrm[a * 3 + 2] + m.nrm[b * 3 + 2] + m.nrm[c * 3 + 2]
            if (nx * sx + ny * sy + nz * sz < 0) bad++
        }
        assertTrue("$name: $bad of $total triangles wound backwards", bad == 0)
    }

    @Test fun windings() {
        val c = Rgba.WHITE
        check("box") { Prims.box(it, 1f, 2f, 3f, c) }
        check("roundBox") { Prims.roundBox(it, 2f, 1f, 4f, 0.3f, c) }
        check("cylinder") { Prims.cylinder(it, 0.5f, 1f, 2f, c) }
        check("cone") { Prims.cone(it, 1f, 2f, c) }
        check("sphere") { Prims.sphere(it, 1f, c) }
        check("torus") { Prims.torus(it, 2f, 0.5f, c) }
        check("rock") { Prims.rock(it, 1f, c) }
        check("plane") { Prims.plane(it, 2f, 2f, c) }
        check("rotated") { Prims.box(it, 1f, 1f, 1f, c, Mat.trs(1f, 2f, 3f, 0.3f, 1.2f, -0.4f, 2f, 1f, 3f)) }
    }
}
