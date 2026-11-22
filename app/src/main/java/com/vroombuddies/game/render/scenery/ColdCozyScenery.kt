package com.vroombuddies.game.render

import kotlin.math.*

/**
 * Track batch D, "Cold & cozy": Snowy Peaks, Frozen Lake and Autumn Woods.
 *
 * Batch ids from docs/ARCHITECTURE.md: snowPine, snowman, iceberg, penguin, igloo, autumnTree, pumpkin, hayBale.
 * Extra ids only these three themes use: snowCabin, snowDrift, sled, icePond, iceSkater, snowFort,
 * leafPile, toadstool, scarecrow, redBarn, woodFence, appleCart.
 *
 * Every model is a soft, rounded cartoon shape. Near models stay at or under ~400 triangles, far ones are a handful
 * of low-poly blobs. Colours that get tinted per instance are built white, everything else uses fixed colours.
 */
object ColdCozyScenery {
    private fun c(h: Int) = Rgba.hex(h)
    private val P2 = (PI / 2).toFloat()
    private val PIf = PI.toFloat()

    private val SNOW = 0xf7fbff
    private val SNOW_SHADE = 0xdfeaf7
    private val BARK = 0x6d4527
    private val COAL = 0x26262e

    val pack: SceneryPack = mapOf(
        // ---------------------------------------------------------------- snow
        "snowPine" to { lod ->
            // a chubby three-tier fir with a dollop of snow on every tier
            val m = MeshData(); val s = if (lod == 0) 9 else 5
            Prims.cylinder(m, 0.45f, 0.65f, 2.6f, c(BARK), Mat.trs(0f, 1.3f, 0f), if (lod == 0) 7 else 5)
            val green = c(0x2f7d4f)
            val tiers = arrayOf(floatArrayOf(4.4f, 5.6f, 4.6f), floatArrayOf(3.4f, 4.8f, 7.4f), floatArrayOf(2.3f, 3.8f, 9.9f))
            for (t in tiers) {
                Prims.cone(m, t[0], t[1], green, Mat.trs(0f, t[2], 0f), s)
                if (lod == 0) Prims.cone(m, t[0] * 0.55f, t[1] * 0.55f, c(SNOW), Mat.trs(0f, t[2] + t[1] * 0.25f, 0f, sx = 1.05f, sz = 1.05f), s)
            }
            if (lod == 0) Prims.sphere(m, 0.45f, c(SNOW), Mat.trs(0f, 11.9f, 0f), 6, 4)
            m.windByHeight(0.15f)
            SceneryModel(m, radius = 4.4f, tints = listOf(0xffffff, 0xeaf6ff, 0xf2fff2, 0xe6f0e6))
        },
        "snowman" to { lod ->
            // a smiling three-ball snowman with a top hat, carrot nose, stick arms and a scarf
            val m = MeshData()
            if (lod == 0) {
                Prims.sphere(m, 1.3f, c(SNOW), Mat.trs(0f, 1.15f, 0f, sy = 0.92f), 8, 5)
                Prims.sphere(m, 0.95f, c(SNOW), Mat.trs(0f, 2.75f, 0f), 8, 5)
                Prims.sphere(m, 0.68f, c(SNOW), Mat.trs(0f, 3.95f, 0f), 7, 5)
                // scarf
                Prims.cylinder(m, 0.62f, 0.72f, 0.28f, c(0xff4d4d), Mat.trs(0f, 3.42f, 0f), 8, false)
                // hat
                Prims.cylinder(m, 0.62f, 0.62f, 0.08f, c(COAL), Mat.trs(0f, 4.5f, 0f), 6)
                Prims.cylinder(m, 0.4f, 0.42f, 0.7f, c(COAL), Mat.trs(0f, 4.88f, 0f), 6)
                // face: coal eyes, carrot nose, coal smile
                for (sx in floatArrayOf(-1f, 1f)) Prims.box(m, 0.12f, 0.14f, 0.1f, c(COAL), Mat.trs(sx * 0.22f, 4.1f, 0.6f))
                Prims.cone(m, 0.11f, 0.6f, c(0xff7a1a), Mat.trs(0f, 3.92f, 0.88f, rx = P2), 5)
                for (k in -1..1) Prims.box(m, 0.1f, 0.08f, 0.08f, c(COAL), Mat.trs(k * 0.14f, 3.72f - (2 - abs(k)) * 0.04f, 0.62f))
                // buttons
                for (k in 0..1) Prims.box(m, 0.13f, 0.13f, 0.1f, c(COAL), Mat.trs(0f, 2.55f + k * 0.36f, 0.9f))
                // stick arms waving
                Prims.box(m, 1.3f, 0.09f, 0.09f, c(BARK), Mat.trs(-1.4f, 3.05f, 0f, rz = -0.5f))
                Prims.box(m, 1.3f, 0.09f, 0.09f, c(BARK), Mat.trs(1.4f, 3.05f, 0f, rz = 0.5f))
            } else {
                Prims.rock(m, 1.3f, c(SNOW), Mat.trs(0f, 1.2f, 0f))
                Prims.rock(m, 0.8f, c(SNOW), Mat.trs(0f, 3.1f, 0f), 4)
            }
            SceneryModel(m, radius = 1.6f, small = true)
        },
        "iceberg" to { lod ->
            // chunky frozen spires, pale blue and see-through looking
            val m = MeshData()
            Prims.rock(m, 3f, Rgba.WHITE, Mat.trs(0f, 3.2f, 0f, 0f, 0.4f, 0f, 1f, 1.5f, 0.9f), 2)
            Prims.rock(m, 2.1f, c(0xdff4ff), Mat.trs(2.4f, 2f, 0.8f, 0.2f, 1.1f, 0.1f, 0.9f, 1.7f, 0.9f), 5)
            if (lod == 0) {
                Prims.rock(m, 1.6f, c(0xc8ecff), Mat.trs(-2.2f, 1.2f, -0.6f, 0f, 2f, 0.2f, 1f, 1.3f, 1f), 7)
                Prims.rock(m, 2.6f, c(SNOW), Mat.trs(0f, 0.3f, 0f, sy = 0.25f), 11)
            }
            SceneryModel(m, radius = 4.5f, tints = listOf(0xbfe9ff, 0xa8dcff, 0xd2f1ff, 0x9fe6f0))
        },
        "penguin" to { lod ->
            // a round little penguin waving hello
            val m = MeshData()
            if (lod == 0) {
                Prims.sphere(m, 0.62f, c(0x1f2633), Mat.trs(0f, 0.85f, 0f, sx = 0.95f, sy = 1.25f, sz = 0.9f), 8, 5)
                Prims.sphere(m, 0.5f, c(0xfafafa), Mat.trs(0f, 0.8f, 0.22f, sx = 0.85f, sy = 1.1f, sz = 0.75f), 7, 4)
                Prims.sphere(m, 0.44f, c(0x1f2633), Mat.trs(0f, 1.72f, 0f), 8, 5)
                // eyes, beak and feet
                for (sx in floatArrayOf(-1f, 1f)) {
                    Prims.sphere(m, 0.11f, c(0xffffff), Mat.trs(sx * 0.16f, 1.8f, 0.36f), 4, 3)
                    Prims.box(m, 0.07f, 0.09f, 0.04f, c(0x111111), Mat.trs(sx * 0.16f, 1.81f, 0.47f))
                    Prims.box(m, 0.26f, 0.08f, 0.36f, c(0xffa21a), Mat.trs(sx * 0.2f, 0.06f, 0.18f, ry = sx * 0.3f))
                }
                Prims.cone(m, 0.09f, 0.26f, c(0xffa21a), Mat.trs(0f, 1.66f, 0.5f, rx = P2), 5)
                // flippers: one down, one up waving
                Prims.box(m, 0.12f, 0.7f, 0.32f, c(0x1f2633), Mat.trs(-0.6f, 0.95f, 0f, rz = -0.25f))
                Prims.box(m, 0.12f, 0.7f, 0.32f, c(0x1f2633), Mat.trs(0.68f, 1.45f, 0f, rz = 2.3f))
            } else {
                Prims.rock(m, 0.6f, c(0x1f2633), Mat.trs(0f, 0.9f, 0f, sy = 1.6f))
            }
            SceneryModel(m, radius = 0.9f, small = true)
        },
        "igloo" to { lod ->
            // a snow-block dome with a glowing, cozy doorway
            val m = MeshData(); val glow = MeshData()
            val ws = if (lod == 0) 14 else 8
            Prims.sphere(m, 4f, c(SNOW), Mat.trs(0f, 0f, 0f), ws, if (lod == 0) 6 else 3, P2)
            if (lod == 0) {
                // block seams as slightly bigger bands
                for ((y, r) in listOf(1.3f to 3.8f, 2.6f to 3.07f)) Prims.cylinder(m, r * 1.02f, r * 1.02f, 0.12f, c(SNOW_SHADE), Mat.trs(0f, y, 0f), 14, false)
                // tunnel entrance
                Prims.cylinder(m, 1.5f, 1.5f, 2.4f, c(SNOW), Mat.trs(0f, 0f, 3.8f, rx = P2), 10, false, PIf)
                Prims.cylinder(m, 1.62f, 1.62f, 0.12f, c(SNOW_SHADE), Mat.trs(0f, 0f, 4.9f, rx = P2), 10, false, PIf)
                Prims.box(glow, 1.9f, 1.25f, 0.05f, Rgba(1f, 0.62f, 0.25f), Mat.trs(0f, 0.62f, 4.6f))
                // a little flag on top
                Prims.cylinder(m, 0.05f, 0.05f, 1.6f, c(0x8a8a8a), Mat.trs(0f, 4.6f, 0f), 4, false)
                Prims.box(m, 0.04f, 0.5f, 0.8f, c(0xff4d4d), Mat.trs(0f, 5.1f, 0.4f))
            }
            SceneryModel(m, glow, radius = 5f)
        },
        "snowCabin" to { lod ->
            // a log cabin with a snowy roof, a chimney and warm glowing windows
            val m = MeshData(); val glow = MeshData()
            Prims.box(m, 7f, 4f, 5.5f, c(0x8d5a34), Mat.trs(0f, 2f, 0f))
            for (sx in floatArrayOf(-1f, 1f)) Prims.box(m, 4.5f, 0.4f, 6.6f, c(SNOW), Mat.trs(sx * 1.85f, 5.35f, 0f, rz = -sx * 0.62f))
            Prims.box(m, 0.9f, 2.4f, 0.9f, c(0x9e9e9e), Mat.trs(2f, 6.4f, -1f))
            if (lod == 0) {
                Prims.box(m, 1.0f, 0.25f, 1.0f, c(SNOW), Mat.trs(2f, 7.7f, -1f))
                // log lines
                for (k in 0 until 4) Prims.box(m, 7.1f, 0.12f, 5.6f, c(0x6d4527), Mat.trs(0f, 0.6f + k * 0.95f, 0f))
                Prims.box(m, 1.4f, 2.6f, 0.1f, c(0x5b3a22), Mat.trs(0f, 1.3f, 2.8f))
                for (sx in floatArrayOf(-1f, 1f)) {
                    Prims.box(glow, 1.3f, 1.1f, 0.06f, Rgba(1f, 0.72f, 0.3f), Mat.trs(sx * 2.3f, 2.4f, 2.78f))
                    Prims.box(m, 1.6f, 0.18f, 0.25f, c(SNOW), Mat.trs(sx * 2.3f, 1.75f, 2.85f))
                }
            }
            SceneryModel(m, glow, radius = 5f)
        },
        "snowDrift" to { _ ->
            // soft lumpy snow banks
            val m = MeshData()
            Prims.rock(m, 2.2f, c(SNOW), Mat.trs(0f, 0.2f, 0f, sx = 1.4f, sy = 0.38f, sz = 1f), 3)
            Prims.rock(m, 1.5f, c(SNOW_SHADE), Mat.trs(1.9f, 0.15f, 0.6f, sy = 0.4f), 8)
            Prims.rock(m, 1.2f, c(SNOW), Mat.trs(-1.8f, 0.1f, -0.4f, sy = 0.45f), 13)
            SceneryModel(m, radius = 3f, small = true)
        },
        "sled" to { _ ->
            // a bright red sled with curly runners
            val m = MeshData()
            Prims.box(m, 1.2f, 0.18f, 2.4f, Rgba.WHITE, Mat.trs(0f, 0.5f, 0f))
            Prims.box(m, 1.2f, 0.4f, 0.12f, Rgba.WHITE, Mat.trs(0f, 0.75f, -1.15f))
            for (sx in floatArrayOf(-1f, 1f)) {
                Prims.box(m, 0.08f, 0.08f, 2.6f, c(0x9e9e9e), Mat.trs(sx * 0.5f, 0.1f, 0f))
                Prims.torus(m, 0.25f, 0.05f, c(0x9e9e9e), Mat.trs(sx * 0.5f, 0.35f, 1.3f, ry = P2), 3, 6, PIf)
                Prims.box(m, 0.08f, 0.38f, 0.08f, c(0x9e9e9e), Mat.trs(sx * 0.5f, 0.3f, -0.6f))
                Prims.box(m, 0.08f, 0.38f, 0.08f, c(0x9e9e9e), Mat.trs(sx * 0.5f, 0.3f, 0.6f))
            }
            SceneryModel(m, radius = 1.4f, small = true, tints = listOf(0xff3b3b, 0x2d7fff, 0x2ecc71, 0xffb300))
        },
        "icePond" to { lod ->
            // a shiny frozen pond ringed with snow
            val m = MeshData()
            Prims.cylinder(m, 10f, 10f, 0.1f, c(0x9fdcf5), Mat.trs(0f, 0.06f, 0f, sz = 0.75f), if (lod == 0) 18 else 10)
            if (lod == 0) {
                // light streaks on the ice
                for (k in 0 until 3) Prims.box(m, 0.35f, 0.02f, 5f - k, c(0xe8f8ff), Mat.trs(-3f + k * 2.6f, 0.13f, 0.5f, ry = 0.7f))
                for (k in 0 until 7) { val a = k * 0.9f; Prims.rock(m, 1.4f, c(SNOW), Mat.trs(cos(a) * 10.2f, 0.1f, sin(a) * 7.7f, sy = 0.4f), k + 2) }
            }
            SceneryModel(m, radius = 10f)
        },
        "iceSkater" to { lod ->
            // a penguin gliding on skates in a bobble hat
            val m = MeshData()
            Prims.sphere(m, 0.6f, c(0x1f2633), Mat.trs(0f, 1.0f, 0f, sx = 0.95f, sy = 1.25f, sz = 0.9f, rx = 0.25f), if (lod == 0) 8 else 5, if (lod == 0) 5 else 3)
            if (lod == 0) {
                Prims.sphere(m, 0.48f, c(0xfafafa), Mat.trs(0f, 0.98f, 0.24f, sx = 0.85f, sy = 1.1f, sz = 0.75f, rx = 0.25f), 7, 4)
                Prims.sphere(m, 0.42f, c(0x1f2633), Mat.trs(0f, 1.85f, 0.25f), 8, 5)
                Prims.sphere(m, 0.44f, c(0xff4d6d), Mat.trs(0f, 2.05f, 0.22f), 7, 3, P2)
                Prims.sphere(m, 0.16f, c(0xffffff), Mat.trs(0f, 2.52f, 0.22f), 4, 3)
                for (sx in floatArrayOf(-1f, 1f)) {
                    Prims.box(m, 0.09f, 0.11f, 0.05f, c(0x111111), Mat.trs(sx * 0.15f, 1.92f, 0.64f))
                    Prims.box(m, 0.06f, 0.06f, 0.7f, c(0xc0c8d0), Mat.trs(sx * 0.2f, 0.05f, 0.1f))
                    Prims.box(m, 0.22f, 0.22f, 0.4f, c(0xffffff), Mat.trs(sx * 0.2f, 0.2f, 0.1f))
                    Prims.box(m, 0.9f, 0.12f, 0.3f, c(0x1f2633), Mat.trs(sx * 0.85f, 1.35f, 0f, rz = sx * 0.4f))
                }
                Prims.cone(m, 0.09f, 0.24f, c(0xffa21a), Mat.trs(0f, 1.8f, 0.75f, rx = P2), 5)
            }
            SceneryModel(m, radius = 1.2f, small = true)
        },
        "snowFort" to { lod ->
            // a kid-built snow wall with a pile of snowballs
            val m = MeshData()
            for (k in -2..2) {
                val t = Mat.trs(k * 1.55f, 0.6f, 0f, ry = k * 0.12f)
                if (lod == 0) Prims.roundBox(m, 1.6f, 1.2f, 1.2f, 0.3f, c(if (k % 2 == 0) SNOW else SNOW_SHADE), t, 1)
                else Prims.box(m, 1.6f, 1.2f, 1.2f, c(SNOW), t)
            }
            if (lod == 0) {
                for (k in -1..1) Prims.box(m, 1.4f, 0.8f, 1.0f, c(if (k == 0) SNOW_SHADE else SNOW), Mat.trs(k * 1.6f + 0.6f, 1.6f, 0f, ry = k * 0.1f, rz = k * 0.04f))
                for (k in 0 until 3) Prims.sphere(m, 0.32f, c(SNOW), Mat.trs(-1f + k * 0.62f, 0.3f + (if (k == 1) 0.45f else 0f), 1.2f), 5, 3)
            }
            SceneryModel(m, radius = 4f, small = true)
        },

        // -------------------------------------------------------------- autumn
        "autumnTree" to { lod ->
            // a big round tree in fall colours (tinted orange, red or gold)
            val m = MeshData(); val s = if (lod == 0) 7 else 5
            Prims.cylinder(m, 0.4f, 0.65f, 5f, c(0x5b3a22), Mat.trs(0f, 2.5f, 0f), s)
            if (lod == 0) {
                Prims.cylinder(m, 0.16f, 0.22f, 2.2f, c(0x5b3a22), Mat.trs(0.9f, 4.6f, 0f, rz = -0.7f), 5, false)
                Prims.sphere(m, 3.2f, Rgba.WHITE, Mat.trs(0f, 6.6f, 0f), 10, 7)
                Prims.sphere(m, 2.3f, Rgba(1.05f, 1.05f, 0.9f), Mat.trs(1.8f, 7.6f, 0.5f), 8, 6)
                Prims.sphere(m, 2.1f, Rgba(0.85f, 0.85f, 0.85f), Mat.trs(-1.5f, 6.1f, -0.9f), 8, 6)
            } else Prims.rock(m, 3.5f, Rgba.WHITE, Mat.trs(0f, 6.8f, 0f))
            m.windByHeight(0.35f)
            SceneryModel(m, radius = 4f, tints = listOf(0xff8f00, 0xf4511e, 0xd84315, 0xffc107, 0xc62828, 0xffa726))
        },
        "pumpkin" to { lod ->
            // a fat ribbed pumpkin with a friendly glowing face
            val m = MeshData(); val glow = MeshData()
            if (lod == 0) {
                for (k in 0 until 5) {
                    val a = k * 1.2566f
                    Prims.sphere(m, 0.62f, Rgba.WHITE, Mat.trs(sin(a) * 0.34f, 0.62f, cos(a) * 0.34f, ry = a, sx = 0.75f, sy = 0.95f), 7, 5)
                }
                Prims.cylinder(m, 0.08f, 0.12f, 0.45f, c(0x4e7a2a), Mat.trs(0f, 1.32f, 0f, rz = 0.25f), 5)
                Prims.box(m, 0.45f, 0.04f, 0.28f, c(0x5aa83c), Mat.trs(0.25f, 1.2f, 0f, rz = -0.3f))
                // happy face: round eyes and a smile
                for (sx in floatArrayOf(-1f, 1f)) Prims.sphere(glow, 0.12f, Rgba(1f, 0.85f, 0.3f), Mat.trs(sx * 0.24f, 0.8f, 0.82f, sz = 0.4f), 6, 4)
                for (k in -2..2) Prims.box(glow, 0.14f, 0.1f, 0.06f, Rgba(1f, 0.85f, 0.3f), Mat.trs(k * 0.12f, 0.45f + abs(k) * abs(k) * 0.025f, 0.86f - abs(k) * 0.03f))
            } else {
                Prims.rock(m, 0.8f, Rgba.WHITE, Mat.trs(0f, 0.6f, 0f, sy = 0.75f))
            }
            SceneryModel(m, glow, radius = 1f, small = true, tints = listOf(0xff8f00, 0xfb8c00, 0xffa726, 0xf57c00))
        },
        "hayBale" to { lod ->
            // round bales: two side by side with one on top
            val m = MeshData(); val s = if (lod == 0) 12 else 7
            val straw = Rgba.WHITE
            Prims.cylinder(m, 0.9f, 0.9f, 1.5f, straw, Mat.trs(-0.95f, 0.9f, 0f, rx = P2), s)
            Prims.cylinder(m, 0.9f, 0.9f, 1.5f, straw, Mat.trs(0.95f, 0.9f, 0f, rx = P2), s)
            Prims.cylinder(m, 0.9f, 0.9f, 1.5f, straw, Mat.trs(0f, 2.45f, 0.1f, rx = P2), s)
            if (lod == 0) for (x in floatArrayOf(-0.95f, 0.95f)) Prims.cylinder(m, 0.92f, 0.92f, 0.1f, Rgba(0.75f, 0.6f, 0.35f), Mat.trs(x, 0.9f, 0f, rx = P2), s, false)
            SceneryModel(m, radius = 2f, tints = listOf(0xf2c75c, 0xe8b84a, 0xf5d27a))
        },
        "leafPile" to { _ ->
            // crunchy piles of fallen leaves
            val m = MeshData()
            Prims.rock(m, 1.4f, Rgba.WHITE, Mat.trs(0f, 0.2f, 0f, sx = 1.3f, sy = 0.45f, sz = 1f), 4)
            Prims.rock(m, 0.9f, Rgba(1.1f, 0.9f, 0.6f), Mat.trs(1.1f, 0.15f, 0.5f, sy = 0.5f), 9)
            Prims.rock(m, 0.8f, Rgba(0.9f, 0.6f, 0.4f), Mat.trs(-1f, 0.12f, -0.3f, sy = 0.5f), 14)
            SceneryModel(m, radius = 2f, small = true, tints = listOf(0xff8f00, 0xe65100, 0xffb300, 0xd84315))
        },
        "toadstool" to { lod ->
            // a red-and-white spotted mushroom, kid sized
            val m = MeshData()
            Prims.cylinder(m, 0.32f, 0.45f, 1.2f, c(0xfff3e0), Mat.trs(0f, 0.6f, 0f), if (lod == 0) 8 else 5)
            Prims.sphere(m, 1.15f, c(0xe53935), Mat.trs(0f, 1.1f, 0f, sy = 0.7f), if (lod == 0) 10 else 6, if (lod == 0) 4 else 2, P2)
            if (lod == 0) for (k in 0 until 5) {
                val a = k * 1.2566f
                Prims.sphere(m, 0.2f, c(0xffffff), Mat.trs(sin(a) * 0.72f, 1.62f, cos(a) * 0.72f, sy = 0.5f), 5, 3)
            }
            SceneryModel(m, radius = 1.3f, small = true)
        },
        "scarecrow" to { lod ->
            // a friendly scarecrow: pumpkin-coloured face, patched shirt, straw hat
            val m = MeshData()
            Prims.cylinder(m, 0.1f, 0.1f, 4f, c(0x8d6e63), Mat.trs(0f, 2f, 0f), 5)
            Prims.box(m, 3f, 0.16f, 0.16f, c(0x8d6e63), Mat.trs(0f, 3.0f, 0f))
            Prims.roundBox(m, 1.3f, 1.3f, 0.6f, 0.2f, c(0x4caf50), Mat.trs(0f, 2.75f, 0f), 1)
            Prims.sphere(m, 0.5f, c(0xffe0a8), Mat.trs(0f, 3.9f, 0f), if (lod == 0) 8 else 5, if (lod == 0) 6 else 3)
            Prims.cylinder(m, 0.8f, 0.8f, 0.06f, c(0xe8c25a), Mat.trs(0f, 4.25f, 0f), 8)
            Prims.cone(m, 0.45f, 0.6f, c(0xe8c25a), Mat.trs(0f, 4.55f, 0f), 8)
            if (lod == 0) {
                for (sx in floatArrayOf(-1f, 1f)) {
                    Prims.box(m, 0.1f, 0.1f, 0.06f, c(COAL), Mat.trs(sx * 0.17f, 4.0f, 0.47f))
                    Prims.cone(m, 0.18f, 0.5f, c(0xf2c75c), Mat.trs(sx * 1.55f, 3.0f, 0f, rz = sx * P2), 5)
                }
                for (k in -2..2) Prims.box(m, 0.08f, 0.06f, 0.06f, c(0xb5402a), Mat.trs(k * 0.09f, 3.75f + abs(k) * 0.02f, 0.47f))
                Prims.box(m, 0.4f, 0.4f, 0.05f, c(0x3f51b5), Mat.trs(0.3f, 2.5f, 0.32f, rz = 0.2f))
            }
            SceneryModel(m, radius = 1.6f)
        },
        "redBarn" to { lod ->
            // a big red barn with a white trimmed door and hayloft
            val m = MeshData()
            Prims.box(m, 12f, 7f, 9f, c(0xc62828), Mat.trs(0f, 3.5f, 0f))
            for (sx in floatArrayOf(-1f, 1f)) Prims.box(m, 7.4f, 0.5f, 9.8f, c(0x5d4037), Mat.trs(sx * 3.1f, 9.15f, 0f, rz = -sx * 0.6f))
            if (lod == 0) {
                Prims.box(m, 4f, 4.8f, 0.2f, c(0x8e1b1b), Mat.trs(0f, 2.4f, 4.55f))
                for (sx in floatArrayOf(-1f, 1f)) {
                    Prims.box(m, 0.25f, 6f, 0.15f, c(0xffffff), Mat.trs(0f, 2.4f, 4.7f, rz = sx * 0.7f))
                    Prims.box(m, 0.3f, 7f, 0.3f, c(0xffffff), Mat.trs(sx * 6f, 3.5f, 4.5f))
                }
                Prims.box(m, 4.4f, 0.3f, 0.3f, c(0xffffff), Mat.trs(0f, 4.9f, 4.65f))
                Prims.box(m, 2f, 1.6f, 0.2f, c(0x3e2723), Mat.trs(0f, 7.6f, 4.55f))
                Prims.cylinder(m, 1.6f, 1.6f, 10f, c(0xb0bec5), Mat.trs(8.6f, 5f, -1f), 10)
                Prims.sphere(m, 1.6f, c(0x90a4ae), Mat.trs(8.6f, 10f, -1f), 10, 3, P2)
            }
            SceneryModel(m, radius = 10f)
        },
        "woodFence" to { _ ->
            // a short rustic fence along the road
            val m = MeshData()
            for (k in -2..2) Prims.box(m, 0.25f, 1.6f, 0.25f, c(0x8d6e63), Mat.trs(k * 2.2f, 0.8f, 0f))
            Prims.box(m, 9.2f, 0.22f, 0.12f, c(0xa1887f), Mat.trs(0f, 1.2f, 0.12f))
            Prims.box(m, 9.2f, 0.22f, 0.12f, c(0xa1887f), Mat.trs(0f, 0.6f, 0.12f))
            SceneryModel(m, radius = 5f, small = true, trackside = true)
        },
        "appleCart" to { lod ->
            // a wooden cart heaped with shiny apples
            val m = MeshData()
            Prims.box(m, 2.6f, 0.8f, 1.6f, c(0x8d5a34), Mat.trs(0f, 1.0f, 0f))
            for (sx in floatArrayOf(-1f, 1f)) Prims.cylinder(m, 0.55f, 0.55f, 0.15f, c(0x5b3a22), Mat.trs(sx * 0.9f, 0.55f, 0.85f, rx = P2), if (lod == 0) 10 else 6)
            Prims.box(m, 1.6f, 0.1f, 0.1f, c(0x5b3a22), Mat.trs(-2f, 1f, 0f))
            if (lod == 0) {
                for (k in 0 until 8) Prims.sphere(m, 0.26f, c(0xe53935), Mat.trs(-0.9f + (k % 4) * 0.6f, 1.55f + (k / 4) * 0.12f, -0.35f + (k / 4) * 0.6f), 5, 3)
                Prims.sphere(m, 0.26f, c(0x8bc34a), Mat.trs(0.1f, 1.85f, 0f), 5, 3)
            }
            SceneryModel(m, radius = 1.8f, small = true)
        },
    )
}
