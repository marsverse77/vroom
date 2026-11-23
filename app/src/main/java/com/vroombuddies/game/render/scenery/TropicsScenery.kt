package com.vroombuddies.game.render

import kotlin.math.*

/**
 * Track batch C, water and tropics: Sunny Beach, Jungle Jam, Pirate Cove and Bubble Reef.
 * Every model here is an original cartoon design built from [Prims]. lod 0 = near, lod 1 = far.
 * Near models stay around 300 triangles or less; the few big set pieces (ships, ruins) are placed sparingly.
 */
object TropicsScenery {
    private fun c(h: Int) = Rgba.hex(h)
    private const val PI_F = PI.toFloat()
    private const val TAU = PI_F * 2
    private val WHITE = Rgba.WHITE
    private val BLACK = Rgba(0.02f, 0.02f, 0.02f)

    /** Child transform: [parent] then a local trs. */
    private fun at(parent: FloatArray, tx: Float = 0f, ty: Float = 0f, tz: Float = 0f, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f,
                   sx: Float = 1f, sy: Float = 1f, sz: Float = 1f) = Mat.mul(parent, Mat.trs(tx, ty, tz, rx, ry, rz, sx, sy, sz))

    /**
     * A slice of a sphere between longitudes [ph0] and [ph1]: used for striped beach balls and umbrellas.
     * Same vertex layout and winding as [Prims.sphere].
     */
    fun spherePatch(m: MeshData, r: Float, ph0: Float, ph1: Float, col: Rgba, t: FloatArray? = null, ws: Int = 2, hs: Int = 6, thetaMax: Float = PI_F) {
        val g = MeshData()
        for (j in 0..hs) {
            val th = j.toFloat() / hs * thetaMax
            for (i in 0..ws) {
                val ph = ph0 + (ph1 - ph0) * i / ws
                val x = sin(th) * sin(ph); val y = cos(th); val z = sin(th) * cos(ph)
                g.vertex(x * r, y * r, z * r, x, y, z, col)
            }
        }
        for (j in 0 until hs) for (i in 0 until ws) {
            val a = j * (ws + 1) + i
            g.quad(a, a + ws + 1, a + ws + 2, a + 1)
        }
        m.append(g, t)
    }

    /** A bent leaf made of flat boxes, starting at [base] and pointing along local +Z, drooping more towards the tip. */
    private fun frond(m: MeshData, base: FloatArray, len: Float, width: Float, tilts: FloatArray, col: Rgba, col2: Rgba) {
        var by = 0f; var bz = 0f
        val seg = len / tilts.size
        for ((k, t) in tilts.withIndex()) {
            val w = width * (1f - 0.35f * k / tilts.size)
            val cy = by - sin(t) * seg / 2; val cz = bz + cos(t) * seg / 2
            Prims.box(m, w, 0.08f, seg * 1.05f, if (k % 2 == 0) col else col2, at(base, 0f, cy, cz, rx = t))
            by -= sin(t) * seg; bz += cos(t) * seg
        }
    }

    /** Cartoon eye: white ball with a black pupil looking along +Z. */
    private fun eye(m: MeshData, r: Float, t: FloatArray, lod: Int) {
        Prims.sphere(m, r, WHITE, t, if (lod == 0) 5 else 4, 3)
        if (lod == 0) Prims.sphere(m, r * 0.55f, BLACK, at(t, 0f, r * 0.08f, r * 0.62f, sz = 0.6f), 4, 3)
    }

    // ---------------------------------------------------------------- beach

    private fun palm(lod: Int): SceneryModel {
        val m = MeshData()
        val segs = if (lod == 0) 5 else 3; val sides = if (lod == 0) 6 else 5
        val height = 9f; val lean = 2.2f
        for (i in 0 until segs) {
            val t0 = i.toFloat() / segs; val t1 = (i + 1f) / segs
            val x0 = lean * t0 * t0; val x1 = lean * t1 * t1
            val y0 = height * t0; val y1 = height * t1
            val dx = x1 - x0; val dy = y1 - y0; val len = hypot(dx, dy)
            val r = 0.45f - 0.17f * t0
            Prims.cylinder(m, r * 0.9f, r, len * 1.08f, c(if (i % 2 == 0) 0x9a7650 else 0x7d5c3a),
                Mat.trs((x0 + x1) / 2, (y0 + y1) / 2, 0f, rz = -atan2(dx, dy)), sides, false)
        }
        val crown = Mat.trs(lean, height, 0f)
        if (lod == 0) for (k in 0 until 2) {
            val a = k * TAU / 3
            Prims.rock(m, 0.33f, c(0x6d4c2f), at(crown, cos(a) * 0.4f, -0.45f, sin(a) * 0.4f), k + 2)
        }
        val n = if (lod == 0) 6 else 5
        val tilts = if (lod == 0) floatArrayOf(-0.35f, 0.25f, 0.8f) else floatArrayOf(-0.1f, 0.7f)
        for (k in 0 until n) {
            val a = k * TAU / n + 0.3f
            frond(m, at(crown, ry = a), 5f, 1.1f, tilts, c(0x3fa34a), c(0x5cc05a))
        }
        Prims.sphere(m, 0.5f, c(0x5aa83c), crown, 5, 3)
        m.windByHeight(0.45f)
        return SceneryModel(m, radius = 4.5f, tints = listOf(0xffffff, 0xe8ffe0, 0xfff2d8, 0xdaf5d0))
    }

    private fun umbrella(lod: Int): SceneryModel {
        val m = MeshData()
        val cols = intArrayOf(0xff4d4d, 0xffffff, 0xffc928, 0xffffff, 0x29b6f6, 0xffffff, 0x66d36e, 0xffffff)
        Prims.cylinder(m, 0.06f, 0.06f, 3f, c(0xeeeeee), Mat.trs(0.3f, 1.5f, 0f, rz = 0.12f), 5, false)
        val top = Mat.trs(0.12f, 2.65f, 0f, rz = 0.12f)
        val w = if (lod == 0) 8 else 4
        for (k in 0 until w) {
            val a0 = k * TAU / w; val a1 = (k + 1) * TAU / w
            spherePatch(m, 2.2f, a0, a1, c(cols[k * (8 / w)]), at(top, sy = 0.38f), 1, if (lod == 0) 4 else 2, PI_F / 2)
        }
        Prims.sphere(m, 0.12f, c(0xff4d4d), at(top, 0f, 0.88f, 0f), 5, 3)
        // towel and a bucket
        Prims.box(m, 1.1f, 0.04f, 2f, c(0xff80ab), Mat.trs(-1.1f, 0.02f, 0.6f, ry = 0.2f))
        Prims.box(m, 1.12f, 0.045f, 0.3f, c(0xffffff), Mat.trs(-1.1f, 0.03f, 0.6f, ry = 0.2f))
        if (lod == 0) Prims.cylinder(m, 0.22f, 0.17f, 0.35f, c(0x29b6f6), Mat.trs(0.9f, 0.18f, 1.1f), 8)
        return SceneryModel(m, radius = 2.5f, tints = listOf(0xffffff, 0xfff0f8, 0xf0f8ff))
    }

    private fun beachBall(lod: Int): SceneryModel {
        val m = MeshData()
        val cols = intArrayOf(0xff3b3b, 0xffffff, 0x2a7bff, 0xffd400, 0xffffff, 0x2ecc71)
        val n = 6
        for (k in 0 until n) spherePatch(m, 0.6f, k * TAU / n, (k + 1) * TAU / n, c(cols[k]), Mat.trs(0f, 0.6f, 0f, rx = 0.4f), if (lod == 0) 2 else 1, if (lod == 0) 7 else 4)
        if (lod == 0) Prims.cylinder(m, 0.12f, 0.12f, 1.22f, c(0xffffff), Mat.trs(0f, 0.6f, 0f, rx = 0.4f), 6)
        return SceneryModel(m, radius = 0.7f, small = true)
    }

    private fun sandcastle(lod: Int): SceneryModel {
        val m = MeshData()
        val sand = c(0xf0cd82); val dark = c(0xd9b066)
        val s = if (lod == 0) 6 else 4
        Prims.box(m, 3.2f, 1.2f, 3.2f, sand, Mat.trs(0f, 0.6f, 0f))
        for (x in intArrayOf(-1, 1)) for (z in intArrayOf(-1, 1)) {
            Prims.cylinder(m, 0.6f, 0.7f, 2f, dark, Mat.trs(x * 1.6f, 1f, z * 1.6f), s)
            Prims.cone(m, 0.75f, 0.9f, sand, Mat.trs(x * 1.6f, 2.45f, z * 1.6f), s)
        }
        Prims.cylinder(m, 0.85f, 1f, 2.8f, sand, Mat.trs(0f, 1.4f, 0f), s)
        Prims.cone(m, 1.05f, 1.2f, dark, Mat.trs(0f, 3.4f, 0f), s)
        Prims.cylinder(m, 0.03f, 0.03f, 1.2f, c(0xffffff), Mat.trs(0f, 4.5f, 0f), 4, false)
        Prims.box(m, 0.04f, 0.4f, 0.6f, c(0xff3b3b), Mat.trs(0f, 4.85f, 0.3f))
        Prims.box(m, 0.8f, 0.9f, 0.1f, c(0x8d6e4a), Mat.trs(0f, 0.45f, 1.61f))
        if (lod == 0) {
            // crenellations along the front wall, plus bucket and spade
            for (k in -1..1) Prims.box(m, 0.35f, 0.3f, 0.35f, sand, Mat.trs(k * 0.7f, 1.35f, 1.45f))
            Prims.cylinder(m, 0.4f, 0.3f, 0.6f, c(0xff6f00), Mat.trs(2.6f, 0.3f, 1.2f), 8)
            Prims.box(m, 0.08f, 1.1f, 0.08f, c(0x1e88e5), Mat.trs(2.4f, 0.6f, -1.6f, rz = 0.5f))
            Prims.box(m, 0.4f, 0.04f, 0.5f, c(0x1e88e5), Mat.trs(2.0f, 0.06f, -1.6f))
        }
        return SceneryModel(m, radius = 3f)
    }

    private fun lifeguardTower(lod: Int): SceneryModel {
        val m = MeshData()
        val s = if (lod == 0) 6 else 4
        for (x in intArrayOf(-1, 1)) for (z in intArrayOf(-1, 1))
            Prims.cylinder(m, 0.12f, 0.15f, 4f, c(0xffffff), Mat.trs(x * 1.1f, 2f, z * 1.1f, rx = -z * 0.08f, rz = x * 0.08f), s, false)
        Prims.box(m, 3f, 0.25f, 3f, c(0xdedede), Mat.trs(0f, 4f, 0f))
        Prims.box(m, 2.4f, 1.6f, 2.2f, c(0xff3b3b), Mat.trs(0f, 4.9f, -0.2f))
        Prims.box(m, 2.45f, 0.4f, 2.25f, c(0xffffff), Mat.trs(0f, 5.0f, -0.2f))
        Prims.box(m, 2.9f, 0.2f, 2.9f, c(0xffffff), Mat.trs(0f, 5.85f, -0.2f, rx = 0.1f))
        Prims.box(m, 1.2f, 0.8f, 0.08f, c(0x7fd8ff), Mat.trs(0f, 5.1f, 0.92f))
        if (lod == 0) {
            for (k in 0 until 6) Prims.box(m, 0.9f, 0.08f, 0.15f, c(0xffffff), Mat.trs(0f, 0.4f + k * 0.62f, 2.0f - k * 0.13f))
            Prims.cylinder(m, 0.04f, 0.04f, 2.2f, c(0xffffff), Mat.trs(1.2f, 7f, -1.2f), 4, false)
            Prims.box(m, 0.04f, 0.5f, 0.8f, c(0xffd400), Mat.trs(1.2f, 7.8f, -0.8f))
            Prims.torus(m, 0.35f, 0.1f, c(0xff6f00), Mat.trs(-1.24f, 4.7f, 0.4f, ry = PI_F / 2), 5, 10)
        }
        return SceneryModel(m, radius = 2.5f)
    }

    private fun surfboards(lod: Int): SceneryModel {
        val m = MeshData()
        val cols = intArrayOf(0xffd400, 0x29b6f6, 0xff5fa2)
        for (k in 0 until 3) {
            val t = Mat.trs((k - 1) * 0.9f, 1.3f, 0f, rx = -0.15f + k * 0.08f, ry = (k - 1) * 0.15f, rz = (k - 1) * 0.12f)
            Prims.sphere(m, 1f, c(cols[k]), at(t, sx = 0.32f, sy = 1.4f, sz = 0.06f), if (lod == 0) 8 else 5, if (lod == 0) 6 else 3)
            if (lod == 0) Prims.box(m, 0.08f, 2.2f, 0.07f, c(0xffffff), at(t, 0f, 0.1f, 0f))
        }
        return SceneryModel(m, radius = 1.6f, small = true)
    }

    private fun beachHut(lod: Int): SceneryModel {
        val m = MeshData()
        val stripes = intArrayOf(0x29b6f6, 0xffffff)
        val n = if (lod == 0) 6 else 2
        for (k in 0 until n) {
            val w = 3.2f / n
            Prims.box(m, w, 2.6f, 3f, c(stripes[k % 2]), Mat.trs(-1.6f + w * (k + 0.5f), 1.5f, 0f))
        }
        Prims.box(m, 3.6f, 0.25f, 3.4f, c(0xa1887f), Mat.trs(0f, 0.12f, 0f))
        Prims.box(m, 2.4f, 0.2f, 3.6f, c(0xff6f61), Mat.trs(-0.85f, 3.25f, 0f, rz = 0.55f))
        Prims.box(m, 2.4f, 0.2f, 3.6f, c(0xff6f61), Mat.trs(0.85f, 3.25f, 0f, rz = -0.55f))
        Prims.box(m, 1f, 1.8f, 0.06f, c(0xfff3c4), Mat.trs(0f, 1.15f, 1.52f))
        if (lod == 0) Prims.box(m, 0.7f, 0.5f, 0.06f, c(0xffd400), Mat.trs(0f, 2.4f, 1.53f))
        return SceneryModel(m, radius = 2.6f)
    }

    private fun crab(lod: Int): SceneryModel {
        val m = MeshData()
        val red = c(0xff5a36)
        Prims.sphere(m, 0.5f, red, Mat.trs(0f, 0.35f, 0f, sy = 0.55f), if (lod == 0) 8 else 5, if (lod == 0) 5 else 3)
        for (s in intArrayOf(-1, 1)) {
            Prims.cylinder(m, 0.04f, 0.04f, 0.35f, red, Mat.trs(s * 0.15f, 0.6f, 0.25f), 4, false)
            eye(m, 0.1f, Mat.trs(s * 0.15f, 0.8f, 0.27f), lod)
            Prims.sphere(m, 0.2f, red, Mat.trs(s * 0.6f, 0.35f, 0.35f, ry = s * 0.5f, sx = 0.6f), 5, 3)
            if (lod == 0) for (k in 0 until 2) Prims.box(m, 0.45f, 0.05f, 0.06f, red, Mat.trs(s * 0.55f, 0.18f, -0.1f + k * 0.2f, rz = -s * 0.5f))
        }
        return SceneryModel(m, radius = 0.8f, small = true)
    }

    private fun starfish(lod: Int): SceneryModel {
        val m = MeshData()
        for (k in 0 until 5) {
            val a = k * TAU / 5
            Prims.cone(m, 0.2f, 0.7f, WHITE, Mat.trs(sin(a) * 0.3f, 0.06f, cos(a) * 0.3f, rx = PI_F / 2, ry = a, sz = 0.35f), if (lod == 0) 5 else 3)
        }
        Prims.cylinder(m, 0.22f, 0.25f, 0.12f, WHITE, Mat.trs(0f, 0.06f, 0f), if (lod == 0) 7 else 5)
        return SceneryModel(m, radius = 0.6f, small = true, tints = listOf(0xff7043, 0xffd54f, 0xff80ab, 0xba68c8))
    }

    /** Little sailboat that bobs on the sea (also used by the water effect). */
    private fun sailboat(lod: Int): SceneryModel {
        val m = MeshData()
        val hull = c(0xffffff)
        Prims.box(m, 2f, 0.9f, 5f, hull, Mat.trs(0f, 0.45f, 0f))
        Prims.cone(m, 1f, 1.8f, hull, Mat.trs(0f, 0.45f, 3.4f, rx = PI_F / 2, sx = 1f, sz = 0.45f), 4)
        Prims.box(m, 2.05f, 0.2f, 5.05f, c(0x1e88e5), Mat.trs(0f, 0.75f, 0f))
        Prims.cylinder(m, 0.08f, 0.1f, 6f, c(0xbcaaa4), Mat.trs(0f, 3.8f, 0.4f), 5, false)
        Prims.cone(m, 2.2f, 5f, c(0xff5a5a), Mat.trs(0f, 4f, -0.9f, sx = 0.05f, sz = 0.6f), if (lod == 0) 4 else 3)
        Prims.cone(m, 1.4f, 4f, c(0xffd400), Mat.trs(0f, 3.4f, 1.6f, sx = 0.05f, sz = 0.5f), 3)
        return SceneryModel(m, radius = 4f)
    }

    // ---------------------------------------------------------------- jungle

    private fun jungleTree(lod: Int): SceneryModel {
        val m = MeshData()
        val bark = c(0x6b4a2e); val s = if (lod == 0) 8 else 5
        Prims.cylinder(m, 0.6f, 0.95f, 13f, bark, Mat.trs(0f, 6.5f, 0f), s, false)
        if (lod == 0) for (k in 0 until 4) {
            val a = k * TAU / 4 + 0.4f
            Prims.box(m, 0.25f, 2.2f, 1.6f, bark, Mat.trs(sin(a) * 0.9f, 0.9f, cos(a) * 0.9f, ry = a, rx = 0.15f))
        }
        val greens = intArrayOf(0x2e7d32, 0x388e3c, 0x43a047, 0x2f8f46)
        val blobs = if (lod == 0) 5 else 2
        for (k in 0 until blobs) {
            val a = k * TAU / blobs
            val r = if (k == 0) 0f else 2.6f
            Prims.rock(m, if (k == 0) 4.6f else 3.4f, c(greens[k % greens.size]), Mat.trs(cos(a) * r, 14f + (k % 2) * 1.2f, sin(a) * r, sy = 0.7f), k + 4)
        }
        if (lod == 0) for (k in 0 until 5) {
            // hanging vines with a leaf at the end
            val a = k * TAU / 5 + 0.2f
            val x = cos(a) * 3.8f; val z = sin(a) * 3.8f; val len = 3f + (k % 3) * 1.4f
            Prims.cylinder(m, 0.05f, 0.05f, len, c(0x558b2f), Mat.trs(x, 12.8f - len / 2, z), 3, false)
            Prims.box(m, 0.35f, 0.06f, 0.5f, c(0x7cb342), Mat.trs(x, 12.8f - len, z, rx = 0.6f, ry = a))
        }
        m.windByHeight(0.3f)
        return SceneryModel(m, radius = 6f, tints = listOf(0xffffff, 0xe0f5d8, 0xf5ffe0))
    }

    private fun bigLeaf(lod: Int): SceneryModel {
        val m = MeshData()
        val n = if (lod == 0) 6 else 3
        for (k in 0 until n) {
            val a = k * TAU / n + 0.3f
            val tilt = 0.7f + (k % 3) * 0.2f; val h = 2.2f + (k % 2) * 0.6f
            Prims.cylinder(m, 0.05f, 0.07f, 1.4f, c(0x689f38), Mat.trs(sin(a) * 0.3f, 0.7f, cos(a) * 0.3f, ry = a, rx = tilt * 0.6f), 3, false)
            Prims.cone(m, 1f, h, c(if (k % 2 == 0) 0x2e9e4f else 0x43b05c),
                Mat.trs(sin(a) * (0.6f + h * 0.4f), 1.4f + cos(tilt) * h * 0.25f, cos(a) * (0.6f + h * 0.4f), rx = PI_F - tilt, ry = a, sz = 0.12f), 5)
        }
        m.windByHeight(0.35f)
        return SceneryModel(m, radius = 2.4f, small = true, tints = listOf(0xffffff, 0xd8f0c0, 0xeaffd8))
    }

    private fun giantFlower(lod: Int): SceneryModel {
        val m = MeshData()
        Prims.cylinder(m, 0.12f, 0.16f, 2.4f, c(0x558b2f), Mat.trs(0f, 1.2f, 0f), 5, false)
        val head = Mat.trs(0f, 2.5f, 0f, rx = -0.35f)
        val n = 5
        for (k in 0 until n) {
            val a = k * TAU / n
            Prims.sphere(m, 0.8f, WHITE, at(head, sin(a) * 0.85f, 0f, cos(a) * 0.85f, ry = a, sx = 0.75f, sy = 0.2f), if (lod == 0) 6 else 4, if (lod == 0) 3 else 2)
        }
        Prims.sphere(m, 0.45f, c(0xffd400), at(head, 0f, 0.12f, 0f, sy = 0.6f), if (lod == 0) 6 else 5, if (lod == 0) 4 else 3)
        if (lod == 0) for (k in 0 until 2) {
            val a = k * PI_F + 0.8f
            Prims.cone(m, 0.5f, 1.4f, c(0x43a047), Mat.trs(sin(a) * 0.6f, 0.6f, cos(a) * 0.6f, rx = 1.1f, ry = a, sz = 0.15f), 4)
        }
        m.windByHeight(0.3f)
        return SceneryModel(m, radius = 1.8f, small = true, tints = listOf(0xff3d6e, 0xff7a1a, 0xc04dff, 0xff5fa2, 0xffffff))
    }

    private fun tikiTotem(lod: Int): SceneryModel {
        val m = MeshData()
        val s = if (lod == 0) 8 else 6
        val woods = intArrayOf(0x8d5a32, 0xa66b3c, 0x7a4b28)
        for (k in 0 until 3) {
            val y = 0.9f + k * 1.7f
            Prims.cylinder(m, 0.75f, 0.8f, 1.7f, c(woods[k]), Mat.trs(0f, y, 0f), s)
            if (lod == 0) {
                for (sx in intArrayOf(-1, 1)) {
                    Prims.box(m, 0.36f, 0.3f, 0.1f, WHITE, Mat.trs(sx * 0.3f, y + 0.25f, 0.74f))
                    Prims.box(m, 0.16f, 0.16f, 0.1f, BLACK, Mat.trs(sx * 0.3f, y + 0.22f, 0.79f))
                }
                Prims.box(m, 0.7f, if (k == 1) 0.35f else 0.15f, 0.2f, c(if (k == 1) 0xff5a36 else 0x3e2414), Mat.trs(0f, y - 0.35f, 0.72f))
                Prims.box(m, 0.15f, 0.35f, 0.2f, c(woods[(k + 1) % 3]), Mat.trs(0f, y, 0.82f))
            }
        }
        for (k in 0 until if (lod == 0) 5 else 3) {
            val a = (k - 2) * 0.35f
            Prims.cone(m, 0.25f, 1.6f, c(intArrayOf(0x2ecc71, 0xffd400, 0xff5a36, 0x29b6f6, 0x2ecc71)[k]), Mat.trs(sin(a) * 0.6f, 5.9f, 0f, rz = -a), 4)
        }
        return SceneryModel(m, radius = 1.2f)
    }

    private fun jungleRuin(lod: Int): SceneryModel {
        val m = MeshData()
        val stone = c(0x9e9a86); val stone2 = c(0x857f6c); val moss = c(0x5d9b3a)
        for (x in intArrayOf(-1, 1)) {
            for (k in 0 until 3) Prims.box(m, 1.6f, 1.4f, 1.6f, if (k % 2 == 0) stone else stone2, Mat.trs(x * 3f, 0.7f + k * 1.4f, 0f, ry = k * 0.06f * x))
            Prims.box(m, 1.8f, 0.3f, 1.8f, moss, Mat.trs(x * 3f, 4.35f, 0f))
        }
        Prims.box(m, 8.2f, 1.2f, 1.8f, stone, Mat.trs(0f, 4.9f, 0f, rz = 0.04f))
        Prims.box(m, 8.4f, 0.3f, 2f, moss, Mat.trs(0f, 5.6f, 0f, rz = 0.04f))
        if (lod == 0) {
            Prims.box(m, 1.4f, 1.2f, 1.4f, stone2, Mat.trs(4.8f, 0.5f, 1.6f, ry = 0.5f, rz = 0.2f))
            Prims.box(m, 1.2f, 1f, 1.2f, stone, Mat.trs(-4.6f, 0.45f, -1.4f, ry = 0.9f))
            // carved smiley face on the lintel
            for (sx in intArrayOf(-1, 1)) Prims.box(m, 0.35f, 0.35f, 0.1f, c(0x5a5546), Mat.trs(sx * 0.7f, 5f, 0.92f))
            Prims.box(m, 1.4f, 0.18f, 0.1f, c(0x5a5546), Mat.trs(0f, 4.6f, 0.92f))
            for (k in 0 until 4) {
                val x = -3f + k * 2f
                Prims.cylinder(m, 0.05f, 0.05f, 2f + k % 2, c(0x558b2f), Mat.trs(x, 4.3f - (k % 2) * 0.5f, 1f), 3, false)
            }
        }
        return SceneryModel(m, radius = 5.5f)
    }

    private fun parrot(lod: Int): SceneryModel {
        val m = MeshData()
        val q = if (lod == 0) 1 else 0
        Prims.cylinder(m, 0.08f, 0.1f, 3f, c(0x6d4c2f), Mat.trs(0f, 1.5f, 0f), 5, false)
        Prims.cylinder(m, 0.06f, 0.06f, 1.4f, c(0x6d4c2f), Mat.trs(0f, 3f, 0f, rz = PI_F / 2), 5, false)
        val b = Mat.trs(0.1f, 3.5f, 0f)
        Prims.sphere(m, 0.35f, c(0xff2d2d), at(b, sy = 1.3f), 6 + q, 4)
        Prims.sphere(m, 0.26f, c(0xff2d2d), at(b, 0f, 0.5f, 0.12f), 6 + q, 4)
        Prims.cone(m, 0.1f, 0.25f, c(0xffd400), at(b, 0f, 0.45f, 0.4f, rx = 2f), 5)
        for (s in intArrayOf(-1, 1)) {
            Prims.box(m, 0.08f, 0.6f, 0.4f, c(0x1e88e5), at(b, s * 0.33f, -0.05f, -0.05f, rx = 0.3f))
            if (lod == 0) eye(m, 0.08f, at(b, s * 0.15f, 0.58f, 0.3f), lod)
        }
        Prims.box(m, 0.18f, 0.9f, 0.06f, c(0x2ecc71), at(b, 0f, -0.75f, -0.2f, rx = -0.4f))
        Prims.box(m, 0.14f, 0.7f, 0.06f, c(0xffd400), at(b, 0.1f, -0.7f, -0.22f, rx = -0.4f, rz = 0.2f))
        return SceneryModel(m, radius = 0.8f, small = true, tints = listOf(0xffffff, 0xffffff, 0xb0e0ff))
    }

    // ---------------------------------------------------------------- pirates

    private fun chest(lod: Int): SceneryModel {
        val m = MeshData(); val glow = MeshData()
        val wood = c(0x8d5524); val gold = c(0xffc928)
        Prims.box(m, 1.6f, 0.9f, 1f, wood, Mat.trs(0f, 0.45f, 0f))
        // lid tipped open
        val hinge = Mat.trs(0f, 0.9f, -0.5f, rx = -1.0f)
        Prims.cylinder(m, 0.5f, 0.5f, 1.6f, wood, at(hinge, 0f, 0f, 0.5f, rz = PI_F / 2), if (lod == 0) 8 else 5, true, PI_F)
        for (x in floatArrayOf(-0.6f, 0.6f)) Prims.box(m, 0.12f, 0.92f, 1.04f, gold, Mat.trs(x, 0.45f, 0f))
        Prims.box(m, 0.25f, 0.3f, 0.08f, gold, Mat.trs(0f, 0.65f, 0.52f))
        // treasure heap and spilled coins
        Prims.sphere(m, 0.65f, gold, Mat.trs(0f, 0.85f, 0f, sy = 0.4f, sx = 1.1f), 7, 3, PI_F / 2)
        if (lod == 0) {
            for (k in 0 until 6) {
                val a = k * 1.1f
                Prims.cylinder(m, 0.14f, 0.14f, 0.04f, gold, Mat.trs(1.0f + cos(a) * 0.4f, 0.02f + (k % 2) * 0.04f, 0.6f + sin(a) * 0.3f, rx = (k % 3) * 0.2f), 7)
            }
            Prims.sphere(glow, 0.12f, Rgba(1f, 0.85f, 0.3f), Mat.trs(0.2f, 1.08f, 0.1f), 5, 3)
            Prims.sphere(glow, 0.1f, Rgba(0.4f, 1f, 0.6f), Mat.trs(-0.25f, 1.05f, -0.1f), 5, 3)
            Prims.sphere(glow, 0.1f, Rgba(1f, 0.3f, 0.4f), Mat.trs(0.05f, 1.06f, -0.25f), 5, 3)
        }
        return SceneryModel(m, if (lod == 0) glow else null, radius = 1.3f, small = true)
    }

    private fun barrelProp(lod: Int): SceneryModel {
        val m = MeshData()
        val wood = c(0xa0662f); val hoop = c(0x5d5d5d); val s = if (lod == 0) 8 else 6
        fun barrel(t: FloatArray) {
            Prims.cylinder(m, 0.55f, 0.48f, 0.6f, wood, at(t, 0f, 0.3f, 0f), s, false)
            Prims.cylinder(m, 0.48f, 0.55f, 0.6f, wood, at(t, 0f, -0.3f, 0f), s, false)
            Prims.cylinder(m, 0.46f, 0.46f, 1.21f, c(0xc28a4a), t, s, true)
            if (lod == 0) for (y in floatArrayOf(-0.4f, 0.4f)) Prims.cylinder(m, 0.53f, 0.53f, 0.08f, hoop, at(t, 0f, y, 0f), s, false)
        }
        barrel(Mat.trs(0f, 0.6f, 0f))
        barrel(Mat.trs(1.15f, 0.6f, 0.2f, ry = 0.4f))
        barrel(Mat.trs(0.55f, 1.8f, 0.1f, ry = 0.9f))
        return SceneryModel(m, radius = 1.8f)
    }

    private fun cannon(lod: Int): SceneryModel {
        val m = MeshData()
        val s = if (lod == 0) 8 else 6
        Prims.box(m, 1.1f, 0.5f, 1.8f, c(0x8d5524), Mat.trs(0f, 0.55f, 0f))
        for (x in intArrayOf(-1, 1)) for (z in intArrayOf(-1, 1))
            Prims.cylinder(m, 0.38f, 0.38f, 0.15f, c(0x6d3f1a), Mat.trs(x * 0.62f, 0.38f, z * 0.6f, rz = PI_F / 2), s)
        Prims.cylinder(m, 0.3f, 0.42f, 2.4f, c(0x37474f), Mat.trs(0f, 1.05f, 0.5f, rx = PI_F / 2 - 0.25f), s)
        Prims.torus(m, 0.32f, 0.08f, c(0x263238), Mat.trs(0f, 1.35f, 1.65f, rx = -0.25f), 4, if (lod == 0) 8 else 6)
        if (lod == 0) {
            Prims.sphere(m, 0.25f, c(0x263238), Mat.trs(-0.2f, 0.25f, -1.4f), 5, 4)
            Prims.sphere(m, 0.25f, c(0x263238), Mat.trs(0.3f, 0.25f, -1.45f), 5, 4)
        }
        return SceneryModel(m, radius = 1.6f)
    }

    private fun anchor(lod: Int): SceneryModel {
        val m = MeshData()
        val iron = c(0x546e7a)
        val t = Mat.trs(0f, 0f, 0f, rz = 0.35f)
        Prims.cylinder(m, 0.18f, 0.18f, 4f, iron, at(t, 0f, 2.2f, 0f), if (lod == 0) 8 else 5)
        Prims.box(m, 2f, 0.25f, 0.25f, iron, at(t, 0f, 3.6f, 0f))
        Prims.torus(m, 0.45f, 0.12f, iron, at(t, 0f, 4.6f, 0f), 5, if (lod == 0) 12 else 6)
        Prims.torus(m, 1.5f, 0.16f, iron, at(t, 0f, 1.7f, 0f, rz = PI_F), 5, if (lod == 0) 12 else 6, PI_F)
        for (s in intArrayOf(-1, 1)) Prims.cone(m, 0.35f, 0.8f, iron, at(t, s * 1.5f, 2f, 0f, rz = -s * 0.5f, sz = 0.4f), 4)
        if (lod == 0) Prims.cylinder(m, 1.5f, 1.8f, 0.3f, c(0xd9bd80), Mat.trs(0f, 0.1f, 0f), 9)
        return SceneryModel(m, radius = 2f)
    }

    private fun jollyFlag(lod: Int): SceneryModel {
        val m = MeshData()
        Prims.cylinder(m, 0.1f, 0.13f, 7f, c(0x6d4c2f), Mat.trs(0f, 3.5f, 0f), if (lod == 0) 6 else 4, false)
        // friendly pirate flag: wavy black cloth with a big smiley and a gold star
        val n = if (lod == 0) 4 else 2
        for (k in 0 until n) {
            val w = 2.6f / n
            Prims.box(m, 0.06f, 1.7f, w * 1.05f, c(0x212121), Mat.trs(sin(k * 1.6f) * 0.15f, 6f, 0.1f + w * (k + 0.5f), ry = cos(k * 1.6f) * 0.25f))
        }
        Prims.sphere(m, 0.45f, c(0xffffff), Mat.trs(0.1f, 6.1f, 1.2f, sx = 0.12f), 7, 5)
        if (lod == 0) {
            for (s in intArrayOf(-1, 1)) Prims.sphere(m, 0.08f, BLACK, Mat.trs(0.16f, 6.2f, 1.2f + s * 0.15f), 4, 3)
            Prims.box(m, 0.04f, 0.06f, 0.4f, BLACK, Mat.trs(0.16f, 5.92f, 1.2f))
            Prims.sphere(m, 0.18f, c(0xffd400), Mat.trs(0.1f, 6.55f, 2.2f, sx = 0.15f), 5, 3)
        }
        Prims.sphere(m, 0.18f, c(0xffc928), Mat.trs(0f, 7.05f, 0f), 6, 4)
        m.windByHeight(0.35f)
        return SceneryModel(m, radius = 2f, small = true)
    }

    private fun lantern(lod: Int): SceneryModel {
        val m = MeshData(); val glow = MeshData()
        Prims.cylinder(m, 0.1f, 0.12f, 3.6f, c(0x5d4037), Mat.trs(0f, 1.8f, 0f), if (lod == 0) 6 else 4, false)
        Prims.box(m, 0.9f, 0.1f, 0.1f, c(0x5d4037), Mat.trs(0.4f, 3.5f, 0f))
        Prims.box(m, 0.42f, 0.08f, 0.42f, c(0x3e2723), Mat.trs(0.75f, 3.05f, 0f))
        Prims.cone(m, 0.32f, 0.3f, c(0x3e2723), Mat.trs(0.75f, 3.25f - 0.6f + 0.6f, 0f), 4)
        Prims.box(m, 0.42f, 0.08f, 0.42f, c(0x3e2723), Mat.trs(0.75f, 2.45f, 0f))
        Prims.box(glow, 0.3f, 0.5f, 0.3f, Rgba(1f, 0.6f, 0.2f), Mat.trs(0.75f, 2.75f, 0f))
        return SceneryModel(m, glow, radius = 1f, small = true)
    }

    /** A cartoon pirate ship (bobs on the sea in Pirate Cove; also usable as far scenery). */
    private fun pirateShip(lod: Int): SceneryModel {
        val m = MeshData()
        val wood = c(0x8d5524); val wood2 = c(0x6d3f1a); val sail = c(0xfff5dc)
        Prims.box(m, 5f, 2.6f, 14f, wood, Mat.trs(0f, 1.3f, 0f))
        Prims.cone(m, 2.5f, 4f, wood, Mat.trs(0f, 1.3f, 8.8f, rx = PI_F / 2, sz = 0.52f), 4)
        Prims.box(m, 5.2f, 0.5f, 14.2f, c(0xffc928), Mat.trs(0f, 2.3f, 0f))
        Prims.box(m, 4.6f, 2.4f, 3.6f, wood2, Mat.trs(0f, 3.8f, -5f))
        Prims.box(m, 5f, 0.3f, 4f, c(0xff3b3b), Mat.trs(0f, 5.1f, -5f))
        for ((z, h) in listOf(1.5f to 13f, -2.5f to 10f)) {
            Prims.cylinder(m, 0.18f, 0.25f, h, wood2, Mat.trs(0f, 2.6f + h / 2, z), if (lod == 0) 6 else 4, false)
            for (k in 0 until 2) {
                val y = 2.6f + h * (0.45f + k * 0.3f)
                Prims.box(m, 4.6f - k * 1.2f, h * 0.24f, 0.25f, sail, Mat.trs(0f, y, z + 0.3f, rx = -0.08f))
                if (lod == 0) Prims.box(m, 4.8f - k * 1.2f, 0.12f, 0.15f, wood2, Mat.trs(0f, y + h * 0.12f, z + 0.2f))
            }
        }
        Prims.box(m, 0.06f, 1f, 1.6f, c(0x212121), Mat.trs(0f, 15.2f, 2.3f))
        if (lod == 0) for (k in 0 until 4) for (s in intArrayOf(-1, 1)) {
            Prims.cylinder(m, 0.3f, 0.3f, 0.1f, c(0x263238), Mat.trs(s * 2.52f, 1.5f, -3f + k * 2.4f, rz = PI_F / 2), 6)
        }
        return SceneryModel(m, radius = 9f)
    }

    // ---------------------------------------------------------------- reef

    private fun coral(lod: Int): SceneryModel {
        val m = MeshData(); val glow = MeshData()
        val s = if (lod == 0) 6 else 4
        fun branch(x: Float, z: Float, h: Float, lean: Float, ry: Float) {
            val t = Mat.trs(x, 0f, z, ry = ry)
            Prims.cylinder(m, 0.16f, 0.24f, h, WHITE, at(t, 0f, h / 2, 0f, rx = lean), s, false)
            val tipY = cos(lean) * h; val tipZ = sin(lean) * h
            Prims.sphere(m, 0.24f, WHITE, at(t, 0f, tipY, tipZ), s, 3)
            if (lod == 0) {
                Prims.cylinder(m, 0.1f, 0.14f, h * 0.5f, WHITE, at(t, 0f, tipY * 0.6f + h * 0.12f, tipZ * 0.6f, rx = lean - 0.8f), s, false)
                Prims.sphere(glow, 0.13f, Rgba(0.9f, 0.6f, 0.8f), at(t, 0f, tipY + 0.18f, tipZ), 5, 3)
            }
        }
        val n = if (lod == 0) 5 else 3
        for (k in 0 until n) {
            val a = k * TAU / n
            branch(cos(a) * 0.4f, sin(a) * 0.4f, 1.6f + (k % 3) * 0.5f, 0.35f + (k % 2) * 0.2f, a)
        }
        Prims.rock(m, 0.8f, c(0xd8c8b8), Mat.trs(0f, 0.2f, 0f, sy = 0.5f), 7)
        return SceneryModel(m, if (lod == 0) glow else null, radius = 1.8f, tints = listOf(0xff6f91, 0xff9a3c, 0xb388ff, 0xffd54f, 0x4dd0e1, 0xff5fa2))
    }

    private fun kelp(lod: Int): SceneryModel {
        val m = MeshData()
        val strands = if (lod == 0) 3 else 2; val segs = if (lod == 0) 6 else 3
        for (k in 0 until strands) {
            val bx = (k - 1) * 0.6f; val bz = (k % 2) * 0.5f
            val h = 6f + k * 1.5f; val seg = h / segs
            for (i in 0 until segs) {
                val zig = if (i % 2 == 0) 0.18f else -0.18f
                Prims.box(m, 0.5f, seg * 1.08f, 0.06f, c(if (i % 2 == 0) 0x4caf50 else 0x7cb342),
                    Mat.trs(bx + zig * 0.5f, seg * (i + 0.5f), bz, ry = k * 1.1f, rz = zig))
                if (lod == 0 && i == 3) Prims.sphere(m, 0.14f, c(0xcddc39), Mat.trs(bx + 0.3f, seg * (i + 0.5f), bz, ry = k * 1.1f), 4, 3)
            }
        }
        m.windByHeight(0.9f)
        return SceneryModel(m, radius = 1.2f, small = true, tints = listOf(0xffffff, 0xd8f0a0, 0xa8d8a0))
    }

    private fun fish(lod: Int): SceneryModel {
        val m = MeshData()
        val q = if (lod == 0) 1 else 0
        val pos = arrayOf(floatArrayOf(0f, 0f, 0f, 1f), floatArrayOf(1.8f, 0.8f, -1.4f, 0.8f), floatArrayOf(-1.6f, -0.5f, -1.8f, 0.7f))
        for ((k, p) in pos.withIndex()) {
            val t = Mat.trs(p[0], p[1], p[2], sx = p[3], sy = p[3], sz = p[3])
            Prims.sphere(m, 0.6f, WHITE, at(t, sx = 0.55f, sy = 0.9f, sz = 1.15f), 6 + q, 4)
            Prims.cone(m, 0.5f, 0.7f, WHITE, at(t, 0f, 0f, -0.85f, rx = -PI_F / 2, sx = 0.2f), 4)
            Prims.cone(m, 0.3f, 0.5f, WHITE, at(t, 0f, 0.55f, -0.1f, rx = -0.5f, sx = 0.15f), 3)
            if (lod == 0 && k == 0) {
                for (s in intArrayOf(-1, 1)) eye(m, 0.15f, at(t, s * 0.22f, 0.15f, 0.45f, ry = s * 0.5f), lod)
                Prims.box(m, 0.62f, 0.9f, 0.12f, c(0x222244), at(t, 0f, 0f, 0.05f))
            }
        }
        return SceneryModel(m, radius = 2.5f, tints = listOf(0xff9a3c, 0xffd54f, 0x4fc3f7, 0xff80ab, 0x69f0ae, 0xb388ff))
    }

    private fun anemone(lod: Int): SceneryModel {
        val m = MeshData(); val glow = MeshData()
        Prims.cylinder(m, 0.55f, 0.7f, 0.6f, c(0xbdbdbd), Mat.trs(0f, 0.3f, 0f), if (lod == 0) 8 else 5)
        val n = if (lod == 0) 10 else 5
        for (k in 0 until n) {
            val a = k * TAU / n; val r = if (k % 2 == 0) 0.4f else 0.2f
            val lean = 0.25f + r * 0.6f; val h = 1.2f + (k % 3) * 0.25f
            Prims.cylinder(m, 0.06f, 0.1f, h, WHITE, Mat.trs(cos(a) * r, 0.6f + h / 2 * cos(lean), sin(a) * r, rx = lean, ry = PI_F / 2 - a), 4, false)
            if (lod == 0) Prims.sphere(glow, 0.1f, Rgba(0.8f, 0.8f, 0.8f), Mat.trs(cos(a) * (r + sin(lean) * h), 0.6f + h * cos(lean), sin(a) * (r + sin(lean) * h)), 4, 3)
        }
        m.windByHeight(0.5f)
        return SceneryModel(m, if (lod == 0) glow else null, radius = 1.2f, small = true, tints = listOf(0xff80ab, 0x80deea, 0xffcc80, 0xce93d8))
    }

    private fun clam(lod: Int): SceneryModel {
        val m = MeshData(); val glow = MeshData()
        val shell = c(0xf8bbd0); val ws = if (lod == 0) 10 else 6
        Prims.sphere(m, 1f, shell, Mat.trs(0f, 0.05f, 0f, rx = PI_F, sy = 0.45f), ws, 3, PI_F / 2)
        Prims.sphere(m, 1f, c(0xf48fb1), Mat.trs(0f, 0.1f, -0.85f, rx = -0.9f, sy = 0.45f, sz = 1f), ws, 3, PI_F / 2)
        Prims.cylinder(m, 0.98f, 0.98f, 0.06f, c(0xfce4ec), Mat.trs(0f, 0.06f, 0f), ws)
        Prims.sphere(glow, 0.28f, Rgba(0.9f, 0.9f, 1f), Mat.trs(0f, 0.32f, 0.1f), 7, 5)
        return SceneryModel(m, glow, radius = 1.2f, small = true)
    }

    private fun jellyfish(lod: Int): SceneryModel {
        val m = MeshData(); val glow = MeshData()
        Prims.sphere(glow, 0.9f, Rgba(0.55f, 0.3f, 0.6f), Mat.trs(0f, 2.2f, 0f, sy = 0.75f), if (lod == 0) 10 else 6, if (lod == 0) 4 else 2, PI_F / 2)
        Prims.sphere(m, 0.75f, c(0xf3e5f5), Mat.trs(0f, 2.15f, 0f, rx = PI_F, sy = 0.25f), 8, 2, PI_F / 2)
        if (lod == 0) for (k in 0 until 6) {
            val a = k * TAU / 6
            for (i in 0 until 3) {
                Prims.cylinder(m, 0.04f, 0.04f, 0.6f, c(0xe1bee7), Mat.trs(cos(a) * 0.5f + sin(i * 1.7f + a) * 0.08f, 1.85f - i * 0.55f, sin(a) * 0.5f, rz = sin(i + a) * 0.3f), 3, false)
            }
        }
        return SceneryModel(m, glow, radius = 1.2f, tints = listOf(0xffffff, 0xb3e5fc, 0xffccbc))
    }

    private fun sunkenShip(lod: Int): SceneryModel {
        val m = MeshData()
        val wood = c(0x6d5a48); val wood2 = c(0x55463a); val moss = c(0x5e8c5a)
        val t = Mat.trs(0f, 0.4f, 0f, rz = 0.35f, rx = 0.06f)
        Prims.box(m, 4.5f, 2.4f, 13f, wood, at(t, 0f, 1f, 0f))
        Prims.cone(m, 2.25f, 3.5f, wood, at(t, 0f, 1f, 8.2f, rx = PI_F / 2, sz = 0.52f), 4)
        Prims.box(m, 4.6f, 0.4f, 13.1f, moss, at(t, 0f, 2.3f, 0f))
        Prims.box(m, 4f, 2f, 3f, wood2, at(t, 0f, 3.2f, -4.6f))
        Prims.cylinder(m, 0.2f, 0.25f, 6f, wood2, at(t, 0f, 5f, 1.5f, rx = 0.3f), if (lod == 0) 6 else 4, false)
        if (lod == 0) {
            Prims.box(m, 3f, 1.6f, 0.15f, c(0xbcae94), at(t, 0f, 6f, 2.3f, rx = 0.4f, rz = 0.2f))
            for (k in 0 until 3) Prims.box(m, 0.8f, 0.8f, 0.1f, c(0x1c2b33), at(t, 2.27f, 1.2f, -3f + k * 3f, ry = PI_F / 2))
            Prims.cylinder(m, 1.2f, 1.6f, 0.5f, c(0xe3d3a3), Mat.trs(-1.2f, 0.1f, 2f), 8)
        }
        return SceneryModel(m, radius = 8f, tints = listOf(0xffffff, 0xd0e8e0))
    }

    /** A sandy islet with a little palm, dotted around the sea. */
    private fun islet(lod: Int): SceneryModel {
        val m = MeshData()
        Prims.sphere(m, 14f, c(0xf0d590), Mat.trs(0f, -6f, 0f, sy = 0.55f), if (lod == 0) 12 else 7, 4, PI_F / 2)
        Prims.rock(m, 4f, c(0x9e9e9e), Mat.trs(5f, 1.5f, -3f), 5)
        val p = palm(lod)
        m.append(p.lit, Mat.trs(-2f, 1.4f, 1f, ry = 0.6f, sx = 1.3f, sy = 1.3f, sz = 1.3f))
        m.append(p.lit, Mat.trs(3f, 1.4f, 3f, ry = 2.2f))
        return SceneryModel(m, radius = 15f)
    }

    val pack: SceneryPack = mapOf(
        "palm" to ::palm, "umbrella" to ::umbrella, "beachBall" to ::beachBall, "sandcastle" to ::sandcastle,
        "lifeguardTower" to ::lifeguardTower, "surfboards" to ::surfboards, "beachHut" to ::beachHut, "crab" to ::crab,
        "starfish" to ::starfish, "sailboat" to ::sailboat, "islet" to ::islet,
        "jungleTree" to ::jungleTree, "bigLeaf" to ::bigLeaf, "giantFlower" to ::giantFlower, "tikiTotem" to ::tikiTotem,
        "jungleRuin" to ::jungleRuin, "parrot" to ::parrot,
        "chest" to ::chest, "barrelProp" to ::barrelProp, "cannon" to ::cannon, "anchor" to ::anchor,
        "jollyFlag" to ::jollyFlag, "lantern" to ::lantern, "pirateShip" to ::pirateShip,
        "coral" to ::coral, "kelp" to ::kelp, "fish" to ::fish, "anemone" to ::anemone, "clam" to ::clam,
        "jellyfish" to ::jellyfish, "sunkenShip" to ::sunkenShip,
    )
}
