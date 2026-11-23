package com.vroombuddies.game.render

import kotlin.math.*

/**
 * Track batch E, "Fun town": Neon City, Sweet Treats, Toy Town and Rainbow Sky.
 * All models are original, chunky and cartoony, built from [Prims]. Glowing parts go in the glow mesh
 * (unlit, x3 brightness, so it blooms); keep its vertex colours around 0.3-0.7 so it glows without blowing out.
 *
 * Batch E owns: building, lamp, neonSign, lollipop, candyCane, gumdrop, toyBlocks, toyBall, pencil, toyHouse,
 * cloudPuff, rainbow, plus extras only these themes use: trafficLight, hydrant, cupcake, donut, iceCream, bigCake,
 * crayon, teddy, skyStar, cloudCastle.
 */
object FunTownScenery {
    private fun c(h: Int) = Rgba.hex(h)
    private fun g(h: Int, k: Float = 0.5f) = Rgba.hex(h).mul(k)     // glow colour, pre-dimmed
    private val PI_F = PI.toFloat()
    private val P2 = PI_F / 2

    /** A thin box from (x0,y0) to (x1,y1) in the XY plane, at depth z (neon tube strokes). */
    private fun stroke(m: MeshData, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, w: Float, col: Rgba) {
        val dx = x1 - x0; val dy = y1 - y0
        Prims.box(m, hypot(dx, dy) + w, w, w, col, Mat.trs((x0 + x1) / 2, (y0 + y1) / 2, z, rz = atan2(dy, dx)))
    }

    val pack: SceneryPack = mapOf(
        // ------------------------------------------------------------------ Neon City
        "building" to { lod ->
            // a chunky tower with a grid of glowing windows: glowing columns behind darker floor slabs
            val m = MeshData(); val glow = MeshData()
            val w = 12f; val h = 30f
            Prims.box(m, w, h, w, c(0x4a3f7a), Mat.trs(0f, h / 2, 0f))
            Prims.box(m, 8f, 3f, 8f, c(0x5b4f94), Mat.trs(0f, h + 1.5f, 0f))
            if (lod == 0) {
                var y = 3f
                while (y < h - 1f) { Prims.box(m, w + 0.6f, 1.0f, w + 0.6f, c(0x5d5290), Mat.trs(0f, y, 0f)); y += 3f }
                Prims.cylinder(m, 0.12f, 0.2f, 5f, c(0xcfd8dc), Mat.trs(0f, h + 5.5f, 0f), 5)
            }
            val win = arrayOf(g(0xffd36b, 0.55f), g(0x7af3ff, 0.5f), g(0xff8ae2, 0.5f))
            for (k in 0 until 3) {
                val x = (k - 1) * 3.6f; val col = win[k]
                Prims.box(glow, 1.6f, h - 3f, 0.2f, col, Mat.trs(x, h / 2 + 0.5f, w / 2 + 0.1f))
                Prims.box(glow, 1.6f, h - 3f, 0.2f, col, Mat.trs(-x, h / 2 + 0.5f, -w / 2 - 0.1f))
                Prims.box(glow, 0.2f, h - 3f, 1.6f, col, Mat.trs(w / 2 + 0.1f, h / 2 + 0.5f, x))
                Prims.box(glow, 0.2f, h - 3f, 1.6f, col, Mat.trs(-w / 2 - 0.1f, h / 2 + 0.5f, -x))
            }
            Prims.box(glow, w + 0.8f, 0.5f, w + 0.8f, g(0x2bf5ff, 0.45f), Mat.trs(0f, h - 0.3f, 0f))
            Prims.sphere(glow, 0.5f, g(0xff2b4f, 0.7f), Mat.trs(0f, h + 8.2f, 0f), 5, 3)
            SceneryModel(m, glow, radius = 9.5f, tints = listOf(0xffffff, 0xb8c6ff, 0xffbde6, 0xb8ffe9, 0xe0c8ff))
        },
        "lamp" to { lod ->
            // street lamp on the road edge; the arm reaches toward the road (+Z)
            val m = MeshData(); val glow = MeshData()
            val pole = c(0x37474f)
            Prims.cylinder(m, 0.14f, 0.2f, 7.4f, pole, Mat.trs(0f, 3.7f, 0f), 6, false)
            Prims.box(m, 0.22f, 0.22f, 3.6f, pole, Mat.trs(0f, 7.3f, 1.7f))
            Prims.box(m, 0.9f, 0.35f, 1.4f, pole, Mat.trs(0f, 7.25f, 3.5f))
            if (lod == 0) Prims.cylinder(m, 0.38f, 0.45f, 0.6f, pole, Mat.trs(0f, 0.3f, 0f), 6)
            Prims.box(glow, 0.7f, 0.12f, 1.1f, g(0xfff1c4, 0.75f), Mat.trs(0f, 7.04f, 3.5f))
            SceneryModel(m, glow, radius = 1f, trackside = true)
        },
        "neonSign" to { lod ->
            // a big sign on two posts with a glowing star and frame, tinted per sign
            val m = MeshData(); val glow = MeshData()
            val y0 = 8f; val bw = 8f; val bh = 5.5f
            for (x in floatArrayOf(-2.6f, 2.6f)) Prims.cylinder(m, 0.25f, 0.3f, y0, c(0x455a64), Mat.trs(x, y0 / 2, -0.4f), if (lod == 0) 6 else 4, false)
            Prims.box(m, bw, bh, 0.5f, c(0x1c1830), Mat.trs(0f, y0 + bh / 2 - 0.5f, -0.2f))
            val wc = g(0xffffff, 0.6f); val cy = y0 + bh / 2 - 0.5f; val z = 0.15f
            stroke(glow, -bw / 2 + 0.2f, cy - bh / 2 + 0.2f, bw / 2 - 0.2f, cy - bh / 2 + 0.2f, z, 0.25f, wc)
            stroke(glow, -bw / 2 + 0.2f, cy + bh / 2 - 0.2f, bw / 2 - 0.2f, cy + bh / 2 - 0.2f, z, 0.25f, wc)
            stroke(glow, -bw / 2 + 0.2f, cy - bh / 2 + 0.2f, -bw / 2 + 0.2f, cy + bh / 2 - 0.2f, z, 0.25f, wc)
            stroke(glow, bw / 2 - 0.2f, cy - bh / 2 + 0.2f, bw / 2 - 0.2f, cy + bh / 2 - 0.2f, z, 0.25f, wc)
            // star (pentagram)
            val r = 1.9f
            val px = FloatArray(5) { cos(P2 + it * 2 * PI_F / 5) * r }; val py = FloatArray(5) { cy + sin(P2 + it * 2 * PI_F / 5) * r }
            for (k in 0 until 5) { val j = (k + 2) % 5; stroke(glow, px[k], py[k], px[j], py[j], z, 0.28f, wc) }
            SceneryModel(m, glow, radius = 4.5f, tints = listOf(0xff2bd6, 0x2bf5ff, 0xffea00, 0x69ff7a, 0xff8a3d, 0xb388ff))
        },
        "trafficLight" to { lod ->
            // over-the-road traffic light (+Z toward the road), all three lamps lit for fun
            val m = MeshData(); val glow = MeshData()
            val pole = c(0x263238)
            Prims.cylinder(m, 0.18f, 0.24f, 7.6f, pole, Mat.trs(0f, 3.8f, 0f), 6, false)
            Prims.box(m, 0.25f, 0.25f, 5.6f, pole, Mat.trs(0f, 7.5f, 2.7f))
            Prims.box(m, 0.8f, 2.6f, 0.8f, c(0xffc400), Mat.trs(0f, 6f, 5.1f))
            if (lod == 0) Prims.box(m, 0.9f, 0.15f, 0.9f, pole, Mat.trs(0f, 7.35f, 5.1f))
            val lights = intArrayOf(0xff1744, 0xffea00, 0x00e676)
            for (i in 0 until 3) for (sx in floatArrayOf(-1f, 1f)) Prims.box(glow, 0.12f, 0.55f, 0.55f, g(lights[i], 0.7f), Mat.trs(sx * 0.45f, 6.8f - i * 0.8f, 5.1f))
            SceneryModel(m, glow, radius = 1f, trackside = true)
        },
        "hydrant" to { lod ->
            val m = MeshData(); val s = if (lod == 0) 8 else 5
            val red = c(0xe53935)
            Prims.cylinder(m, 0.32f, 0.38f, 1.0f, red, Mat.trs(0f, 0.5f, 0f), s)
            Prims.sphere(m, 0.33f, red, Mat.trs(0f, 1.0f, 0f), s, 3, P2)
            if (lod == 0) {
                Prims.cylinder(m, 0.13f, 0.13f, 0.9f, c(0xffeb3b), Mat.trs(0f, 0.65f, 0f, rz = P2), 6)
                Prims.cylinder(m, 0.45f, 0.45f, 0.12f, red, Mat.trs(0f, 0.06f, 0f), 8)
            }
            SceneryModel(m, radius = 0.6f, small = true)
        },

        // ------------------------------------------------------------------ Sweet Treats
        "lollipop" to { lod ->
            // stick + round candy with a swirl; the rings poke through both faces
            val m = MeshData()
            val y = 6f
            Prims.cylinder(m, 0.18f, 0.22f, y, c(0xfafafa), Mat.trs(0f, y / 2, 0f), 6, false)
            Prims.cylinder(m, 2.4f, 2.4f, 0.6f, Rgba.WHITE, Mat.trs(0f, y + 2.2f, 0f, rx = P2), if (lod == 0) 20 else 10)
            if (lod == 0) {
                val sw = c(0xff3d7f)
                Prims.torus(m, 1.65f, 0.36f, sw, Mat.trs(0f, y + 2.2f, 0f), 4, 16)
                Prims.torus(m, 0.8f, 0.34f, sw, Mat.trs(0f, y + 2.2f, 0f), 4, 10)
            }
            SceneryModel(m, radius = 2.6f, tints = listOf(0xffffff, 0xfff59d, 0xb2ebf2, 0xc8f7c5, 0xe1bee7, 0xffd0a8))
        },
        "candyCane" to { lod ->
            // red and white striped cane with a hook
            val m = MeshData()
            val red = c(0xe53935); val white = c(0xfafafa)
            val bands = if (lod == 0) 6 else 2; val bh = 6f / bands; val s = if (lod == 0) 8 else 5
            for (i in 0 until bands) Prims.cylinder(m, 0.3f, 0.3f, bh, if (i % 2 == 0) red else white, Mat.trs(0f, bh * (i + 0.5f), 0f), s, false)
            val arcs = if (lod == 0) 4 else 1
            for (i in 0 until arcs) {
                // torus arc in XY, centred so its far end meets the shaft top
                val a0 = PI_F - (i + 1) * PI_F / arcs
                Prims.torus(m, 0.95f, 0.3f, if (i % 2 == 0) white else red, Mat.trs(0.95f, 6f, 0f, rz = a0), if (lod == 0) 6 else 4, if (lod == 0) 3 else 6, PI_F / arcs)
            }
            Prims.sphere(m, 0.3f, red, Mat.trs(1.9f, 6f, 0f), 5, 3)
            SceneryModel(m, radius = 1.6f)
        },
        "gumdrop" to { lod ->
            val m = MeshData(); val s = if (lod == 0) 10 else 6
            Prims.cylinder(m, 0.95f, 1.3f, 1.1f, Rgba.WHITE, Mat.trs(0f, 0.55f, 0f), s, false)
            Prims.sphere(m, 0.95f, Rgba.WHITE, Mat.trs(0f, 1.1f, 0f, sy = 0.8f), s, if (lod == 0) 4 else 2, P2)
            SceneryModel(m, radius = 1.4f, small = true, tints = listOf(0xff1744, 0xff9100, 0xffea00, 0x00e676, 0x2979ff, 0xd500f9, 0xff4081))
        },
        "cupcake" to { lod ->
            val m = MeshData(); val s = if (lod == 0) 12 else 6
            Prims.cylinder(m, 1.7f, 1.25f, 1.6f, c(0xf48fb1), Mat.trs(0f, 0.8f, 0f), s)
            Prims.sphere(m, 1.75f, Rgba.WHITE, Mat.trs(0f, 1.75f, 0f, sy = 0.65f), s, if (lod == 0) 5 else 3, P2 + 0.3f)
            if (lod == 0) {
                Prims.sphere(m, 0.95f, Rgba.WHITE, Mat.trs(0f, 2.55f, 0f, sy = 0.8f), 8, 4)
                Prims.sphere(m, 0.38f, c(0xd50000), Mat.trs(0f, 3.4f, 0f), 6, 4)
            }
            SceneryModel(m, radius = 1.9f, small = true, tints = listOf(0xffffff, 0xfff59d, 0xb3e5fc, 0xf8bbd0, 0xd1c4e9))
        },
        "donut" to { lod ->
            // a giant upright donut with icing on its front
            val m = MeshData()
            val y = 2.1f
            Prims.torus(m, 1.4f, 0.72f, c(0xd7a26a), Mat.trs(0f, y, 0f), if (lod == 0) 6 else 4, if (lod == 0) 14 else 8)
            if (lod == 0) Prims.torus(m, 1.4f, 0.72f, Rgba.WHITE, Mat.trs(0f, y, 0.16f, sz = 0.82f), 6, 14)
            SceneryModel(m, radius = 2.2f, small = true, tints = listOf(0xffffff, 0xffc1e3, 0xb3e5fc, 0xfff9c4, 0xc5e1a5))
        },
        "iceCream" to { lod ->
            val m = MeshData(); val s = if (lod == 0) 10 else 7
            Prims.cylinder(m, 1.1f, 0.06f, 3f, c(0xe0a96d), Mat.trs(0f, 1.5f, 0f), s)
            Prims.sphere(m, 1.25f, c(0xfff3e0), Mat.trs(0f, 3.4f, 0f), if (lod == 0) 8 else 6, if (lod == 0) 6 else 4)
            if (lod == 0) {
                Prims.sphere(m, 1.05f, Rgba.WHITE, Mat.trs(0.1f, 4.9f, 0f), 8, 5)
                Prims.sphere(m, 0.32f, c(0xd50000), Mat.trs(0.1f, 6.1f, 0f), 6, 3)
            }
            SceneryModel(m, radius = 1.4f, tints = listOf(0xffffff, 0xf8bbd0, 0xc8e6c9, 0xfff59d, 0xd7ccc8))
        },
        "bigCake" to { lod ->
            // a far-off landmark: three-tier birthday cake with glowing candles
            val m = MeshData(); val glow = MeshData(); val s = if (lod == 0) 20 else 10
            val tiers = arrayOf(floatArrayOf(20f, 9f), floatArrayOf(14f, 8f), floatArrayOf(8.5f, 7f))
            val cols = intArrayOf(0xf8bbd0, 0xfffde7, 0xb3e5fc)
            var y = 0f
            for ((i, t) in tiers.withIndex()) {
                Prims.cylinder(m, t[0], t[0], t[1], c(cols[i]), Mat.trs(0f, y + t[1] / 2, 0f), s)
                if (lod == 0) Prims.torus(m, t[0], 0.9f, c(0xffffff), Mat.trs(0f, y + t[1], 0f, rx = P2), 4, s)
                y += t[1]
            }
            if (lod == 0) for (k in 0 until 6) {
                val a = k * PI_F / 3; val x = cos(a) * 5f; val z = sin(a) * 5f
                Prims.cylinder(m, 0.45f, 0.45f, 4f, c(if (k % 2 == 0) 0x40c4ff else 0xff80ab), Mat.trs(x, y + 2f, z), 6, false)
                Prims.sphere(glow, 0.6f, g(0xffb300, 0.7f), Mat.trs(x, y + 4.6f, z, sy = 1.5f), 5, 3)
            }
            SceneryModel(m, glow, radius = 21f)
        },

        // ------------------------------------------------------------------ Toy Town
        "toyBlocks" to { lod ->
            // a little tumble of alphabet blocks and a triangle block on top
            val m = MeshData()
            val cols = intArrayOf(0xff5252, 0x448aff, 0xffd740, 0x69f0ae)
            val pos = arrayOf(floatArrayOf(0f, 0.9f, 0f, 0.1f), floatArrayOf(1.95f, 0.9f, 0.3f, -0.25f), floatArrayOf(0.95f, 2.7f, 0.15f, 0.4f), floatArrayOf(-1.4f, 0.9f, 1.6f, 0.7f))
            for ((i, p) in pos.withIndex()) {
                val t = Mat.trs(p[0], p[1], p[2], ry = p[3])
                if (lod == 0) {
                    Prims.roundBox(m, 1.8f, 1.8f, 1.8f, 0.22f, c(cols[i]), t, 1)
                    // a raised square "letter" panel on two faces
                    Prims.box(m, 1.0f, 1.0f, 1.86f, c(0xfafafa), t)
                } else Prims.box(m, 1.8f, 1.8f, 1.8f, c(cols[i]), t)
            }
            Prims.cone(m, 1.3f, 1.5f, c(0xff6e40), Mat.trs(0.95f, 4.35f, 0.15f, ry = 0.4f + PI_F / 4), 4)
            SceneryModel(m, radius = 2.8f, small = true, tints = listOf(0xffffff, 0xfff3e0, 0xe3f2fd))
        },
        "toyBall" to { lod ->
            val m = MeshData()
            val r = 1.6f
            Prims.sphere(m, r, Rgba.WHITE, Mat.trs(0f, r, 0f), if (lod == 0) 10 else 6, if (lod == 0) 6 else 4)
            if (lod == 0) {
                val band = c(0x1565c0)
                Prims.torus(m, r, 0.17f, band, Mat.trs(0f, r, 0f, rx = P2), 3, 14)
                Prims.torus(m, r, 0.17f, band, Mat.trs(0f, r, 0f), 3, 14)
            }
            SceneryModel(m, radius = r, small = true, tints = listOf(0xff3d3d, 0xffea00, 0x00e5ff, 0x76ff03, 0xff80ab, 0xffffff))
        },
        "pencil" to { lod ->
            // giant pencil stuck in the ground, leaning a bit
            val m = MeshData()
            val lean = Mat.trs(0f, 0f, 0f, rz = 0.22f)
            val parts = MeshData()
            Prims.cylinder(parts, 0.8f, 0.8f, 9f, c(0xffc107), Mat.trs(0f, 6.5f, 0f), 6)
            Prims.cone(parts, 0.8f, 1.6f, c(0xf5d6a8), Mat.trs(0f, 1.2f, 0f, rx = PI_F), 6)
            if (lod == 0) {
                Prims.cone(parts, 0.33f, 0.6f, c(0x37474f), Mat.trs(0f, 0.7f, 0f, rx = PI_F), 6)
                Prims.cylinder(parts, 0.84f, 0.84f, 0.8f, c(0xb0bec5), Mat.trs(0f, 11.4f, 0f), 8, false)
            }
            Prims.cylinder(parts, 0.78f, 0.78f, 1.1f, c(0xff8a9e), Mat.trs(0f, 12.35f, 0f), 8)
            m.append(parts, lean)
            SceneryModel(m, radius = 2.2f)
        },
        "crayon" to { lod ->
            val m = MeshData(); val s = if (lod == 0) 8 else 5
            Prims.cylinder(m, 0.55f, 0.55f, 4.5f, Rgba.WHITE, Mat.trs(0f, 2.25f, 0f), s, false)
            if (lod == 0) {
                Prims.cylinder(m, 0.58f, 0.58f, 2.4f, Rgba(0.55f, 0.55f, 0.55f), Mat.trs(0f, 2.1f, 0f), s, false)
                Prims.cylinder(m, 0.55f, 0.55f, 0.05f, Rgba.WHITE, Mat.trs(0f, 0.02f, 0f), s)
            }
            Prims.cylinder(m, 0.12f, 0.55f, 1.2f, Rgba.WHITE, Mat.trs(0f, 5.1f, 0f), s)
            SceneryModel(m, radius = 0.8f, small = true, tints = listOf(0xff1744, 0x2979ff, 0x00c853, 0xaa00ff, 0xff9100, 0xffea00))
        },
        "toyHouse" to { lod ->
            // a chunky toy house with a pyramid roof, door, windows and chimney
            val m = MeshData()
            Prims.box(m, 8f, 6f, 8f, c(0xfff3e0), Mat.trs(0f, 3f, 0f))
            Prims.cone(m, 6.6f, 4.5f, c(0xff5252), Mat.trs(0f, 8.25f, 0f, ry = PI_F / 4), 4)
            if (lod == 0) {
                Prims.box(m, 1.8f, 3f, 0.3f, c(0x6d4c41), Mat.trs(0f, 1.5f, 4.05f))
                for (x in floatArrayOf(-2.6f, 2.6f)) Prims.box(m, 1.5f, 1.5f, 0.3f, c(0x81d4fa), Mat.trs(x, 3.8f, 4.05f))
                for (z in floatArrayOf(-2f, 2f)) Prims.box(m, 0.3f, 1.5f, 1.5f, c(0x81d4fa), Mat.trs(4.05f, 3.8f, z))
                Prims.box(m, 1.1f, 3f, 1.1f, c(0x8d6e63), Mat.trs(2.2f, 9f, -1.2f))
            }
            SceneryModel(m, radius = 6f, tints = listOf(0xffffff, 0xb3e5fc, 0xfff59d, 0xc8e6c9, 0xf8bbd0))
        },
        "teddy" to { lod ->
            // a sitting teddy bear (original, no character)
            val m = MeshData(); val s = if (lod == 0) 8 else 5; val hs = if (lod == 0) 5 else 3
            val fur = c(0xb07a4a); val pale = c(0xf1d1a8)
            Prims.sphere(m, 1.5f, fur, Mat.trs(0f, 1.5f, 0f, sy = 1.1f), s, hs)
            Prims.sphere(m, 1.15f, fur, Mat.trs(0f, 3.6f, 0.2f), s, hs)
            if (lod == 0) {
                for (x in floatArrayOf(-0.85f, 0.85f)) {
                    Prims.sphere(m, 0.42f, fur, Mat.trs(x, 4.55f, 0.1f), 4, 3)
                    Prims.sphere(m, 0.6f, fur, Mat.trs(x * 1.1f, 0.5f, 1.1f, sz = 1.3f), 6, 4)
                    Prims.box(m, 0.18f, 0.28f, 0.1f, c(0x212121), Mat.trs(x * 0.45f, 3.85f, 1.3f))
                }
                Prims.sphere(m, 0.5f, pale, Mat.trs(0f, 3.35f, 1.15f, sy = 0.75f), 4, 3)
            }
            SceneryModel(m, radius = 1.8f, small = true, tints = listOf(0xffffff, 0xffe0b2, 0xd7ccc8, 0xffcdd2))
        },

        // ------------------------------------------------------------------ Rainbow Sky
        "cloudPuff" to { lod ->
            val m = MeshData()
            if (lod == 0) {
                Prims.sphere(m, 3.2f, Rgba.WHITE, Mat.trs(0f, 0f, 0f, sy = 0.75f), 7, 5)
                Prims.sphere(m, 2.4f, Rgba.WHITE, Mat.trs(2.9f, -0.4f, 0.4f, sy = 0.8f), 7, 5)
                Prims.sphere(m, 2.2f, Rgba.WHITE, Mat.trs(-2.8f, -0.5f, -0.3f, sy = 0.8f), 7, 5)
                Prims.sphere(m, 2.0f, Rgba.WHITE, Mat.trs(0.6f, 1.6f, -0.2f), 7, 5)
            } else Prims.sphere(m, 4.2f, Rgba.WHITE, Mat.trs(0f, 0f, 0f, sx = 1.4f, sy = 0.65f), 6, 4)
            SceneryModel(m, radius = 5.5f, tints = listOf(0xffffff, 0xffffff, 0xffeef8, 0xeef6ff, 0xfff8e8))
        },
        "rainbow" to { lod ->
            // a glowing arc of six bands with a fluffy cloud at each foot
            val m = MeshData(); val glow = MeshData()
            val bands = intArrayOf(0xff1744, 0xff9100, 0xffea00, 0x00e676, 0x2979ff, 0xaa00ff)
            for ((i, b) in bands.withIndex()) Prims.torus(glow, 46f - i * 1.6f, 0.85f, g(b, 0.42f), Mat.trs(0f, 0f, 0f), 4, 32, PI_F)
            for (x in floatArrayOf(-42f, 42f)) {
                Prims.sphere(m, 7f, Rgba.WHITE, Mat.trs(x, 1f, 0f, sy = 0.7f), if (lod == 0) 9 else 6, if (lod == 0) 6 else 4)
                if (lod == 0) {
                    Prims.sphere(m, 5f, Rgba.WHITE, Mat.trs(x + 5.5f, 0f, 1.5f, sy = 0.75f), 8, 5)
                    Prims.sphere(m, 5f, Rgba.WHITE, Mat.trs(x - 5.5f, 0f, -1.5f, sy = 0.75f), 8, 5)
                }
            }
            SceneryModel(m, glow, radius = 50f)
        },
        "skyStar" to { lod ->
            // a floating, glowing five-point star with a happy face
            val m = MeshData(); val glow = MeshData()
            val gold = g(0xffd54f, 0.5f)
            Prims.sphere(glow, 1.1f, gold, Mat.trs(0f, 0f, 0f, sz = 0.6f), 6, 4)
            for (k in 0 until 5) {
                val a = P2 + k * 2 * PI_F / 5
                Prims.cone(glow, 0.75f, 1.8f, gold, Mat.trs(cos(a) * 1.5f, sin(a) * 1.5f, 0f, rz = a - P2, sz = 0.6f), 4)
            }
            if (lod == 0) {
                for (x in floatArrayOf(-0.35f, 0.35f)) Prims.box(m, 0.16f, 0.32f, 0.1f, c(0x3e2723), Mat.trs(x, 0.2f, 0.68f))
                Prims.torus(m, 0.32f, 0.06f, c(0x3e2723), Mat.trs(0f, -0.15f, 0.66f, rz = PI_F), 3, 6, PI_F)
            }
            SceneryModel(m, glow, radius = 2.4f)
        },
        "cloudCastle" to { lod ->
            // a pastel castle sitting on a big cloud, for the far distance
            val m = MeshData(); val s = if (lod == 0) 10 else 6
            Prims.sphere(m, 22f, Rgba.WHITE, Mat.trs(0f, 0f, 0f, sy = 0.4f), s + 2, if (lod == 0) 6 else 4)
            if (lod == 0) for (k in 0 until 5) { val a = k * 1.256f; Prims.sphere(m, 9f, Rgba.WHITE, Mat.trs(cos(a) * 17f, 1f, sin(a) * 17f, sy = 0.6f), 7, 4) }
            Prims.box(m, 16f, 12f, 12f, c(0xf8bbd0), Mat.trs(0f, 12f, 0f))
            for ((x, z) in listOf(-9f to -7f, 9f to -7f, -9f to 7f, 9f to 7f, 0f to 0f)) {
                val h = if (x == 0f) 24f else 18f
                Prims.cylinder(m, 3f, 3f, h, c(0xfff3e0), Mat.trs(x, 6f + h / 2, z), s, false)
                Prims.cone(m, 3.8f, 6f, c(0xb388ff), Mat.trs(x, 6f + h + 3f, z), s)
            }
            if (lod == 0) Prims.box(m, 4f, 6f, 0.4f, c(0x7e57c2), Mat.trs(0f, 9f, 6.1f))
            SceneryModel(m, radius = 24f)
        },
    )
}
