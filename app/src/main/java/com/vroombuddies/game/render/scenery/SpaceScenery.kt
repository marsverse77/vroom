package com.vroombuddies.game.render

import kotlin.math.*

/**
 * Track batch B (Space): Space Station, Moon Base, Galaxy Grand Prix and Crystal Canyon.
 *
 * Everything is a chunky, friendly cartoon shape built from [Prims]. Bright parts that should bloom go in the
 * glow mesh (vertex colour x3 in the unlit glow material, so keep glow colours around 0.2..1).
 * Near LODs stay under ~300 triangles for repeated props; the few huge far-away ones (planet, station) are allowed more.
 */
object SpaceScenery {
    private fun c(h: Int) = Rgba.hex(h)
    private fun g(r: Float, gg: Float, b: Float) = Rgba(r, gg, b)
    private val P2 = (PI / 2).toFloat()
    private val TAU = (PI * 2).toFloat()

    val pack: SceneryPack = mapOf(
        // ------------------------------------------------------------ floating space rocks
        "asteroid" to { lod ->
            val m = MeshData()
            Prims.rock(m, 3f, Rgba.WHITE, Mat.trs(0f, 0f, 0f, 0.4f, 0.2f, 0.1f, 1.2f, 0.85f, 1f), 5)
            if (lod == 0) {
                Prims.rock(m, 1.4f, Rgba(0.85f, 0.85f, 0.85f), Mat.trs(2.6f, 1.1f, 0.4f), 11)
                Prims.rock(m, 0.9f, Rgba(1.1f, 1.1f, 1.1f), Mat.trs(-2.4f, -0.9f, 1.3f), 17)
                // dark crater dimples so it reads as a cartoon space rock
                Prims.sphere(m, 0.9f, Rgba(0.45f, 0.45f, 0.45f), Mat.trs(0.6f, 1.95f, 1.2f, sy = 0.35f), 6, 3)
                Prims.sphere(m, 0.6f, Rgba(0.45f, 0.45f, 0.45f), Mat.trs(-1.2f, 1.6f, -1f, sy = 0.35f), 6, 3)
            }
            SceneryModel(m, radius = 3.5f, tints = listOf(0x9e8f86, 0x8c8fa8, 0xa88f7a, 0x7d7a95, 0xb39ddb))
        },
        // ------------------------------------------------------------ satellite with solar wings and a blinking tip
        "satellite" to { lod ->
            val m = MeshData(); val glow = MeshData()
            Prims.box(m, 2.2f, 2.2f, 3f, c(0xffc94d), Mat.trs(0f, 0f, 0f))
            Prims.cylinder(m, 0.15f, 0.15f, 9f, c(0xcfd8dc), Mat.trs(0f, 0f, 0f, rz = P2), 5, false)
            for (sd in -1..1 step 2) {
                Prims.box(m, 4.5f, 0.12f, 2.6f, c(0x2962ff), Mat.trs(sd * 4.6f, 0f, 0f))
                if (lod == 0) for (k in -1..1) Prims.box(m, 0.08f, 0.16f, 2.6f, c(0xe3f2fd), Mat.trs(sd * 4.6f + k * 1.5f, 0f, 0f))
            }
            if (lod == 0) {
                Prims.cone(m, 1.3f, 0.7f, c(0xf5f5f5), Mat.trs(0f, 1.5f, 0.6f, rx = -0.5f), 10)
                Prims.cylinder(m, 0.06f, 0.06f, 1.8f, c(0x90a4ae), Mat.trs(0f, 2.2f, -0.8f), 4, false)
                Prims.sphere(glow, 0.28f, g(1f, 0.15f, 0.2f), Mat.trs(0f, 3.15f, -0.8f), 6, 4)
            }
            SceneryModel(m, glow, radius = 7f)
        },
        // ------------------------------------------------------------ big ringed planets far away (few of them, so richer mesh)
        "planet" to { lod ->
            val m = MeshData(); val glow = MeshData()
            val ws = if (lod == 0) 26 else 12; val hs = if (lod == 0) 16 else 8
            Prims.sphere(m, 60f, Rgba.WHITE, null, ws, hs)
            // two soft stripes like a cartoon gas giant
            Prims.cylinder(m, 59.0f, 59.0f, 6f, Rgba(0.8f, 0.8f, 0.85f), Mat.trs(0f, 16f, 0f, sx = 1.04f, sz = 1.04f), ws, false)
            Prims.cylinder(m, 57.5f, 57.5f, 5f, Rgba(1.15f, 1.15f, 1.15f), Mat.trs(0f, -22f, 0f, sx = 1.04f, sz = 1.04f), ws, false)
            Prims.torus(m, 98f, 9f, Rgba(1.2f, 1.1f, 0.95f), Mat.trs(0f, 0f, 0f, rx = 1.35f, rz = 0.3f, sz = 0.15f), 6, if (lod == 0) 40 else 20)
            Prims.sphere(m, 9f, c(0xd7ccc8), Mat.trs(130f, 30f, 20f), if (lod == 0) 12 else 6, if (lod == 0) 8 else 4)
            // a thin glowing halo so planets pop against the dark sky
            if (lod == 0) Prims.torus(glow, 61f, 0.8f, g(0.25f, 0.2f, 0.35f), Mat.trs(0f, 0f, 0f, rx = 1.35f, rz = 0.3f), 4, 40)
            SceneryModel(m, glow, radius = 110f, tints = listOf(0xff8a65, 0x4fc3f7, 0xba68c8, 0x81c784, 0xffd54f, 0xf06292))
        },
        // ------------------------------------------------------------ moon base habitat dome with glowing windows
        "dome" to { lod ->
            val m = MeshData(); val glow = MeshData()
            val ws = if (lod == 0) 12 else 8
            Prims.sphere(m, 6f, c(0xf5f7fa), null, ws, if (lod == 0) 5 else 3, P2)
            Prims.cylinder(m, 6.4f, 6.8f, 1.2f, c(0x90a4ae), Mat.trs(0f, 0.3f, 0f), ws, false)
            if (lod == 0) {
                Prims.cylinder(m, 1.6f, 1.6f, 4f, c(0xeceff1), Mat.trs(0f, 1.5f, 7f, rx = P2), 8, true)
                Prims.box(m, 1.8f, 2.2f, 0.2f, c(0x37474f), Mat.trs(0f, 1.3f, 9.05f))
                Prims.cylinder(m, 0.06f, 0.06f, 4f, c(0xcfd8dc), Mat.trs(2f, 7.5f, 0f), 4, false)
                Prims.box(m, 0.05f, 1f, 1.6f, c(0xff5252), Mat.trs(2f, 9f, 0.8f))
                for (k in 0 until 6) {
                    val a = k * TAU / 6 + 0.5f
                    Prims.box(glow, 1.2f, 0.8f, 0.1f, g(1f, 0.8f, 0.35f), Mat.trs(sin(a) * 5.2f, 2.6f, cos(a) * 5.2f, rx = -0.35f, ry = a))
                }
                Prims.sphere(glow, 0.3f, g(0.2f, 1f, 0.4f), Mat.trs(0f, 6.05f, 0f), 6, 3)
            }
            SceneryModel(m, glow, radius = 7f, tints = listOf(0xffffff, 0xe3f2fd, 0xfff8e1))
        },
        // ------------------------------------------------------------ radio antenna tower with a dish and blinking light
        "antenna" to { lod ->
            val m = MeshData(); val glow = MeshData()
            Prims.cylinder(m, 0.25f, 0.7f, 12f, c(0xcfd8dc), Mat.trs(0f, 6f, 0f), 6)
            Prims.box(m, 2.4f, 0.6f, 2.4f, c(0x607d8b), Mat.trs(0f, 0.3f, 0f))
            if (lod == 0) {
                for (k in 0 until 3) Prims.box(m, 1.6f - k * 0.3f, 0.15f, 0.15f, c(0xff7043), Mat.trs(0f, 3f + k * 3f, 0f, ry = k * 1.1f))
                Prims.sphere(m, 2.2f, c(0xfafafa), Mat.trs(0f, 9f, 0.8f, rx = -1.1f, sy = 0.45f), 10, 4, P2)
                Prims.cylinder(m, 0.08f, 0.08f, 1.6f, c(0x90a4ae), Mat.trs(0f, 9.9f, 1.5f, rx = -0.5f), 4, false)
            }
            Prims.sphere(glow, 0.4f, g(1f, 0.2f, 0.15f), Mat.trs(0f, 12.3f, 0f), 6, 4)
            SceneryModel(m, glow, radius = 2.5f)
        },
        // ------------------------------------------------------------ crystal cluster: one crystal in each cluster glows
        "crystal" to { lod ->
            val m = MeshData(); val glow = MeshData()
            val spec = if (lod == 0) arrayOf(
                floatArrayOf(0f, 0f, 0.9f, 4.2f, 0f, 0f), floatArrayOf(1.1f, 0.3f, 0.6f, 2.6f, 0.35f, 0.5f),
                floatArrayOf(-1f, 0.4f, 0.55f, 2.2f, -0.4f, 0.2f), floatArrayOf(0.2f, -1.1f, 0.5f, 1.8f, 0.2f, -0.45f),
                floatArrayOf(-0.4f, 1f, 0.45f, 1.5f, -0.3f, 0.5f))
            else arrayOf(floatArrayOf(0f, 0f, 0.9f, 4.2f, 0f, 0f), floatArrayOf(1.1f, 0.3f, 0.6f, 2.6f, 0.35f, 0.5f))
            for ((i, s) in spec.withIndex()) {
                val x = s[0]; val z = s[1]; val r = s[2]; val h = s[3]; val tz = s[4]; val tx = s[5]
                val tgt = if (i == 0) glow else m
                val col = if (i == 0) g(0.55f, 0.55f, 0.6f) else Rgba(1.15f, 1.15f, 1.2f)
                // the crystal's tilted axis (Mat.trs rotates Rz first, then Rx)
                val ux = -sin(tz); val uy = cos(tz) * cos(tx); val uz = cos(tz) * sin(tx)
                val shaft = h * 0.75f; val tl = h * 0.25f
                Prims.cylinder(tgt, r, r * 0.85f, shaft, col, Mat.trs(x + ux * shaft / 2, uy * shaft / 2, z + uz * shaft / 2, rx = tx, rz = tz), 6, false)
                val tc = shaft + tl / 2
                Prims.cone(tgt, r, tl, col, Mat.trs(x + ux * tc, uy * tc, z + uz * tc, rx = tx, rz = tz), 6)
            }
            Prims.rock(m, 1.2f, c(0x5e4b7a), Mat.trs(0f, 0.1f, 0f, sy = 0.4f), 4)
            SceneryModel(m, glow, radius = 2.2f, tints = listOf(0xb388ff, 0x18ffff, 0xff80ab, 0x69f0ae, 0x82b1ff))
        },
        // ------------------------------------------------------------ cluster of glowing mushrooms
        "glowMushroom" to { lod ->
            val m = MeshData(); val glow = MeshData()
            val spots = arrayOf(floatArrayOf(0f, 0f, 1f), floatArrayOf(0.9f, 0.5f, 0.65f), floatArrayOf(-0.7f, 0.6f, 0.5f))
            val n = if (lod == 0) 3 else 1
            for (k in 0 until n) {
                val (x, z, s) = spots[k].let { Triple(it[0], it[1], it[2]) }
                Prims.cylinder(m, 0.16f * s, 0.24f * s, 1.3f * s, c(0xf3e5f5), Mat.trs(x, 0.65f * s, z), 6, false)
                Prims.sphere(glow, 0.75f * s, g(0.55f, 0.55f, 0.6f), Mat.trs(x, 1.25f * s, z, sy = 0.7f), 8, 3, P2)
                if (lod == 0) Prims.sphere(m, 0.12f * s, c(0xffffff), Mat.trs(x + 0.35f * s, 1.55f * s, z + 0.2f * s), 4, 2)
            }
            SceneryModel(m, glow, radius = 1.6f, small = true, tints = listOf(0x18ffff, 0xff80ab, 0xb2ff59, 0xea80fc, 0xffd740))
        },

        // ============================================================ extra batch B ids (only used by space themes)
        // ------------------------------------------------------------ cartoon rocket, flame glow under the tail
        "rocket" to { lod ->
            val m = MeshData(); val glow = MeshData()
            val s = if (lod == 0) 10 else 6
            Prims.cylinder(m, 1.5f, 1.6f, 8f, c(0xfafafa), Mat.trs(0f, 5f, 0f), s)
            Prims.cone(m, 1.5f, 3.4f, Rgba.WHITE, Mat.trs(0f, 10.7f, 0f), s)
            Prims.cylinder(m, 1.0f, 1.3f, 1.2f, c(0x546e7a), Mat.trs(0f, 0.6f, 0f), s)
            if (lod == 0) {
                for (k in 0 until 3) {
                    val a = k * TAU / 3
                    Prims.box(m, 0.25f, 2.6f, 1.8f, Rgba.WHITE, Mat.trs(sin(a) * 1.8f, 2f, cos(a) * 1.8f, ry = a))
                }
                Prims.torus(m, 0.75f, 0.18f, c(0x90a4ae), Mat.trs(0f, 7f, 1.55f), 3, 10)
                Prims.sphere(glow, 0.6f, g(0.35f, 0.8f, 1f), Mat.trs(0f, 7f, 1.45f, sz = 0.4f), 6, 4)
            }
            Prims.cone(glow, 1.0f, 3.6f, g(1f, 0.55f, 0.12f), Mat.trs(0f, -1.8f, 0f, rx = PI.toFloat()), 8)
            Prims.cone(glow, 0.55f, 2.2f, g(1f, 0.95f, 0.6f), Mat.trs(0f, -1.1f, 0f, rx = PI.toFloat()), 6)
            SceneryModel(m, glow, radius = 3f, tints = listOf(0xff5252, 0x40c4ff, 0xffd740, 0x69f0ae, 0xff80ab))
        },
        // ------------------------------------------------------------ friendly flying saucer
        "ufo" to { lod ->
            val m = MeshData(); val glow = MeshData()
            Prims.sphere(m, 5f, Rgba.WHITE, Mat.trs(0f, 0f, 0f, sy = 0.28f), if (lod == 0) 12 else 8, if (lod == 0) 5 else 3)
            Prims.sphere(m, 2.2f, c(0xb3e5fc), Mat.trs(0f, 0.8f, 0f), if (lod == 0) 9 else 6, 3, P2)
            if (lod == 0) {
                for (k in 0 until 6) {
                    val a = k * TAU / 6
                    Prims.box(glow, 0.6f, 0.4f, 0.6f, if (k % 2 == 0) g(1f, 0.9f, 0.3f) else g(0.3f, 1f, 0.6f), Mat.trs(sin(a) * 4.3f, 0f, cos(a) * 4.3f, ry = a))
                }
                // two little antenna bobbles on top
                for (sd in -1..1 step 2) {
                    Prims.cylinder(m, 0.05f, 0.05f, 1.2f, c(0x90a4ae), Mat.trs(sd * 0.7f, 3.3f, 0f, rz = -sd * 0.3f), 4, false)
                    Prims.box(glow, 0.35f, 0.35f, 0.35f, g(1f, 0.3f, 0.6f), Mat.trs(sd * 0.88f, 3.9f, 0f, rx = 0.6f, ry = 0.6f))
                }
            }
            Prims.cylinder(glow, 1.4f, 2.2f, 0.3f, g(0.2f, 0.9f, 1f), Mat.trs(0f, -1.45f, 0f), if (lod == 0) 8 else 6)
            SceneryModel(m, glow, radius = 5f, tints = listOf(0xb0bec5, 0xce93d8, 0x80cbc4, 0xffcc80))
        },
        // ------------------------------------------------------------ big space station hub (few, far away)
        "station" to { lod ->
            val m = MeshData(); val glow = MeshData()
            val s = if (lod == 0) 14 else 7
            Prims.torus(m, 28f, 3f, c(0xeceff1), Mat.trs(0f, 0f, 0f, rx = P2), if (lod == 0) 8 else 4, if (lod == 0) 32 else 14)
            Prims.cylinder(m, 5f, 5f, 26f, c(0xcfd8dc), null, s)
            Prims.sphere(m, 5f, c(0xcfd8dc), Mat.trs(0f, 13f, 0f), s, 5, P2)
            for (k in 0 until 4) Prims.cylinder(m, 0.8f, 0.8f, 24f, c(0x90a4ae), Mat.trs(0f, 0f, 0f, ry = k * P2, rz = P2), 6, false)
            for (sd in -1..1 step 2) Prims.box(m, 16f, 0.3f, 7f, c(0x2962ff), Mat.trs(sd * 9f, -10f, 0f))
            if (lod == 0) {
                for (k in 0 until 16) {
                    val a = k * TAU / 16
                    Prims.box(glow, 1.4f, 1f, 0.2f, g(1f, 0.85f, 0.4f), Mat.trs(sin(a) * 31.05f, 0f, cos(a) * 31.05f, ry = a))
                }
                Prims.sphere(glow, 0.8f, g(1f, 0.2f, 0.2f), Mat.trs(0f, 18.6f, 0f), 6, 4)
                Prims.cylinder(m, 0.15f, 0.15f, 2f, c(0xcfd8dc), Mat.trs(0f, 17.5f, 0f), 4, false)
            }
            SceneryModel(m, glow, radius = 32f, tints = listOf(0xffffff, 0xfff3e0, 0xe1f5fe))
        },
        // ------------------------------------------------------------ glowing star gate rings to fly past
        "ring" to { lod ->
            val m = MeshData(); val glow = MeshData()
            val ts = if (lod == 0) 18 else 12
            Prims.torus(m, 14f, 1.4f, c(0x455a64), Mat.trs(0f, 14f, 0f), if (lod == 0) 4 else 3, ts)
            Prims.torus(glow, 12.4f, 0.4f, g(0.8f, 0.8f, 0.85f), Mat.trs(0f, 14f, 0f), 3, ts)
            if (lod == 0) for (k in 0 until 4) {
                val a = k * P2 + PI.toFloat() / 4
                Prims.box(m, 2.4f, 2.4f, 3.6f, c(0xffb300), Mat.trs(cos(a) * 14f, 14f + sin(a) * 14f, 0f, rz = a))
            }
            SceneryModel(m, glow, radius = 15f, tints = listOf(0x18ffff, 0xff2bd6, 0xffea00, 0x76ff03))
        },
        // ------------------------------------------------------------ trackside light post (like a street lamp, space style)
        "beacon" to { lod ->
            val m = MeshData(); val glow = MeshData()
            Prims.cylinder(m, 0.18f, 0.28f, 5f, c(0x78909c), Mat.trs(0f, 2.5f, 0f), if (lod == 0) 6 else 4)
            Prims.box(m, 1.2f, 0.4f, 1.2f, c(0x455a64), Mat.trs(0f, 0.2f, 0f))
            Prims.sphere(glow, 0.5f, g(0.75f, 0.75f, 0.8f), Mat.trs(0f, 5.4f, 0f), 6, 4)
            if (lod == 0) Prims.torus(m, 0.62f, 0.08f, c(0xcfd8dc), Mat.trs(0f, 5.4f, 0f, rx = P2), 3, 10)
            SceneryModel(m, glow, radius = 0.8f, small = true, trackside = true, tints = listOf(0x18ffff, 0xff3df2, 0xffea00))
        },
        // ------------------------------------------------------------ twinkly floating star bits (cheap: 20 triangles)
        "starBit" to { _ ->
            val glow = MeshData()
            Prims.rock(glow, 0.7f, g(0.75f, 0.75f, 0.7f), null, 3)
            SceneryModel(MeshData(), glow, radius = 0.8f, small = true, tints = listOf(0xffffff, 0xfff59d, 0x80d8ff, 0xff80ab, 0xb9f6ca))
        },
        // ------------------------------------------------------------ moon crater rim (small ones near, huge ones as far hills)
        "crater" to { lod ->
            val m = MeshData()
            Prims.torus(m, 7f, 2.2f, Rgba.WHITE, Mat.trs(0f, 0f, 0f, rx = P2, sz = 0.45f), if (lod == 0) 5 else 3, if (lod == 0) 16 else 8)
            Prims.cylinder(m, 6f, 6f, 0.1f, Rgba(0.72f, 0.72f, 0.75f), Mat.trs(0f, 0.04f, 0f), if (lod == 0) 14 else 7, true)
            SceneryModel(m, radius = 9f, tints = listOf(0xb0b0b8, 0xa0a0a8, 0xc0bcc0))
        },
        // ------------------------------------------------------------ six-wheel moon buggy with a dish
        "rover" to { lod ->
            val m = MeshData(); val glow = MeshData()
            Prims.box(m, 2.4f, 0.8f, 3.6f, c(0xeceff1), Mat.trs(0f, 1.2f, 0f))
            Prims.box(m, 1.8f, 0.8f, 1.4f, Rgba.WHITE, Mat.trs(0f, 1.95f, -0.6f))
            for (sd in -1..1 step 2) for (k in -1..1) Prims.cylinder(m, 0.5f, 0.5f, 0.4f, c(0x37474f), Mat.trs(sd * 1.35f, 0.5f, k * 1.3f, rz = P2), if (lod == 0) 7 else 5)
            if (lod == 0) {
                Prims.cylinder(m, 0.06f, 0.06f, 1.4f, c(0x90a4ae), Mat.trs(0.6f, 2.8f, -0.6f), 4, false)
                Prims.sphere(m, 0.7f, c(0xfafafa), Mat.trs(0.6f, 3.5f, -0.6f, rx = -0.8f, sy = 0.4f), 8, 3, P2)
                for (sd in -1..1 step 2) Prims.sphere(glow, 0.18f, g(1f, 0.95f, 0.6f), Mat.trs(sd * 0.7f, 1.3f, 1.82f), 5, 3)
            }
            SceneryModel(m, glow, radius = 2.5f, small = true, tints = listOf(0xffd740, 0xff8a65, 0x4fc3f7, 0xffffff))
        },
        // ------------------------------------------------------------ tall canyon rock spire crowned with crystals
        "spire" to { lod ->
            val m = MeshData(); val glow = MeshData()
            Prims.rock(m, 6f, Rgba.WHITE, Mat.trs(0f, 6f, 0f, sx = 1f, sy = 1.6f, sz = 1f), 7)
            Prims.rock(m, 4.5f, Rgba(0.9f, 0.9f, 0.9f), Mat.trs(0.5f, 17f, -0.3f, ry = 0.7f, sy = 1.4f), 13)
            Prims.rock(m, 3f, Rgba(1.1f, 1.1f, 1.1f), Mat.trs(-0.4f, 25f, 0.4f, ry = 1.3f, sy = 1.3f), 21)
            if (lod == 0) for (k in 0 until 4) {
                val a = k * TAU / 4 + 0.3f; val h = 2.4f + (k % 2) * 1.2f
                Prims.cylinder(glow, 0.5f, 0.45f, h, g(0.6f, 0.4f, 0.9f), Mat.trs(sin(a) * 1.3f, 27.5f + h / 2, cos(a) * 1.3f), 6, false)
                Prims.cone(glow, 0.5f, 1.2f, g(0.6f, 0.4f, 0.9f), Mat.trs(sin(a) * 1.3f, 27.5f + h + 0.6f, cos(a) * 1.3f), 6)
            }
            SceneryModel(m, glow, radius = 7f, tints = listOf(0x6a4c93, 0x5c4a7d, 0x7b5ea7, 0x4e3d6b))
        },
    )
}
