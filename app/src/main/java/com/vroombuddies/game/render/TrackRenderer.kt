package com.vroombuddies.game.render

import com.google.android.filament.*
import com.vroombuddies.game.content.Theme
import com.vroombuddies.game.sim.TrackPath
import kotlin.math.*

/** Uploads [TrackGeometry] to Filament with the right material for each layer. */
class TrackRenderer(private val engine: Engine, private val scene: Scene, private val mats: MaterialLib, private val track: TrackPath, private val theme: Theme) {
    private val entities = ArrayList<Int>()
    private val meshes = ArrayList<GpuMesh>()
    private val textures = ArrayList<Texture>()
    private val instances = ArrayList<MaterialInstance>()
    private val geometry = TrackGeometry(track, theme)
    val wallHeight get() = geometry.wallHeight

    private fun add(m: MeshData, mi: MaterialInstance, shadows: Boolean = true, receive: Boolean = true) {
        if (m.vCount == 0) return
        val g = Gpu.upload(engine, m); meshes.add(g)
        val e = Gpu.renderable(engine, g, mi, castShadows = shadows, receiveShadows = receive)
        entities.add(e); scene.addEntity(e)
    }

    private fun texturedMi(tex: Texture, uScale: Float, vScale: Float, rough: Float, glow: Float = 0f, tint: Rgba = Rgba.WHITE): MaterialInstance {
        textures.add(tex)
        return mats.textured.createInstance().also {
            it.setParameter("albedo", tex, Textures.sampler())
            it.setParameter("uvScale", uScale, vScale)
            it.setParameter("tint", tint.r, tint.g, tint.b, 1f)
            it.setParameter("roughness", rough)
            it.setParameter("glow", glow)
            instances.add(it)
        }
    }

    fun build() {
        val roadMi = texturedMi(Textures.road(engine, theme.road, theme.kerbs.first, theme.kerbs.second), 1f, 1f, 0.75f)
        val wallMi = texturedMi(Textures.wall(engine, theme.walls.first, theme.walls.second), 1f, 1f, 0.5f, if (theme.glowWalls) 1.6f else 0f)
        val g = theme.ground ?: (theme.skirt to theme.skirt)
        val skirtMi = texturedMi(Textures.ground(engine, g.first, g.second, 5), 1f, 1f, 0.95f, tint = Rgba.hex(theme.skirt).let { Rgba(1f, 1f, 1f) })
        val rampMi = texturedMi(Textures.ramp(engine), 1f, 1f, 0.45f, 0.15f)

        val padTex = Textures.chevrons(engine); textures.add(padTex)
        val padMi = mats.glowTex.createInstance().also {
            it.setParameter("tex", padTex, Textures.sampler())
            it.setParameter("color", 0.4f, 2.2f, 4.0f, 1f)
            it.setParameter("scroll", 1.6f)
            instances.add(it)
        }
        val startMi = texturedMi(Textures.checker(engine), 1f, 1f, 0.6f)
        val archMi = mats.lit.createInstance().also { it.setParameter("baseColor", 1f, 1f, 1f, 1f); it.setParameter("roughness", 0.35f); it.setParameter("metallic", 0f); it.setParameter("emissive", 0f, 0f, 0f, 0f); it.setParameter("wind", 0f); instances.add(it) }
        val archGlowMi = mats.glow.createInstance().also { it.setParameter("color", 4f, 4f, 4f, 1f); instances.add(it) }
        val groundMi = theme.ground?.let { texturedMi(Textures.ground(engine, it.first, it.second, theme.shape.seed), 1f, 1f, 0.95f) }
        geometry.build { m, layer ->
            when (layer) {
                TrackLayer.ROAD -> add(m, roadMi, shadows = false)
                TrackLayer.WALL -> add(m, wallMi)
                TrackLayer.SKIRT -> add(m, skirtMi, shadows = false)
                TrackLayer.RAMP -> add(m, rampMi)
                TrackLayer.PADS -> add(m, padMi, shadows = false, receive = false)
                TrackLayer.START_LINE -> add(m, startMi, shadows = false)
                TrackLayer.ARCH -> add(m, archMi)
                TrackLayer.ARCH_GLOW -> add(m, archGlowMi, shadows = false)
                TrackLayer.GROUND -> groundMi?.let { add(m, it, shadows = false) }
            }
        }
    }

    fun includeInProfile(visible: Boolean) {
        for (e in entities) if (visible) scene.addEntity(e) else scene.remove(e)
    }

    fun destroy() {
        for (e in entities) { scene.remove(e); engine.destroyEntity(e) }
        meshes.forEach { it.destroy(engine) }
        instances.forEach { engine.destroyMaterialInstance(it) }
        textures.forEach { engine.destroyTexture(it) }
    }
}
