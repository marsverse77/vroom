package com.vroombuddies.game.render

import com.google.android.filament.*
import com.vroombuddies.game.sim.PropWorld
import kotlin.math.PI

/** Draws the knock-over props (tyre stacks, cones, hay...). Each body is its own entity, moved every frame while awake. */
class KnockRenderer(private val engine: Engine, private val scene: Scene, mats: MaterialLib, private val world: PropWorld) {
    private val meshes = HashMap<String, List<GpuMesh>>()
    private val ents = IntArray(world.bodies.size)
    private val mi = mats.lit.createInstance().also {
        it.setParameter("baseColor", 1f, 1f, 1f, 1f); it.setParameter("roughness", 0.6f); it.setParameter("metallic", 0f)
        it.setParameter("emissive", 0f, 0f, 0f, 0f); it.setParameter("wind", 0f)
    }
    private val m = FloatArray(16)

    companion object {
        private fun c(h: Int) = Rgba.hex(h)
        /** Mesh variants per knock kind; add new kinds here (snowballs, gumballs, space crates...). */
        fun build(id: String): List<MeshData> = when (id) {
            "tires" -> listOf(0xffffff, 0xff3b3b, 0xffd740, 0x40c4ff).map { band ->
                MeshData().also { m ->
                    Prims.torus(m, 0.72f, 0.36f, c(0x22232a), Mat.trs(rx = (PI / 2).toFloat()), 8, 18)
                    Prims.torus(m, 0.72f, 0.12f, c(band), Mat.trs(0f, 0.3f, 0f, rx = (PI / 2).toFloat(), sz = 0.6f), 5, 18)
                }
            }
            "cone" -> listOf(MeshData().also { m ->
                Prims.cone(m, 0.55f, 1.5f, c(0xff6d00), Mat.trs(0f, 0.05f, 0f), 12)
                Prims.cylinder(m, 0.38f, 0.42f, 0.22f, c(0xffffff), Mat.trs(0f, -0.05f, 0f), 12, false)
                Prims.box(m, 1.3f, 0.12f, 1.3f, c(0xff6d00), Mat.trs(0f, -0.74f, 0f))
            })
            "hay" -> listOf(MeshData().also { m ->
                Prims.roundBox(m, 2.2f, 1.2f, 1.3f, 0.15f, c(0xe8c25a))
                Prims.box(m, 0.12f, 1.24f, 1.34f, c(0xa1773f), Mat.trs(0.5f, 0f, 0f)); Prims.box(m, 0.12f, 1.24f, 1.34f, c(0xa1773f), Mat.trs(-0.5f, 0f, 0f))
            })
            "barrel" -> listOf(0xd84315, 0x1e88e5, 0xfbc02d).map { col ->
                MeshData().also { m ->
                    Prims.cylinder(m, 0.72f, 0.72f, 1.6f, c(col), null, 14)
                    Prims.torus(m, 0.74f, 0.06f, c(0x333333), Mat.trs(0f, 0.5f, 0f, rx = (PI / 2).toFloat()), 4, 16)
                    Prims.torus(m, 0.74f, 0.06f, c(0x333333), Mat.trs(0f, -0.5f, 0f, rx = (PI / 2).toFloat()), 4, 16)
                }
            }
            "crate" -> listOf(MeshData().also { m ->
                Prims.box(m, 1.6f, 1.6f, 1.6f, c(0xc68642))
                for (y in floatArrayOf(-0.6f, 0.6f)) Prims.box(m, 1.66f, 0.22f, 1.66f, c(0x8d5524), Mat.trs(0f, y, 0f))
            })
            else -> listOf(MeshData().also { Prims.box(it, 1.4f, 1.4f, 1.4f, c(0xff00ff)) })
        }
    }

    init {
        val tm = engine.transformManager
        world.bodies.forEachIndexed { i, b ->
            val list = meshes.getOrPut(b.kind.id) { build(b.kind.id).map { Gpu.upload(engine, it) } }
            val g = list[b.tint % list.size]
            val e = Gpu.renderable(engine, g, mi)
            tm.create(e)
            ents[i] = e; scene.addEntity(e)
            place(i)
        }
    }

    private fun place(i: Int) {
        val b = world.bodies[i]
        Mat.fromQuat(m, b.qx, b.qy, b.qz, b.qw, b.x, if (b.gone) -1e4f else b.y, b.z)
        engine.transformManager.setTransform(engine.transformManager.getInstance(ents[i]), m)
    }

    fun update() { world.bodies.forEachIndexed { i, b -> if (!b.asleep || b.gone) place(i) } }

    fun resetAll() { world.resetAll(); for (i in ents.indices) place(i) }

    fun destroy() {
        for (e in ents) { scene.remove(e); engine.destroyEntity(e) }
        meshes.values.flatten().forEach { it.destroy(engine) }
        engine.destroyMaterialInstance(mi)
    }
}
