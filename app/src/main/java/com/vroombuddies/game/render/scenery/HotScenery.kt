package com.vroombuddies.game.render

import kotlin.math.*

/**
 * Track batch A, "Hot & dry": Desert Canyon, Sunset Highway, Volcano Run and Dino Valley.
 *
 * Every model is an original cartoon design made from [Prims]. Near LODs stay under ~300 triangles
 * (big one-off landmarks like volcanoes and mesas use a single cheap LOD). Glowing parts (lava, neon)
 * go in the glow mesh, which blooms; models with glow don't use tints so the glow keeps its colour.
 */
object HotScenery {
    private fun c(h: Int) = Rgba.hex(h)
    private val P2 = (PI / 2).toFloat()
    private val PI_F = PI.toFloat()
    private val TAU = (PI * 2).toFloat()

    /** A vertical cactus arm made of an elbow, an upright and a rounded tip. */
    private fun cactusArm(m: MeshData, side: Float, y: Float, reach: Float, up: Float, r: Float, col: Rgba, seg: Int, tips: Boolean) {
        Prims.cylinder(m, r, r, reach, col, Mat.trs(side * (0.35f + reach / 2), y, 0f, rz = P2), seg, false)
        val ux = side * (0.35f + reach)
        Prims.cylinder(m, r, r, up, col, Mat.trs(ux, y + up / 2 - r * 0.2f, 0f), seg, false)
        if (tips) Prims.sphere(m, r, col, Mat.trs(ux, y + up - r * 0.2f, 0f), seg, 3, P2)
    }

    val pack: SceneryPack = mapOf(
        // ---------------------------------------------------------------- desert
        "cactus" to { lod ->
            val m = MeshData(); val g = c(0x3fa34d); val near = lod == 0
            val seg = if (near) 8 else 5
            Prims.cylinder(m, 0.55f, 0.6f, 5f, g, Mat.trs(0f, 2.5f, 0f), seg, !near)
            if (near) Prims.sphere(m, 0.55f, g, Mat.trs(0f, 5f, 0f), seg, 3, P2)
            cactusArm(m, 1f, 2.3f, 0.7f, 1.9f, 0.38f, g, seg, near)
            cactusArm(m, -1f, 3.0f, 0.55f, 1.4f, 0.34f, g, seg, near)
            if (near) {
                // a pink flower on top, because cactus flowers are the best part
                for (i in 0 until 4) { val a = i * TAU / 4; Prims.sphere(m, 0.22f, c(0xff6fa8), Mat.trs(cos(a) * 0.22f, 5.55f, sin(a) * 0.22f, sy = 0.6f), 4, 2) }
                Prims.sphere(m, 0.14f, c(0xffe14d), Mat.trs(0f, 5.62f, 0f), 4, 2)
            }
            SceneryModel(m, radius = 2f, tints = listOf(0xffffff, 0xe6ffe0, 0xd8f0c8))
        },
        "barrelCactus" to { _ ->
            val m = MeshData()
            Prims.sphere(m, 0.75f, c(0x4caf50), Mat.trs(0f, 0.55f, 0f, sy = 0.8f), 8, 5)
            Prims.sphere(m, 0.22f, c(0xff8a3d), Mat.trs(0f, 1.18f, 0f, sy = 0.6f), 5, 3)
            SceneryModel(m, radius = 0.8f, small = true, tints = listOf(0xffffff, 0xd0f0ff, 0xfff0c0))
        },
        "tumbleweed" to { _ ->
            val m = MeshData(); val col = c(0xc8a165)
            for (k in 0 until 3) Prims.torus(m, 0.8f, 0.07f, col, Mat.trs(0f, 0.85f, 0f, rx = k * 1.05f, ry = k * 0.9f), 3, 10)
            Prims.rock(m, 0.55f, c(0xa98152), Mat.trs(0f, 0.85f, 0f), 4)
            SceneryModel(m, radius = 1f, small = true)
        },
        "mesa" to { _ ->
            // a big striped flat-top rock far off in the distance; one cheap LOD
            val m = MeshData(); val sx = 1.5f; val sz = 0.85f
            Prims.cylinder(m, 70f, 88f, 10f, c(0xc8643a), Mat.trs(0f, 5f, 0f, sx = sx, sz = sz), 10, false)
            Prims.cylinder(m, 64f, 70f, 18f, c(0xe08a4f), Mat.trs(0f, 19f, 0f, sx = sx, sz = sz), 10, false)
            Prims.cylinder(m, 60f, 64f, 8f, c(0xf2b077), Mat.trs(0f, 32f, 0f, sx = sx, sz = sz), 10, false)
            Prims.cylinder(m, 56f, 60f, 12f, c(0xb85a30), Mat.trs(0f, 42f, 0f, sx = sx, sz = sz), 10, true)
            SceneryModel(m, radius = 95f)
        },
        "rockArch" to { lod ->
            val m = MeshData(); val col = c(0xd0703c)
            Prims.torus(m, 8f, 2.2f, col, Mat.trs(0f, 7f, 0f), if (lod == 0) 6 else 4, if (lod == 0) 10 else 6, PI_F)
            Prims.rock(m, 3f, c(0xc4612f), Mat.trs(-8f, 5.8f, 0f, sy = 1.6f), 5)
            Prims.rock(m, 3f, c(0xc4612f), Mat.trs(8f, 5.8f, 0f, sy = 1.6f), 6)
            if (lod == 0) Prims.rock(m, 1.6f, c(0xe08a4f), Mat.trs(-11f, 1.9f, 2f), 7)
            SceneryModel(m, radius = 12f)
        },
        "waterTower" to { lod ->
            val m = MeshData(); val wood = c(0xa0623a); val s = if (lod == 0) 12 else 6
            for (k in 0 until 4) { val a = k * P2 + P2 / 2; Prims.cylinder(m, 0.2f, 0.25f, 7f, wood, Mat.trs(cos(a) * 2.2f, 3.5f, sin(a) * 2.2f), 5, false) }
            Prims.cylinder(m, 2.8f, 2.8f, 4f, c(0xc98a55), Mat.trs(0f, 9f, 0f), s)
            Prims.cone(m, 3.2f, 2.2f, c(0xd84315), Mat.trs(0f, 12.1f, 0f), s)
            if (lod == 0) for (y in floatArrayOf(7.8f, 10.2f)) Prims.torus(m, 2.85f, 0.08f, c(0x5d4037), Mat.trs(0f, y, 0f, rx = P2), 3, 12)
            SceneryModel(m, radius = 4f)
        },
        // ---------------------------------------------------------------- sunset highway
        "billboard" to { lod ->
            val m = MeshData(); val glow = MeshData()
            for (x in floatArrayOf(-3f, 3f)) Prims.cylinder(m, 0.22f, 0.22f, 7f, c(0x8d8d8d), Mat.trs(x, 3.5f, -0.3f), 6, false)
            Prims.box(m, 10f, 5f, 0.4f, c(0xfff3d6), Mat.trs(0f, 8.5f, 0f))
            Prims.box(m, 10.6f, 0.4f, 0.6f, c(0xff6f00), Mat.trs(0f, 11.1f, 0f))
            Prims.box(m, 10.6f, 0.4f, 0.6f, c(0xff6f00), Mat.trs(0f, 5.9f, 0f))
            if (lod == 0) {
                // the poster: a smiling sun over a little cartoon car
                Prims.cylinder(m, 1.3f, 1.3f, 0.1f, c(0xffc400), Mat.trs(-2.6f, 9.3f, 0.25f, rx = P2), 12, true)
                for (x in floatArrayOf(-3f, -2.2f)) Prims.box(m, 0.2f, 0.3f, 0.06f, c(0x3e2723), Mat.trs(x, 9.6f, 0.33f))
                Prims.box(m, 1.2f, 0.15f, 0.06f, c(0x3e2723), Mat.trs(-2.6f, 8.85f, 0.33f))
                Prims.box(m, 4.2f, 1.1f, 0.12f, c(0x29b6f6), Mat.trs(1.8f, 7.6f, 0.26f))
                Prims.box(m, 2.2f, 0.9f, 0.12f, c(0x29b6f6), Mat.trs(1.6f, 8.6f, 0.26f))
                Prims.box(m, 1.6f, 0.6f, 0.08f, c(0xe1f5fe), Mat.trs(1.6f, 8.6f, 0.34f))
                for (x in floatArrayOf(0.4f, 3.2f)) Prims.cylinder(m, 0.5f, 0.5f, 0.12f, c(0x263238), Mat.trs(x, 7.05f, 0.3f, rx = P2), 8, true)
                // spotlights along the top
                for (k in 0 until 4) Prims.box(glow, 0.6f, 0.25f, 0.4f, Rgba(1f, 0.85f, 0.5f), Mat.trs(-3.6f + k * 2.4f, 11.45f, 0.4f))
            }
            SceneryModel(m, if (lod == 0) glow else null, radius = 6f, trackside = true)
        },
        "roadSign" to { _ ->
            // a big friendly chevron sign
            val m = MeshData()
            Prims.cylinder(m, 0.1f, 0.1f, 3f, c(0x9e9e9e), Mat.trs(0f, 1.5f, -0.1f), 5, false)
            Prims.box(m, 2.2f, 1.4f, 0.12f, c(0xffd600), Mat.trs(0f, 3.2f, 0f))
            for (k in -1..1) {
                Prims.box(m, 0.18f, 0.7f, 0.05f, c(0x212121), Mat.trs(k * 0.6f - 0.12f, 3.38f, 0.08f, rz = -0.6f))
                Prims.box(m, 0.18f, 0.7f, 0.05f, c(0x212121), Mat.trs(k * 0.6f - 0.12f, 3.02f, 0.08f, rz = 0.6f))
            }
            SceneryModel(m, radius = 1.4f, small = true, trackside = true)
        },
        "diner" to { lod ->
            // a roadside ice-cream diner with a giant cone on the roof and neon trim
            val m = MeshData(); val glow = MeshData()
            Prims.box(m, 14f, 5f, 8f, c(0xfff8e1), Mat.trs(0f, 2.5f, 0f))
            Prims.box(m, 15f, 0.6f, 9f, c(0xff5c8a), Mat.trs(0f, 5.3f, 0f))
            if (lod == 0) {
                Prims.box(m, 14.05f, 0.8f, 8.05f, c(0x26c6da), Mat.trs(0f, 1.2f, 0f))
                for (k in -2..2) if (k != 0) Prims.box(m, 1.8f, 1.6f, 0.1f, c(0x80d8ff), Mat.trs(k * 2.6f, 3f, 4.02f))
                Prims.box(m, 1.6f, 3f, 0.1f, c(0x8d6e63), Mat.trs(0f, 1.5f, 4.02f))
                Prims.cone(m, 1.4f, 4f, c(0xd9a066), Mat.trs(0f, 7.6f, 0f, rx = PI_F), 8)
                Prims.sphere(m, 1.6f, c(0xff9ec4), Mat.trs(0f, 10.2f, 0f), 8, 5)
                Prims.sphere(m, 1.3f, c(0xfff3c4), Mat.trs(0f, 11.9f, 0f), 8, 5)
                Prims.sphere(m, 0.35f, c(0xe53935), Mat.trs(0f, 13.3f, 0f), 5, 3)
                Prims.box(glow, 15.2f, 0.18f, 0.18f, Rgba(1f, 0.3f, 0.6f), Mat.trs(0f, 5.65f, 4.55f))
                Prims.box(glow, 0.18f, 0.18f, 9.2f, Rgba(0.2f, 0.9f, 1f), Mat.trs(7.55f, 5.65f, 0f))
                Prims.box(glow, 0.18f, 0.18f, 9.2f, Rgba(0.2f, 0.9f, 1f), Mat.trs(-7.55f, 5.65f, 0f))
            }
            SceneryModel(m, if (lod == 0) glow else null, radius = 9f)
        },
        // ---------------------------------------------------------------- volcano
        "lavaRock" to { lod ->
            val m = MeshData(); val glow = MeshData()
            Prims.rock(m, 2f, c(0x3a2a2a), Mat.trs(0f, 1f, 0f, 0.2f, 0.4f, 0f, 1.2f, 0.85f, 1f), 11)
            if (lod == 0) {
                Prims.rock(m, 1.1f, c(0x4a3530), Mat.trs(1.7f, 0.5f, 0.7f), 12)
                // glowing cracks poke through the surface, and a hot puddle at the foot
                Prims.box(glow, 0.18f, 2.6f, 0.18f, Rgba(1f, 0.35f, 0.05f), Mat.trs(0.4f, 1.2f, 1.45f, rz = 0.5f))
                Prims.box(glow, 0.18f, 1.8f, 0.18f, Rgba(1f, 0.45f, 0.05f), Mat.trs(-1.5f, 1.1f, 0.3f, rx = 0.6f, ry = 1.2f))
                Prims.cylinder(glow, 2.6f, 2.6f, 0.06f, Rgba(1f, 0.3f, 0.02f), Mat.trs(0.3f, 0.03f, 0.2f), 9, true)
            }
            SceneryModel(m, if (lod == 0) glow else null, radius = 3f)
        },
        "volcano" to { _ ->
            // a big cartoon volcano with a glowing crater, lava rivers and a puffy smoke cloud; one cheap LOD
            val m = MeshData(); val glow = MeshData()
            Prims.cylinder(m, 95f, 160f, 70f, c(0x5a3b33), Mat.trs(0f, 35f, 0f), 12, false)
            Prims.cylinder(m, 42f, 95f, 50f, c(0x46302b), Mat.trs(0f, 95f, 0f), 12, false)
            Prims.cylinder(glow, 40f, 40f, 1f, Rgba(1f, 0.4f, 0.05f), Mat.trs(0f, 119f, 0f), 12, true)
            for (k in 0 until 5) {
                val a = k * TAU / 5 + 0.3f; val len = 70f + (k % 2) * 40f
                val rr = 42f + len * 0.42f
                val y = 120f - len * 0.42f
                Prims.box(glow, 5f + (k % 2) * 2f, len, 3f, Rgba(1f, 0.32f, 0.04f), Mat.trs(sin(a) * (rr + 1f), y, cos(a) * (rr + 1f), rx = -0.8f, ry = a))
            }
            val smoke = c(0x8a7f7a)
            Prims.sphere(m, 26f, smoke, Mat.trs(0f, 140f, 0f), 8, 5)
            Prims.sphere(m, 20f, smoke, Mat.trs(18f, 165f, 6f), 8, 5)
            Prims.sphere(m, 16f, smoke, Mat.trs(36f, 186f, 10f), 8, 5)
            SceneryModel(m, glow, radius = 165f)
        },
        "lavaPool" to { lod ->
            val m = MeshData(); val glow = MeshData()
            Prims.torus(m, 4.2f, 0.7f, c(0x2e2220), Mat.trs(0f, 0.15f, 0f, rx = P2), 4, if (lod == 0) 12 else 7)
            Prims.cylinder(glow, 4.2f, 4.2f, 0.1f, Rgba(1f, 0.38f, 0.04f), Mat.trs(0f, 0.12f, 0f), 12, true)
            if (lod == 0) {
                Prims.rock(m, 0.8f, c(0x3a2a2a), Mat.trs(5f, 0.4f, 1f), 21)
                Prims.sphere(glow, 0.45f, Rgba(1f, 0.6f, 0.1f), Mat.trs(1f, 0.25f, -1.2f, sy = 0.5f), 5, 3)
                Prims.sphere(glow, 0.3f, Rgba(1f, 0.6f, 0.1f), Mat.trs(-1.6f, 0.2f, 0.8f, sy = 0.5f), 5, 3)
            }
            SceneryModel(m, glow, radius = 5f)
        },
        "burntTree" to { lod ->
            val m = MeshData(); val bark = c(0x3b2b28); val s = if (lod == 0) 6 else 4
            Prims.cylinder(m, 0.3f, 0.55f, 5f, bark, Mat.trs(0f, 2.5f, 0f), s, false)
            Prims.cylinder(m, 0.12f, 0.2f, 2.4f, bark, Mat.trs(0.8f, 4.4f, 0f, rz = -0.8f), s, false)
            Prims.cylinder(m, 0.1f, 0.18f, 2f, bark, Mat.trs(-0.7f, 3.8f, 0.2f, rz = 0.9f), s, false)
            Prims.cylinder(m, 0.08f, 0.14f, 1.6f, bark, Mat.trs(0.1f, 5.5f, -0.5f, rx = -0.6f), s, false)
            SceneryModel(m, radius = 2f)
        },
        "obsidian" to { lod ->
            // shiny black crystal spikes
            val m = MeshData(); val col = c(0x2b2236)
            val spikes = if (lod == 0) 5 else 3
            for (k in 0 until spikes) {
                val a = k * 2.4f; val r = if (k == 0) 0f else 0.9f; val h = if (k == 0) 4f else 2.2f + (k % 2) * 0.8f
                Prims.cone(m, if (k == 0) 0.8f else 0.5f, h, col, Mat.trs(cos(a) * r, h / 2 - 0.1f, sin(a) * r, rx = sin(a) * r * 0.3f, rz = -cos(a) * r * 0.3f), 5)
            }
            SceneryModel(m, radius = 2f, tints = listOf(0xffffff, 0xd8c0ff, 0xffc8b0))
        },
        // ---------------------------------------------------------------- dino valley
        "fern" to { lod ->
            val m = MeshData(); val n = if (lod == 0) 7 else 4
            for (k in 0 until n) {
                val a = k * TAU / n + 0.3f
                Prims.box(m, 0.08f, 2.4f, 0.4f, c(0x5cb85c), Mat.trs(cos(a) * 0.82f, 0.95f, sin(a) * 0.82f, ry = -a, rz = -0.75f))
            }
            Prims.box(m, 0.3f, 1.6f, 0.3f, c(0x6d8b3a), Mat.trs(0f, 0.6f, 0f))
            m.windByHeight(0.35f)
            SceneryModel(m, radius = 1.6f, small = true, tints = listOf(0xffffff, 0xc8ffb0, 0xe0ff90, 0xa8e0a0))
        },
        "cycad" to { lod ->
            // a stubby prehistoric palm with a crown of long fronds
            val m = MeshData(); val s = if (lod == 0) 7 else 5; val n = if (lod == 0) 9 else 5
            Prims.cylinder(m, 0.45f, 0.7f, 3.2f, c(0x8d6e4a), Mat.trs(0f, 1.6f, 0f), s, false)
            if (lod == 0) for (y in floatArrayOf(0.8f, 1.6f, 2.4f)) Prims.cylinder(m, 0.62f - y * 0.06f, 0.66f - y * 0.06f, 0.25f, c(0x6d5438), Mat.trs(0f, y, 0f), s, false)
            for (k in 0 until n) {
                val a = k * TAU / n
                Prims.box(m, 0.08f, 3.8f, 0.55f, c(0x3f9a3f), Mat.trs(cos(a) * 1.65f, 3.9f, sin(a) * 1.65f, ry = -a, rz = -1.05f))
            }
            m.windByHeight(0.3f)
            SceneryModel(m, radius = 3.5f, tints = listOf(0xffffff, 0xe0ffc0, 0xc0f0c0))
        },
        "bigEgg" to { lod ->
            val m = MeshData()
            if (lod == 0) {
                Prims.sphere(m, 0.9f, c(0xfffbee), Mat.trs(0f, 1.3f, 0f, sy = 1.3f), 10, 6)
                Prims.sphere(m, 0.28f, c(0x8bc34a), Mat.trs(0.62f, 1.6f, 0.4f, sy = 0.7f), 6, 3)
                Prims.sphere(m, 0.22f, c(0x8bc34a), Mat.trs(-0.45f, 1.1f, 0.65f, sy = 0.7f), 6, 3)
                Prims.torus(m, 1.05f, 0.35f, c(0xa1887f), Mat.trs(0f, 0.3f, 0f, rx = P2), 4, 8)
            } else Prims.sphere(m, 0.9f, c(0xfffbee), Mat.trs(0f, 1.3f, 0f, sy = 1.3f), 6, 4)
            SceneryModel(m, radius = 1.5f, tints = listOf(0xffffff, 0xfff3c4, 0xd6f5ff, 0xffd6ec))
        },
        "longNeck" to { lod ->
            // a gentle long-necked dinosaur munching the view; tint gives its colour
            val m = MeshData(); val skin = c(0xffffff); val near = lod == 0
            Prims.sphere(m, 3f, skin, Mat.trs(0f, 5f, 0f, sx = 1f, sy = 0.8f, sz = 1.5f), if (near) 8 else 6, if (near) 6 else 4)
            Prims.cylinder(m, 0.7f, 1.1f, 7f, skin, Mat.trs(0f, 8.8f, 4.4f, rx = 0.55f), if (near) 8 else 5, false)
            Prims.sphere(m, 1.1f, skin, Mat.trs(0f, 11.8f, 6.6f, sz = 1.3f), if (near) 7 else 5, if (near) 5 else 3)
            for (x in floatArrayOf(-1.6f, 1.6f)) for (z in floatArrayOf(-2.2f, 2.2f))
                Prims.cylinder(m, 0.6f, 0.65f, 3.2f, skin, Mat.trs(x, 1.6f, z), if (near) 6 else 4, false)
            Prims.cone(m, 1.2f, 6f, skin, Mat.trs(0f, 4.2f, -6.5f, rx = -1.85f), if (near) 6 else 4)
            if (near) {
                for (x in floatArrayOf(-0.75f, 0.75f)) Prims.sphere(m, 0.22f, c(0x1e1e1e), Mat.trs(x, 12.1f, 7.5f), 4, 2)
                for (k in 0 until 3) Prims.sphere(m, 0.55f, c(0xe8e8e8), Mat.trs(0f, 7.2f - k * 0.25f, 2.5f - k * 2.6f, sy = 0.5f), 4, 2)
            }
            SceneryModel(m, radius = 8f, tints = listOf(0x7bc96f, 0x6ec6c9, 0xb39ddb, 0xffb74d))
        },
        "dinoBuddy" to { lod ->
            // a small, round, two-legged dino with a big smile and tiny arms
            val m = MeshData(); val skin = c(0xffffff); val near = lod == 0
            Prims.sphere(m, 1.1f, skin, Mat.trs(0f, 2.1f, 0f, sy = 1.2f), if (near) 8 else 5, if (near) 5 else 4)
            Prims.sphere(m, 0.85f, skin, Mat.trs(0f, 3.7f, 0.55f, sz = 1.2f), if (near) 7 else 5, if (near) 5 else 3)
            for (x in floatArrayOf(-0.5f, 0.5f)) Prims.cylinder(m, 0.28f, 0.32f, 1.2f, skin, Mat.trs(x, 0.6f, 0f), if (near) 6 else 4, false)
            Prims.cone(m, 0.6f, 2.2f, skin, Mat.trs(0f, 1.6f, -1.5f, rx = -2f), if (near) 6 else 4)
            if (near) {
                for (x in floatArrayOf(-0.75f, 0.75f)) Prims.box(m, 0.16f, 0.16f, 0.5f, skin, Mat.trs(x, 2.4f, 0.85f, rx = 0.4f))
                for (x in floatArrayOf(-0.35f, 0.35f)) {
                    Prims.sphere(m, 0.2f, c(0xfafafa), Mat.trs(x, 4.05f, 1.35f), 4, 2)
                    Prims.sphere(m, 0.1f, c(0x1e1e1e), Mat.trs(x, 4.07f, 1.52f), 4, 2)
                }
                Prims.box(m, 0.7f, 0.08f, 0.1f, c(0x5d2a2a), Mat.trs(0f, 3.45f, 1.5f))
                for (k in 0 until 3) Prims.cone(m, 0.22f, 0.45f, c(0xffd54f), Mat.trs(0f, 4.55f - k * 0.55f, 0.1f - k * 0.6f, rx = -0.4f - k * 0.3f), 4)
            }
            SceneryModel(m, radius = 2.2f, tints = listOf(0x8bc34a, 0xff8a65, 0x4fc3f7, 0xba68c8, 0xffd54f))
        },
    )
}
