package com.vroombuddies.game.render

import com.vroombuddies.game.content.Theme
import com.vroombuddies.game.sim.Frame
import com.vroombuddies.game.sim.TrackPath
import kotlin.math.*

/** Which material a piece of track geometry uses. */
enum class TrackLayer { ROAD, WALL, SKIRT, RAMP, PADS, START_LINE, ARCH, ARCH_GLOW, GROUND }

/**
 * Pure geometry for every static piece of the race track: road, kerbs, walls, embankments or floating slab,
 * jump ramps, boost pads, start arch and the ground. No GPU calls, so tests and tools can use it.
 * Road pieces come in 240 m chunks so the renderer can skip the parts behind the camera.
 */
class TrackGeometry(private val track: TrackPath, private val theme: Theme) {
    val wallHeight = 1.4f
    private val wallThick = 0.8f

    fun build(out: (MeshData, TrackLayer) -> Unit) {
        val hw = track.halfWidth
        val step = 4f
        val chunk = 240f
        val f = Frame()
        var s0 = 0f
        while (s0 < track.length - 0.01f) {
            val s1 = min(track.length, s0 + chunk)
            val road = MeshData(); val walls = MeshData(); val skirt = MeshData()
            var s = s0
            var prevRoad: IntArray? = null; var prevWall: Array<IntArray>? = null; var prevSkirt: Array<IntArray>? = null
            while (true) {
                track.frame(s, f)
                val y = f.y
                // tilted normal for hills: n = r x t3
                val tl = sqrt(1 + f.slope * f.slope)
                val t3x = f.tx / tl; val t3y = f.slope / tl; val t3z = f.tz / tl
                val nx = 0f * t3z - 0f * t3y + (f.rz * t3y) * 0f + (-f.rz * t3y)
                val ny = f.rz * t3x - f.rx * t3z
                val nz = f.rx * t3y
                val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-4f)
                val v = s / (hw * 2)
                val w = Rgba.WHITE
                val l = road.vertex(f.x - f.rx * hw, y, f.z - f.rz * hw, nx / nl, ny / nl, nz / nl, w, 0f, v)
                val r = road.vertex(f.x + f.rx * hw, y, f.z + f.rz * hw, nx / nl, ny / nl, nz / nl, w, 1f, v)
                prevRoad?.let { road.quadFacing(it[0], it[1], r, l) }
                prevRoad = intArrayOf(l, r)

                // walls (both sides): inner face, top, outer face
                val wv = s / 8f
                val wallRow = Array(2) { IntArray(6) }
                for ((k, side) in floatArrayOf(-1f, 1f).withIndex()) {
                    val xi = side * hw; val xo = side * (hw + wallThick)
                    val ix = f.x + f.rx * xi; val iz = f.z + f.rz * xi; val ox = f.x + f.rx * xo; val oz = f.z + f.rz * xo
                    val inN = -side
                    wallRow[k][0] = walls.vertex(ix, y - 0.2f, iz, f.rx * inN, 0f, f.rz * inN, w, wv, 1f)
                    wallRow[k][1] = walls.vertex(ix, y + wallHeight, iz, f.rx * inN, 0f, f.rz * inN, w, wv, 0.05f)
                    wallRow[k][2] = walls.vertex(ix, y + wallHeight, iz, 0f, 1f, 0f, w, wv, 0f)
                    wallRow[k][3] = walls.vertex(ox, y + wallHeight, oz, 0f, 1f, 0f, w, wv, 0.1f)
                    wallRow[k][4] = walls.vertex(ox, y + wallHeight, oz, f.rx * side, 0f, f.rz * side, w, wv, 0.05f)
                    wallRow[k][5] = walls.vertex(ox, y - 0.2f, oz, f.rx * side, 0f, f.rz * side, w, wv, 1f)
                    prevWall?.let { p ->
                        walls.quadFacing(p[k][0], wallRow[k][0], wallRow[k][1], p[k][1])
                        walls.quadFacing(p[k][2], wallRow[k][2], wallRow[k][3], p[k][3])
                        walls.quadFacing(p[k][4], wallRow[k][4], wallRow[k][5], p[k][5])
                    }
                }
                prevWall = wallRow

                // embankment down to the ground, or a floating slab underside
                val skirtRow = Array(2) { IntArray(2) }
                for ((k, side) in floatArrayOf(-1f, 1f).withIndex()) {
                    val xa = side * (hw + wallThick)
                    val top = y - 0.2f
                    if (theme.ground != null) {
                        val drop = max(0.5f, top + 0.3f)
                        val xb = side * (hw + wallThick + drop / 0.7f + 2f)
                        // outward-and-up normal
                        val sl = 0.7f
                        val nnx = f.rx * side * sl; val nnz = f.rz * side * sl
                        skirtRow[k][0] = skirt.vertex(f.x + f.rx * xa, top, f.z + f.rz * xa, nnx, 1f, nnz, w, xa / 20f, s / 20f)
                        skirtRow[k][1] = skirt.vertex(f.x + f.rx * xb, -0.3f, f.z + f.rz * xb, nnx, 1f, nnz, w, xb / 20f, s / 20f)
                    } else {
                        skirtRow[k][0] = skirt.vertex(f.x + f.rx * xa, top, f.z + f.rz * xa, f.rx * side, 0f, f.rz * side, w, 0f, s / 20f)
                        skirtRow[k][1] = skirt.vertex(f.x + f.rx * xa, top - 3f, f.z + f.rz * xa, f.rx * side, 0f, f.rz * side, w, 0.15f, s / 20f)
                    }
                    prevSkirt?.let { p -> skirt.quadFacing(p[k][0], skirtRow[k][0], skirtRow[k][1], p[k][1]) }
                }
                if (theme.ground == null) {
                    // underside of the floating road
                    val a = skirt.vertex(f.x - f.rx * (hw + wallThick), y - 3.2f, f.z - f.rz * (hw + wallThick), 0f, -1f, 0f, w, 0f, s / 20f)
                    val b = skirt.vertex(f.x + f.rx * (hw + wallThick), y - 3.2f, f.z + f.rz * (hw + wallThick), 0f, -1f, 0f, w, 1f, s / 20f)
                    prevSkirt?.let { p -> skirt.quadFacing(p[0][2.coerceAtMost(p[0].size - 1)], p[1][p[1].size - 1], b, a) }
                    prevSkirt = arrayOf(intArrayOf(skirtRow[0][0], skirtRow[0][1], a), intArrayOf(skirtRow[1][0], skirtRow[1][1], b))
                } else prevSkirt = skirtRow

                if (s >= s1 - 0.001f) break
                s = min(s1, s + step)
            }
            out(road, TrackLayer.ROAD)
            out(walls, TrackLayer.WALL)
            out(skirt, TrackLayer.SKIRT)
            s0 = s1
        }
        buildRamps(out)
        buildPads(out)
        buildBoostGuides(out)
        buildChargeGates(out)
        buildStart(Frame(), out)
        if (theme.ground != null) buildGround(out)
    }

    private fun buildRamps(out: (MeshData, TrackLayer) -> Unit) {
        val hw = track.halfWidth - 0.4f
        val f = Frame()
        for (j in track.jumps) {
            val m = MeshData()
            var prev: IntArray? = null
            val n = 12
            for (i in 0..n) {
                val s = j.s0 + j.length * i / n
                track.frame(s, f)
                val y = track.centreHeight(s) + track.rampHeight(s) + 0.04f
                val v = i.toFloat() / n * 2
                val w = Rgba.WHITE
                val a = m.vertex(f.x - f.rx * hw, y, f.z - f.rz * hw, 0f, 1f, 0f, w, 0f, v)
                val b = m.vertex(f.x + f.rx * hw, y, f.z + f.rz * hw, 0f, 1f, 0f, w, 2f, v)
                prev?.let { m.quadFacing(it[0], it[1], b, a) }
                prev = intArrayOf(a, b)
            }
            // front lip face and the two sides
            track.frame(j.sEnd, f)
            val yb = track.centreHeight(j.sEnd); val yt = yb + j.height + 0.04f
            val c = Rgba.hex(0xd86a00)
            val a = m.vertex(f.x - f.rx * hw, yt, f.z - f.rz * hw, f.tx, 0f, f.tz, c)
            val b = m.vertex(f.x + f.rx * hw, yt, f.z + f.rz * hw, f.tx, 0f, f.tz, c)
            val cc = m.vertex(f.x + f.rx * hw, yb, f.z + f.rz * hw, f.tx, 0f, f.tz, c)
            val d = m.vertex(f.x - f.rx * hw, yb, f.z - f.rz * hw, f.tx, 0f, f.tz, c)
            m.quadFacing(a, b, cc, d)
            for (side in floatArrayOf(-1f, 1f)) {
                var pTop = -1; var pBot = -1
                for (i in 0..n) {
                    val s = j.s0 + j.length * i / n
                    track.frame(s, f)
                    val y0 = track.centreHeight(s); val y1 = y0 + track.rampHeight(s) + 0.04f
                    val px = f.x + f.rx * hw * side; val pz = f.z + f.rz * hw * side
                    val top = m.vertex(px, y1, pz, f.rx * side, 0f, f.rz * side, c)
                    val bot = m.vertex(px, y0, pz, f.rx * side, 0f, f.rz * side, c)
                    if (pTop >= 0) m.quadFacing(pBot, bot, top, pTop)
                    pTop = top; pBot = bot
                }
            }
            out(m, TrackLayer.RAMP)
        }
    }

    private fun buildChargeGates(out: (MeshData, TrackLayer) -> Unit) {
        val housing = MeshData()
        val light = MeshData()
        for (gate in track.chargeGates) {
            val frame = track.frame(gate.s)
            val x = frame.x + frame.rx * gate.x
            val z = frame.z + frame.rz * gate.x
            val yaw = atan2(frame.tx, frame.tz)
            val pose = Mat.trs(x, frame.y + 0.08f, z, ry = yaw)
            Prims.torus(housing, gate.halfWidth + 0.28f, 0.24f, Rgba.hex(0x27374e), pose, 8, 32, PI.toFloat())
            Prims.torus(light, gate.halfWidth, 0.10f, Rgba.hex(0xffbf4a), pose, 6, 32, PI.toFloat())
        }
        if (housing.iCount > 0) {
            out(housing, TrackLayer.ARCH)
            out(light, TrackLayer.ARCH_GLOW)
        }
    }

    /** Three narrowing chevrons lead young drivers into the actual boost lane. */
    private fun buildBoostGuides(out: (MeshData, TrackLayer) -> Unit) {
        val guides = MeshData()
        val frame = Frame()
        val color = Rgba.hex(theme.kerbs.first)
        fun vertex(s: Float, x: Float): Int {
            track.frame(s, frame)
            return guides.vertex(frame.x + frame.rx * x, track.groundHeight(s) + 0.12f,
                frame.z + frame.rz * x, 0f, 1f, 0f, color)
        }
        for (pad in track.pads) for (step in 1..3) {
            val tip = pad.s - pad.halfLength - step * 7f
            val width = pad.halfWidth * (0.60f + step * 0.08f)
            for (side in listOf(-1, 1)) {
                val a = vertex(tip, pad.x)
                val b = vertex(tip - 2f, pad.x + side * width)
                val c = vertex(tip - 2.85f, pad.x + side * width)
                val d = vertex(tip - 0.85f, pad.x)
                guides.quadFacing(a, b, c, d)
            }
        }
        if (guides.iCount > 0) out(guides, TrackLayer.ARCH_GLOW)
    }

    private fun buildPads(out: (MeshData, TrackLayer) -> Unit) {
        val m = MeshData(); val f = Frame()
        for (p in track.pads) {
            val rows = 6
            var prev: IntArray? = null
            for (i in 0..rows) {
                val s = p.s - p.halfLength + 2 * p.halfLength * i / rows
                track.frame(s, f)
                val y = track.groundHeight(s) + 0.07f
                val x0 = p.x - p.halfWidth; val x1 = p.x + p.halfWidth
                val v = i.toFloat() / rows * 2f
                val a = m.vertex(f.x + f.rx * x0, y, f.z + f.rz * x0, 0f, 1f, 0f, Rgba.WHITE, 0f, v)
                val b = m.vertex(f.x + f.rx * x1, y, f.z + f.rz * x1, 0f, 1f, 0f, Rgba.WHITE, 1f, v)
                prev?.let { m.quadFacing(it[0], it[1], b, a) }
                prev = intArrayOf(a, b)
            }
        }
        out(m, TrackLayer.PADS)
    }

    private fun buildStart(f: Frame, out: (MeshData, TrackLayer) -> Unit) {
        track.frame(0f, f)
        val hw = track.halfWidth
        val line = MeshData()
        val y = track.groundHeight(0f) + 0.05f
        val v0 = line.vertex(f.x - f.rx * hw - f.tx * 1.5f, y, f.z - f.rz * hw - f.tz * 1.5f, 0f, 1f, 0f, Rgba.WHITE, 0f, 0f)
        val v1 = line.vertex(f.x + f.rx * hw - f.tx * 1.5f, y, f.z + f.rz * hw - f.tz * 1.5f, 0f, 1f, 0f, Rgba.WHITE, 8f, 0f)
        val v2 = line.vertex(f.x + f.rx * hw + f.tx * 1.5f, y, f.z + f.rz * hw + f.tz * 1.5f, 0f, 1f, 0f, Rgba.WHITE, 8f, 1f)
        val v3 = line.vertex(f.x - f.rx * hw + f.tx * 1.5f, y, f.z - f.rz * hw + f.tz * 1.5f, 0f, 1f, 0f, Rgba.WHITE, 0f, 1f)
        line.quadFacing(v0, v1, v2, v3)
        out(line, TrackLayer.START_LINE)

        // arch: two chunky pillars and a banner
        val arch = MeshData(); val glow = MeshData()
        val yaw = atan2(f.tx, f.tz)
        for (side in floatArrayOf(-1f, 1f)) {
            val px = f.x + f.rx * (hw + 2f) * side; val pz = f.z + f.rz * (hw + 2f) * side
            Prims.roundBox(arch, 2.2f, 11f, 2.2f, 0.5f, Rgba.hex(0xff3b3b), Mat.trs(px, y + 5.5f, pz, 0f, yaw))
            Prims.sphere(arch, 1.6f, Rgba.hex(0xffd54a), Mat.trs(px, y + 11.6f, pz))
        }
        Prims.roundBox(arch, (hw + 2f) * 2 + 2f, 2.6f, 1.2f, 0.5f, Rgba.hex(0x2a6bff), Mat.trs(f.x, y + 9.5f, f.z, 0f, yaw + PI.toFloat() / 2))
        Prims.box(glow, (hw + 2f) * 2, 0.35f, 1.3f, Rgba(1f, 0.9f, 0.3f), Mat.trs(f.x, y + 8.4f, f.z, 0f, yaw + PI.toFloat() / 2))
        out(arch, TrackLayer.ARCH)
        out(glow, TrackLayer.ARCH_GLOW)
    }

    private fun buildGround(out: (MeshData, TrackLayer) -> Unit) {
        val m = MeshData()
        // a grid (not one quad) so fog, shadows and precision behave over 8 km
        val size = 8000f; val n = 16; val cell = size / n
        for (j in 0 until n) for (i in 0 until n) {
            val x = -size / 2 + i * cell + cell / 2; val z = -size / 2 + j * cell + cell / 2
            Prims.plane(m, cell, cell, Rgba.WHITE, Mat.trs(x, 0f, z), cell / 40f, cell / 40f)
        }
        // shift uvs so tiles line up across cells
        for (k in 0 until m.vCount) { m.uv[k * 2] = m.pos[k * 3] / 40f; m.uv[k * 2 + 1] = m.pos[k * 3 + 2] / 40f }
        out(m, TrackLayer.GROUND)
    }
}
