package com.vroombuddies.game

import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.VehicleParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Checks every car design is well formed and fair. Set CAR_PREVIEW_DIR to also export each car as an .obj for previewing. */
class CarsTest {
    private val base = VehicleParams()

    @Test fun designsAreWellFormed() {
        val ids = CarDesigns.all.map { it.id }
        assertEquals("car ids must be unique", ids.size, ids.toSet().size)
        assertTrue("aim for at least 6 cars", CarDesigns.all.size >= 6)
        for (d in CarDesigns.all) {
            val c = d.build()
            assertEquals("${d.id}: four wheels", 4, c.wheelPos.size)
            for (w in c.wheelPos) assertEquals("${d.id}: wheels sit on the ground", c.wheelRadius, w[1], 1e-4f)
            assertTrue("${d.id}: front wheels first", c.wheelPos[0][2] > 0 && c.wheelPos[2][2] < 0)
            assertTrue("${d.id}: needs a boost exhaust", c.exhausts.isNotEmpty())
            for (e in c.exhausts) assertTrue("${d.id}: exhausts point out the back", e[2] < 0)
            val b = c.paint.bounds()
            assertTrue("${d.id}: body is centred", abs(b[0] + b[3]) < 0.05f)
            assertTrue("${d.id}: body is above the ground", b[1] > 0.05f)
            // collision box roughly matches the model
            val wb = c.wheel.bounds()
            val halfLen = maxOf(-b[2], b[5]); val halfW = maxOf(-b[0], b[3], c.wheelPos.maxOf { abs(it[0]) } + wb[3])
            assertTrue("${d.id}: halfLength ${d.params.halfLength} vs model $halfLen", abs(d.params.halfLength - halfLen) < 0.45f)
            assertTrue("${d.id}: halfWidth ${d.params.halfWidth} vs model $halfW", abs(d.params.halfWidth - halfW) < 0.3f)
            assertTrue("${d.id}: triangle budget", (c.paint.iCount + c.trim.iCount + c.glass.iCount + c.lights.iCount + 4 * c.wheel.iCount) / 3 < 30000)
        }
    }

    /** No car is a bad choice for a child: handling stays within about ±10% of the default. */
    @Test fun handlingIsFair() {
        fun near(name: String, v: Float, ref: Float, tol: Float = 0.1f) = assertTrue("$name=$v is more than ${(tol * 100).toInt()}% from $ref", abs(v / ref - 1f) <= tol + 1e-4f)
        for (d in CarDesigns.all) {
            val p = d.params
            near("${d.id} mass", p.mass, base.mass); near("${d.id} topSpeed", p.topSpeed, base.topSpeed)
            near("${d.id} boostSpeed", p.boostSpeed, base.boostSpeed); near("${d.id} accel", p.accel, base.accel)
            near("${d.id} grip", p.grip, base.grip); near("${d.id} wheelbase", p.frontAxle + p.rearAxle, base.frontAxle + base.rearAxle, 0.12f)
        }
    }

    @Test fun paintsFitThePicker() {
        assertTrue(Paints.all.size % 2 == 0)
        assertEquals(Paints.all.size, Paints.all.map { it.name }.toSet().size)
        assertTrue(Paints.all.count { !it.rainbow } >= 6) // AI racers pick from the non-rainbow paints
    }

    @Test fun exportPreviews() {
        val dir = System.getenv("CAR_PREVIEW_DIR") ?: return
        File(dir).mkdirs()
        for (d in CarDesigns.all) {
            val c = d.build()
            val sb = StringBuilder()
            var base = 1
            fun dump(m: MeshData, part: String, tf: FloatArray? = null) {
                val mm = MeshData(); mm.append(m, tf)
                sb.append("o $part\n")
                for (i in 0 until mm.vCount) sb.append("v ${mm.pos[i * 3]} ${mm.pos[i * 3 + 1]} ${mm.pos[i * 3 + 2]} ${mm.col[i * 4]} ${mm.col[i * 4 + 1]} ${mm.col[i * 4 + 2]}\n")
                for (i in 0 until mm.vCount) sb.append("vn ${mm.nrm[i * 3]} ${mm.nrm[i * 3 + 1]} ${mm.nrm[i * 3 + 2]}\n")
                for (t in 0 until mm.iCount / 3) {
                    val a = mm.idx[t * 3] + base; val b = mm.idx[t * 3 + 1] + base; val e = mm.idx[t * 3 + 2] + base
                    sb.append("f $a//$a $b//$b $e//$e\n")
                }
                base += mm.vCount
            }
            dump(c.paint, "paint"); dump(c.trim, "trim"); dump(c.glass, "glass"); dump(c.lights, "lights")
            for (w in c.wheelPos) dump(c.wheel, "wheel", Mat.trs(w[0], w[1], w[2]))
            File(dir, "${d.id}.obj").writeText(sb.toString())
        }
    }
}
