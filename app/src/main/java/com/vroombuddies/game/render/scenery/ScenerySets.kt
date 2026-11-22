package com.vroombuddies.game.render

/**
 * Scenery packs, one file per theme group (for example render/scenery/DesertScenery.kt).
 * Each pack is a map of id -> builder(lod), where lod 0 = near/detailed and lod 1 = far/simple,
 * using the ids that theme lists in content/Themes.kt. Add your pack to this list.
 */
typealias SceneryPack = Map<String, (Int) -> SceneryModel>

object ScenerySets {
    val packs: List<SceneryPack> = listOf(
        // DesertScenery.pack,
        FunTownScenery.pack,
    )
}
