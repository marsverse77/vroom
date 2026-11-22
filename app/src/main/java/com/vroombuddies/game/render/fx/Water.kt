package com.vroombuddies.game.render.fx

import com.google.android.filament.*
import com.vroombuddies.game.content.Theme
import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.TrackPath
import kotlin.math.*

/**
 * A sea around the island for themes with [Theme.water]: a big ring of water starting a safe distance past the
 * scenery, with rolling foam at the shore. It's glossy, so it reflects the sky light, and SSR adds the scene when on.
 * Batches can tune the look per theme with [deep]/[shallow]/[wave].
 */
class Water(private val engine: Engine, private val scene: Scene, mats: MaterialLib, track: TrackPath, theme: Theme) {
    private var entity = 0
    private var mesh: GpuMesh? = null
    private var mi: MaterialInstance? = null

    init {
        val mat = mats.water
        val wc = theme.water
        if (mat != null && wc != null) {
            // inner edge: past the furthest track point plus the scenery band
            var maxR = 0f
            val f = com.vroombuddies.game.sim.Frame()
            var s = 0f
            while (s < track.length) { track.frame(s, f); maxR = max(maxR, sqrt(f.x * f.x + f.z * f.z)); s += 20f }
            val inner = maxR + track.halfWidth + 280f
            val outer = 4200f
            val m = MeshData()
            val seg = 96
            // radii: a soft foam band at the shore, then rings growing outward (keeps far triangles reasonable)
            val radii = floatArrayOf(inner - 12f, inner, inner + 25f, inner + 70f, inner + 160f, inner + 360f, inner + 800f, inner + 1600f, outer)
            val foam = floatArrayOf(1f, 0.8f, 0.3f, 0f, 0f, 0f, 0f, 0f, 0f)
            val y = 0.25f
            for ((ri, r) in radii.withIndex()) for (i in 0..seg) {
                val a = i.toFloat() / seg * PI.toFloat() * 2
                val x = cos(a) * r; val z = sin(a) * r
                m.vertex(x, y, z, 0f, 1f, 0f, Rgba(1f, 1f, 1f, foam[ri]), x, z)
            }
            val row = seg + 1
            for (j in 0 until radii.size - 1) for (i in 0 until seg) {
                val a = j * row + i
                // CCW seen from above
                m.quad(a, a + 1, a + row + 1, a + row)
            }
            val gm = Gpu.upload(engine, m)
            val inst = mat.createInstance()
            val c = Rgba.hex(wc)
            inst.setParameter("deep", c.r * 0.35f, c.g * 0.45f, c.b * 0.6f, 1f)
            inst.setParameter("shallow", min(1f, c.r * 1.2f + 0.05f), min(1f, c.g * 1.2f + 0.08f), min(1f, c.b * 1.1f + 0.05f), 1f)
            inst.setParameter("wave", 1f)
            entity = Gpu.renderable(engine, gm, inst, castShadows = false, receiveShadows = true, priority = 3, culling = true)
            scene.addEntity(entity)
            mesh = gm; mi = inst
        }
    }

    fun setLook(deep: Int, shallow: Int, wave: Float) {
        val m = mi ?: return
        val d = Rgba.hex(deep); val s = Rgba.hex(shallow)
        m.setParameter("deep", d.r, d.g, d.b, 1f); m.setParameter("shallow", s.r, s.g, s.b, 1f); m.setParameter("wave", wave)
    }

    fun destroy() {
        if (entity != 0) { scene.remove(entity); engine.destroyEntity(entity) }
        mesh?.destroy(engine); mi?.let { engine.destroyMaterialInstance(it) }
    }
}
