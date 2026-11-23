package com.vroombuddies.game.content

import com.vroombuddies.game.sim.KnockKind
import com.vroombuddies.game.sim.TrackShape

/**
 * Scenery spawn rule. [id] names a builder in [com.vroombuddies.game.render.SceneryLibrary].
 * Distances are metres from the road edge. yMin/yMax lift objects off the ground (floating asteroids, balloons).
 */
data class PropRule(val id: String, val count: Int, val minDist: Float, val maxDist: Float, val scaleMin: Float = 1f, val scaleMax: Float = 1f, val yMin: Float = 0f, val yMax: Float = 0f)

data class Theme(
    val name: String,
    val emoji: String,
    val skyTop: Int, val skyHorizon: Int, val skyBottom: Int,
    val sunColor: Int, val sunIntensity: Float, val sunElevation: Float, val sunAzimuth: Float,
    val ambient: Float,                  // sky light (IBL) strength
    val fogColor: Int, val fogDistance: Float, val fogDensity: Float,
    val ground: Pair<Int, Int>?,         // null = no ground (space / sky tracks float)
    val road: Int, val kerbs: Pair<Int, Int>, val walls: Pair<Int, Int>, val glowWalls: Boolean = false,
    val skirt: Int,
    val props: List<PropRule>,
    val knock: List<String>,
    val shape: TrackShape,
    val gravity: Float = 30f,
    val night: Boolean = false,
    val water: Int? = null,
    val grip: Float = 2.0f,
    // colour grading
    val saturation: Float = 1.15f, val contrast: Float = 1.05f, val warmth: Float = 0f,
)

object Knock {
    val tires = KnockKind("tires", 1.1f, 0.76f, 0.5f, 3, 6)
    val cone = KnockKind("cone", 0.7f, 1.6f, 0.2f, 1, 1, row = 5)
    val hay = KnockKind("hay", 1.2f, 1.2f, 0.6f, 2, 3)
    val barrel = KnockKind("barrel", 0.85f, 1.6f, 0.5f, 1, 2)
    val crate = KnockKind("crate", 0.95f, 1.6f, 0.6f, 2, 3)
    val all = listOf(tires, cone, hay, barrel, crate).associateBy { it.id }
    fun kinds(ids: List<String>) = ids.mapNotNull { all[it] }.ifEmpty { listOf(tires) }
}

private fun p(id: String, count: Int, minD: Float, maxD: Float, s0: Float = 1f, s1: Float = 1f, y0: Float = 0f, y1: Float = 0f) = PropRule(id, count, minD, maxD, s0, s1, y0, y1)

/**
 * The 20 tracks. Sunny Meadow is the fully built sample; the others have their colours and track
 * shapes set and list the scenery they want. Scenery ids without a builder yet are skipped.
 */
object Themes {
    val all = listOf(
        Theme("Sunny Meadow", "🌼", 0x3f8fe0, 0xbfe6ff, 0x9cc46a, 0xfff2d6, 3.4f, 0.9f, 0.6f, 1.0f, 0xcfeaff, 120f, 0.0012f,
            0x6cc24a to 0x5aa83c, 0x55565e, 0xff4d4d to 0xffffff, 0xff4d4d to 0xffffff, skirt = 0x7cb850,
            props = listOf(p("roundTree", 160, 8f, 220f, 0.8f, 1.4f), p("pine", 90, 14f, 260f, 0.8f, 1.3f), p("flower", 700, 3f, 110f, 0.8f, 1.6f),
                p("bush", 260, 3f, 160f, 0.7f, 1.5f), p("rock", 80, 6f, 200f, 0.6f, 1.8f), p("windmill", 8, 60f, 300f, 1f, 1.3f),
                p("balloon", 14, 40f, 260f, 1f, 1.4f, 25f, 70f), p("grandstand", 3, 3f, 4f), p("flagRow", 30, 2f, 3f), p("mountain", 26, 700f, 1300f, 0.8f, 1.6f)),
            knock = listOf("tires", "hay"), shape = TrackShape(11, 520f, 0.22f, 10f, 1.25f, 0.9f, 12, 3)),
        Theme("Desert Canyon", "🌵", 0x3a7bd5, 0xf6d39a, 0xd9a060, 0xffe0b0, 3.8f, 0.85f, 1.4f, 0.9f, 0xf3d1a1, 140f, 0.0011f,
            0xe2a35f to 0xd18e4b, 0x5d5552, 0xff8a00 to 0xffffff, 0xff8a00 to 0xffffff, skirt = 0xb4672e,
            props = listOf(p("cactus", 300, 5f, 240f, 0.8f, 1.6f), p("barrelCactus", 320, 2f, 120f, 0.7f, 1.5f), p("rock", 240, 4f, 260f, 0.6f, 2.2f),
                p("tumbleweed", 110, 3f, 150f, 0.8f, 1.3f), p("rockArch", 16, 18f, 220f, 0.9f, 1.5f), p("waterTower", 12, 20f, 200f),
                p("grandstand", 2, 3f, 4f), p("flagRow", 24, 2f, 3f), p("mesa", 40, 260f, 900f, 0.7f, 1.7f), p("mountain", 20, 900f, 1400f)),
            knock = listOf("tires", "barrel", "crate"), shape = TrackShape(23, 600f, 0.3f, 14f, 1.4f, 0.9f, 14, 5), warmth = 0.15f),
        Theme("Space Station", "🚀", 0x05031a, 0x1d0f4a, 0x05031a, 0xe8e4ff, 3.0f, 0.7f, 0.4f, 0.7f, 0x120a33, 300f, 0.0005f,
            null, 0x2b2c44, 0x00e5ff to 0xff3df2, 0x00e5ff to 0xff3df2, glowWalls = true, skirt = 0x3b3f63,
            props = listOf(p("asteroid", 320, 10f, 300f, 0.6f, 3f, -60f, 60f), p("satellite", 60, 25f, 260f, 1f, 1.6f, 10f, 80f),
                p("station", 7, 160f, 520f, 1f, 1.6f, 30f, 160f), p("rocket", 34, 20f, 240f, 1f, 1.5f, 10f, 90f), p("ufo", 18, 30f, 240f, 1f, 1.4f, 15f, 70f),
                p("beacon", 150, 1.5f, 2f, 1f, 1f, 5.6f, 5.6f), p("starBit", 420, 6f, 220f, 0.7f, 1.6f, -40f, 80f), p("planet", 9, 500f, 1300f, 1f, 2.2f, 50f, 400f)),
            knock = listOf("tires", "crate"), shape = TrackShape(37, 640f, 0.25f, 40f, 1.25f, 1.1f, 15, 5), gravity = 15f, night = true, saturation = 1.25f),
        Theme("Neon City", "🌃", 0x0b0624, 0x40206e, 0x2a1650, 0xa8b8ff, 1.4f, 0.8f, 2.2f, 0.7f, 0x2a1650, 100f, 0.0016f,
            0x2b2b38 to 0x26263a, 0x2f3040, 0xff2bd6 to 0x2bf5ff, 0xff2bd6 to 0x2bf5ff, glowWalls = true, skirt = 0x3a3a50,
            props = listOf(p("building", 260, 14f, 200f, 0.8f, 1.5f), p("building", 90, 260f, 700f, 1.6f, 3.2f), p("lamp", 170, 1.5f, 2f),
                p("trafficLight", 26, 1.5f, 2f), p("neonSign", 80, 8f, 90f, 1f, 1.5f), p("hydrant", 110, 2f, 14f, 0.9f, 1.2f),
                p("roundTree", 70, 4f, 12f, 0.5f, 0.75f), p("bush", 140, 2f, 12f, 0.6f, 1f)),
            knock = listOf("cone", "tires"), shape = TrackShape(41, 640f, 0.28f, 6f, 1.35f, 1.0f, 16, 4), night = true, contrast = 1.1f),
        Theme("Snowy Peaks", "❄️", 0x6fa8dc, 0xdbeeff, 0xf3f8ff, 0xffffff, 3.2f, 0.7f, 0.9f, 1.2f, 0xe6f2ff, 110f, 0.0013f,
            0xf3f8ff to 0xdde9f5, 0x5a6272, 0x2d7fff to 0xffffff, 0x2d7fff to 0xffffff, skirt = 0xdde9f5,
            props = listOf(p("snowPine", 320, 4f, 260f, 0.8f, 1.6f), p("snowman", 40, 4f, 120f), p("rock", 120, 4f, 200f, 0.6f, 1.8f), p("mountain", 30, 600f, 1200f, 1f, 1.9f)),
            knock = listOf("tires", "hay"), shape = TrackShape(53, 540f, 0.28f, 22f, 1.1f, 1.0f, 13, 4), grip = 1.7f),
        Theme("Sunny Beach", "🏖️", 0x1e88e5, 0xa8e6ff, 0xf4dc9a, 0xfff4d6, 3.6f, 1.0f, 0.3f, 1.0f, 0xc7ecff, 150f, 0.001f,
            0xf4dc9a to 0xeccf86, 0x5a5a62, 0x00b3ff to 0xffe14d, 0x00b3ff to 0xffe14d, skirt = 0xe0c27a,
            props = listOf(p("palm", 260, 4f, 210f, 0.8f, 1.5f), p("umbrella", 140, 3f, 110f, 0.9f, 1.2f), p("beachBall", 110, 2f, 90f),
                p("sandcastle", 50, 3f, 110f, 0.8f, 1.4f), p("surfboards", 60, 3f, 100f), p("lifeguardTower", 18, 6f, 120f), p("beachHut", 26, 10f, 160f),
                p("crab", 90, 2f, 80f), p("starfish", 160, 2f, 90f, 0.8f, 1.5f), p("rock", 60, 4f, 180f, 0.5f, 1.5f),
                p("grandstand", 2, 3f, 4f), p("flagRow", 26, 2f, 3f)),
            knock = listOf("tires", "barrel"), shape = TrackShape(67, 560f, 0.24f, 6f, 1.45f, 0.85f, 13, 4), water = 0x1fb2e0, saturation = 1.2f),
        Theme("Jungle Jam", "🌴", 0x2f9e8f, 0xc8f2c6, 0x3f8f2e, 0xfff2c0, 3.2f, 0.95f, 1.0f, 1.0f, 0xbfe8c0, 80f, 0.0018f,
            0x3f8f2e to 0x357d27, 0x5a4e46, 0xffb300 to 0x2e7d32, 0xffb300 to 0x2e7d32, skirt = 0x3f8f2e,
            props = listOf(p("jungleTree", 170, 6f, 220f, 0.9f, 1.5f), p("roundTree", 160, 4f, 220f, 0.9f, 1.8f), p("palm", 120, 4f, 200f, 0.9f, 1.5f),
                p("bigLeaf", 380, 2f, 120f, 0.8f, 1.6f), p("giantFlower", 120, 3f, 100f, 0.8f, 1.5f), p("bush", 240, 3f, 120f, 0.8f, 1.6f),
                p("fern", 200, 3f, 120f, 1f, 1.8f), p("tikiTotem", 30, 3f, 70f, 0.9f, 1.3f), p("jungleRuin", 14, 8f, 140f, 1f, 1.4f),
                p("parrot", 50, 3f, 60f), p("flower", 220, 3f, 80f), p("rock", 60, 4f, 160f, 0.6f, 1.6f, 0f, 0f), p("flagRow", 20, 2f, 3f)),
            knock = listOf("tires", "barrel"), shape = TrackShape(79, 540f, 0.32f, 18f, 1.05f, 1.2f, 15, 5), saturation = 1.22f),
        Theme("Volcano Run", "🌋", 0x3a0d0d, 0xff7043, 0x3b2a26, 0xffc08a, 3.0f, 0.6f, 2.0f, 0.8f, 0x8a3a26, 90f, 0.0017f,
            0x3b2a26 to 0x2b1d1a, 0x3b3438, 0xff3d00 to 0x222222, 0xff3d00 to 0x222222, glowWalls = true, skirt = 0x2b1f1c,
            props = listOf(p("lavaRock", 300, 4f, 240f, 0.6f, 2.2f), p("lavaPool", 50, 6f, 180f, 0.8f, 1.6f), p("obsidian", 180, 3f, 200f, 0.7f, 1.8f),
                p("burntTree", 140, 5f, 220f, 0.8f, 1.4f), p("rock", 120, 4f, 220f, 0.6f, 2f), p("flagRow", 20, 2f, 3f),
                p("volcano", 8, 350f, 900f, 1f, 1.8f), p("mountain", 20, 800f, 1400f)),
            knock = listOf("tires", "barrel"), shape = TrackShape(89, 580f, 0.26f, 24f, 1.2f, 1.0f, 14, 5), warmth = 0.3f),
        Theme("Sweet Treats", "🍭", 0xff7ac6, 0xffd1ec, 0x9be8b8, 0xfff0f8, 3.4f, 0.9f, 0.8f, 1.2f, 0xffd9ef, 120f, 0.0012f,
            0x9be8b8 to 0x8fe0ad, 0x8c5a7a, 0xff2d6f to 0xffffff, 0xff2d6f to 0xffffff, skirt = 0xffb6d9,
            props = listOf(p("lollipop", 170, 4f, 200f, 0.8f, 1.7f), p("candyCane", 140, 3f, 160f, 0.9f, 1.5f), p("gumdrop", 420, 2f, 160f, 0.7f, 1.8f),
                p("cupcake", 90, 3f, 140f, 0.9f, 1.6f), p("donut", 70, 3f, 140f, 0.9f, 1.5f), p("iceCream", 80, 3f, 150f, 0.9f, 1.6f),
                p("bigCake", 9, 160f, 600f, 1f, 1.6f), p("bush", 160, 3f, 120f, 0.6f, 1.2f), p("balloon", 16, 40f, 260f, 1f, 1.4f, 20f, 60f)),
            knock = listOf("tires", "cone"), shape = TrackShape(97, 600f, 0.3f, 16f, 1.25f, 1.0f, 14, 5), saturation = 1.3f),
        Theme("Moon Base", "🌙", 0x000000, 0x0d1030, 0xa7a7ad, 0xffffff, 3.6f, 0.5f, 0.7f, 0.6f, 0x0a0c1e, 400f, 0.0005f,
            0xa7a7ad to 0x95959c, 0x46475a, 0xffd200 to 0x333333, 0xffd200 to 0x333333, skirt = 0x7c7c85,
            props = listOf(p("rock", 240, 4f, 260f, 0.6f, 2f), p("crater", 180, 4f, 220f, 0.5f, 1.6f), p("dome", 40, 15f, 240f, 1f, 1.8f),
                p("antenna", 50, 8f, 200f, 0.8f, 1.4f), p("rover", 36, 4f, 120f), p("rocket", 14, 30f, 220f, 1.2f, 2f), p("beacon", 120, 1.5f, 2f),
                p("satellite", 12, 40f, 260f, 1f, 1.5f, 40f, 110f), p("ufo", 6, 40f, 240f, 1f, 1.3f, 30f, 90f),
                p("crater", 26, 320f, 1100f, 6f, 12f), p("planet", 3, 900f, 1500f, 3f, 4f, 300f, 500f)),
            knock = listOf("tires", "crate"), shape = TrackShape(101, 600f, 0.27f, 8f, 1.25f, 1.0f, 14, 5), gravity = 11f, night = true),
        Theme("Autumn Woods", "🍂", 0x5b8fd6, 0xffdcae, 0xc77d2e, 0xffd9a0, 3.4f, 0.65f, 2.4f, 1.0f, 0xf2d3b0, 100f, 0.0014f,
            0xc77d2e to 0xb06d26, 0x564d4a, 0xd84315 to 0xfff3e0, 0xd84315 to 0xfff3e0, skirt = 0x9a6a30,
            props = listOf(p("autumnTree", 340, 4f, 240f, 0.8f, 1.5f), p("pumpkin", 90, 3f, 100f, 0.7f, 1.6f), p("hayBale", 50, 3f, 120f)),
            knock = listOf("tires", "hay"), shape = TrackShape(113, 520f, 0.3f, 14f, 1.1f, 1.1f, 13, 3), warmth = 0.2f),
        Theme("Rainbow Sky", "🌈", 0x47a7ff, 0xc6e8ff, 0xfde7ff, 0xffffff, 3.4f, 1.0f, 0.5f, 1.3f, 0xd6ecff, 160f, 0.0009f,
            null, 0x7a6a9a, 0xff5ec4 to 0x7ad7ff, 0xff5ec4 to 0x7ad7ff, glowWalls = true, skirt = 0xffffff,
            props = listOf(p("cloudPuff", 300, 8f, 400f, 1f, 3f, -40f, 60f), p("cloudPuff", 120, 2f, 30f, 0.7f, 1.2f, 1f, 5f),
                p("rainbow", 14, 120f, 500f, 1f, 1.6f, -30f, 0f), p("balloon", 70, 20f, 260f, 1f, 1.4f, 0f, 80f),
                p("skyStar", 110, 6f, 220f, 0.8f, 1.6f, 8f, 70f), p("cloudCastle", 6, 220f, 650f, 1f, 1.5f, -20f, 20f)),
            knock = listOf("tires", "cone"), shape = TrackShape(127, 680f, 0.25f, 50f, 1.2f, 1.05f, 15, 6), gravity = 20f, saturation = 1.25f),
        Theme("Frozen Lake", "🧊", 0x4aa3df, 0xd8f3ff, 0xbfe6f7, 0xffffff, 3.2f, 0.6f, 1.6f, 1.2f, 0xdff3ff, 110f, 0.0012f,
            0xbfe6f7 to 0xa9daf0, 0x6a7a8e, 0x00bcd4 to 0xffffff, 0x00bcd4 to 0xffffff, skirt = 0xe6f5ff,
            props = listOf(p("iceberg", 120, 10f, 260f, 0.8f, 2.2f), p("snowPine", 140, 8f, 240f), p("penguin", 60, 3f, 80f), p("igloo", 14, 15f, 160f)),
            knock = listOf("tires", "cone"), shape = TrackShape(131, 520f, 0.22f, 4f, 1.3f, 0.9f, 12, 3), grip = 1.4f),
        Theme("Pirate Cove", "🏴‍☠️", 0x2b5fa8, 0xffc48a, 0xe9cf8f, 0xffcf8a, 3.4f, 0.55f, 2.8f, 1.0f, 0xf3caa0, 130f, 0.0011f,
            0xe9cf8f to 0xdcc07e, 0x5d544b, 0x8d5524 to 0xffd54f, 0x8d5524 to 0xffd54f, skirt = 0xb48a50,
            props = listOf(p("palm", 220, 4f, 210f, 0.8f, 1.4f), p("chest", 60, 2f, 100f), p("barrelProp", 110, 3f, 120f), p("cannon", 50, 3f, 90f),
                p("anchor", 26, 6f, 130f, 0.9f, 1.4f), p("jollyFlag", 60, 3f, 100f), p("lantern", 120, 1.5f, 3f), p("beachHut", 20, 10f, 150f),
                p("crab", 70, 2f, 80f), p("starfish", 100, 2f, 80f), p("rock", 120, 4f, 200f, 0.6f, 2f), p("grandstand", 2, 3f, 4f)),
            knock = listOf("tires", "barrel", "crate"), shape = TrackShape(139, 580f, 0.3f, 9f, 1.35f, 0.95f, 14, 5), water = 0x0f8fb0, warmth = 0.2f),
        Theme("Dino Valley", "🦕", 0x56a8d8, 0xe8f0c0, 0x7fb24a, 0xfff0c0, 3.4f, 0.8f, 1.2f, 1.0f, 0xdbeec0, 100f, 0.0014f,
            0x7fb24a to 0x6fa03e, 0x5e5346, 0x8bc34a to 0xfff59d, 0x8bc34a to 0xfff59d, skirt = 0x6fa03e,
            props = listOf(p("fern", 480, 2f, 160f, 1f, 2f), p("cycad", 200, 5f, 220f, 0.9f, 1.6f), p("palm", 100, 6f, 220f, 1.2f, 2f), p("bush", 200, 3f, 160f, 0.8f, 1.6f),
                p("bigEgg", 60, 3f, 120f, 0.8f, 1.4f), p("dinoBuddy", 36, 6f, 120f, 0.9f, 1.4f), p("longNeck", 16, 30f, 260f, 1f, 1.8f),
                p("rock", 120, 4f, 200f, 0.7f, 2f), p("volcano", 4, 500f, 1000f)),
            knock = listOf("tires", "barrel"), shape = TrackShape(149, 580f, 0.32f, 18f, 1.2f, 1.1f, 14, 5)),
        Theme("Toy Town", "🧸", 0x6aa8ff, 0xe8f0ff, 0xd9b38c, 0xffffff, 3.4f, 0.9f, 0.7f, 1.2f, 0xe9f0ff, 120f, 0.0012f,
            0xd9b38c to 0xcfa880, 0x4d6b9a, 0xff5252 to 0xffeb3b, 0xff5252 to 0xffeb3b, skirt = 0xb08a68,
            props = listOf(p("toyBlocks", 260, 3f, 200f, 0.8f, 1.7f), p("toyBall", 130, 3f, 160f, 0.8f, 2f), p("pencil", 70, 6f, 160f, 0.9f, 1.4f),
                p("crayon", 160, 3f, 120f, 0.8f, 1.5f), p("toyHouse", 50, 12f, 240f, 0.9f, 1.4f), p("teddy", 50, 3f, 120f, 0.9f, 1.6f),
                p("roundTree", 60, 8f, 220f, 0.7f, 1.1f), p("balloon", 20, 40f, 240f, 1f, 1.4f, 20f, 60f)),
            knock = listOf("tires", "crate"), shape = TrackShape(151, 560f, 0.32f, 12f, 1.3f, 0.95f, 15, 5), saturation = 1.25f),
        Theme("Sunset Highway", "🌅", 0x3b1d6e, 0xff7e5f, 0xd9875a, 0xffb070, 3.6f, 0.25f, 0.0f, 0.9f, 0xffa585, 110f, 0.0013f,
            0xd9875a to 0xc97a50, 0x3d3a48, 0xff6f00 to 0xfff176, 0xff6f00 to 0xfff176, skirt = 0x8a4a3a,
            props = listOf(p("cactus", 180, 6f, 220f, 0.8f, 1.5f), p("barrelCactus", 160, 3f, 120f), p("lamp", 140, 1.5f, 2f), p("billboard", 44, 8f, 70f, 0.9f, 1.2f),
                p("roadSign", 50, 1f, 2f), p("diner", 12, 14f, 90f), p("tumbleweed", 60, 3f, 140f), p("waterTower", 8, 30f, 220f),
                p("rock", 120, 4f, 220f, 0.6f, 2f), p("mesa", 34, 260f, 900f, 0.8f, 1.8f)),
            knock = listOf("cone", "tires"), shape = TrackShape(163, 680f, 0.18f, 8f, 1.6f, 0.8f, 12, 4), warmth = 0.35f),
        Theme("Crystal Canyon", "💎", 0x1a0838, 0x6b2fa0, 0x4b3568, 0xd8b8ff, 2.0f, 0.6f, 1.0f, 0.9f, 0x4a2470, 90f, 0.0016f,
            0x4b3568 to 0x3e2c58, 0x3a2f4a, 0xb388ff to 0x18ffff, 0xb388ff to 0x18ffff, glowWalls = true, skirt = 0x3a2a50,
            props = listOf(p("crystal", 380, 3f, 240f, 0.7f, 2.4f), p("glowMushroom", 260, 3f, 120f, 0.8f, 1.6f), p("rock", 160, 4f, 220f, 0.6f, 2f),
                p("spire", 70, 25f, 300f, 0.8f, 1.6f), p("crystal", 40, 60f, 400f, 4f, 8f), p("starBit", 160, 6f, 160f, 0.5f, 1f, 6f, 40f),
                p("beacon", 90, 1.5f, 2f), p("spire", 30, 320f, 800f, 2.5f, 4f)),
            knock = listOf("tires", "cone"), shape = TrackShape(173, 580f, 0.3f, 18f, 1.15f, 1.1f, 15, 5), night = true, saturation = 1.25f),
        Theme("Bubble Reef", "🐠", 0x013a5a, 0x0e8fae, 0xe8d6a0, 0xbff6ff, 2.8f, 0.95f, 0.5f, 1.2f, 0x1a8aa0, 40f, 0.004f,
            0xe8d6a0 to 0xdcc890, 0x47607a, 0xff7043 to 0xfff59d, 0xff7043 to 0xfff59d, skirt = 0xc9b47c,
            props = listOf(p("coral", 320, 2f, 200f, 0.8f, 2f), p("kelp", 300, 3f, 200f, 0.8f, 1.8f), p("anemone", 200, 2f, 110f, 0.8f, 1.5f),
                p("fish", 170, 6f, 160f, 0.8f, 1.5f, 5f, 26f), p("jellyfish", 60, 8f, 140f, 0.9f, 1.6f, 6f, 30f), p("clam", 70, 2f, 90f, 0.8f, 1.4f),
                p("starfish", 140, 2f, 80f, 0.9f, 1.6f), p("chest", 25, 3f, 90f), p("sunkenShip", 8, 20f, 160f, 1f, 1.5f), p("rock", 100, 4f, 200f)),
            knock = listOf("tires", "barrel"), shape = TrackShape(181, 540f, 0.28f, 12f, 1.2f, 1.05f, 14, 5), gravity = 16f, saturation = 1.2f),
        Theme("Galaxy Grand Prix", "🪐", 0x0a0218, 0x3a0c5a, 0x0a0218, 0xffe0ff, 3.0f, 0.8f, 1.2f, 0.7f, 0x1a0830, 300f, 0.0005f,
            null, 0x252040, 0xffea00 to 0xff2bd6, 0xffea00 to 0xff2bd6, glowWalls = true, skirt = 0x2d2850,
            props = listOf(p("asteroid", 340, 10f, 320f, 0.6f, 3.5f, -80f, 80f), p("ring", 26, 20f, 200f, 1f, 1.6f, -10f, 40f), p("ufo", 24, 30f, 260f, 1f, 1.5f, 10f, 80f),
                p("rocket", 24, 20f, 260f, 1f, 1.5f, 10f, 90f), p("satellite", 30, 30f, 260f, 1f, 1.6f, 10f, 80f), p("beacon", 160, 1.5f, 2f, 1f, 1f, 5.6f, 5.6f),
                p("starBit", 600, 6f, 260f, 0.7f, 1.8f, -60f, 90f), p("station", 4, 300f, 700f, 1.2f, 1.8f, 40f, 200f), p("planet", 14, 450f, 1400f, 1f, 2.6f, -200f, 400f)),
            knock = listOf("tires", "crate"), shape = TrackShape(191, 700f, 0.3f, 60f, 1.2f, 1.1f, 16, 6), gravity = 14f, night = true, saturation = 1.3f),
    )
}
