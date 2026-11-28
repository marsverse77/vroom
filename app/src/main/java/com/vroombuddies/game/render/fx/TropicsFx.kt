package com.vroombuddies.game.render

import com.google.android.filament.*
import com.vroombuddies.game.content.Theme
import com.vroombuddies.game.sim.Frame
import com.vroombuddies.game.sim.Rng
import com.vroombuddies.game.sim.TrackPath
import kotlin.math.*

/**
 * Batch C special effects for the water and tropics tracks:
 *  - Sunny Beach / Pirate Cove: a sea around the island (shoreline follows the track, with a foam edge),
 *    bobbing boats and pirate ships, sandy islets, and seagulls circling overhead.
 *  - Jungle Jam: drifting leaves and sunny pollen motes.
 *  - Bubble Reef: rising bubbles and soft light shafts.
 * Hooked into GameScene with one line each for create, update and destroy.
 */
class TropicsFx private constructor(
    private val engine: Engine, private val scene: Scene, private val mats: MaterialLib,
    private val track: TrackPath, private val theme: Theme, private val particleScale: Float,
) {
    companion object {
        /** Returns the effect for this theme, or null when it isn't one of batch C's tracks. */
        fun create(engine: Engine, scene: Scene, mats: MaterialLib, track: TrackPath, theme: Theme, particleScale: Float): TropicsFx? =
            if (theme.name in setOf("Sunny Beach", "Jungle Jam", "Pirate Cove", "Bubble Reef")) TropicsFx(engine, scene, mats, track, theme, particleScale) else null
    }

    private val meshes = ArrayList<GpuMesh>()
    private val ents = ArrayList<Int>()
    private val mis = ArrayList<MaterialInstance>()
    private val tm = engine.transformManager
    private var time = 0f
    private val rnd = Rng(theme.shape.seed + 99)

    private class Floater(val e: Int, val x: Float, val z: Float, val yaw: Float, val phase: Float, val y: Float = 0f)
    private class Gull(val e: Int, val cx: Float, val cz: Float, val r: Float, val h: Float, val speed: Float, val phase: Float)
    private val floaters = ArrayList<Floater>()
    private val gulls = ArrayList<Gull>()
    private var waterEnt = 0
    private val waterY = 0.35f

    private val add: Particles? = if (theme.name == "Bubble Reef" || theme.name == "Jungle Jam") Particles(engine, scene, mats.particleAdd, 160) else null
    private val alpha: Particles? = if (theme.name == "Bubble Reef" || theme.name == "Jungle Jam") Particles(engine, scene, mats.particleAlpha, 320) else null
    private val mtx = FloatArray(16)


    private fun litMi(rough: Float): MaterialInstance = mats.lit.createInstance().also {
        it.setParameter("baseColor", 1f, 1f, 1f, 1f); it.setParameter("roughness", rough); it.setParameter("metallic", 0f)
        it.setParameter("emissive", 0f, 0f, 0f, 0f); it.setParameter("wind", 0f); mis.add(it)
    }

    private fun entity(m: MeshData, mi: MaterialInstance, shadows: Boolean): Int {
        val g = Gpu.upload(engine, m); meshes.add(g)
        return entity(g, mi, shadows)
    }
    private fun entity(g: GpuMesh, mi: MaterialInstance, shadows: Boolean): Int {
        val e = Gpu.renderable(engine, g, mi, castShadows = shadows)
        if (!tm.hasComponent(e)) tm.create(e)
        scene.addEntity(e); ents.add(e)
        return e
    }

    // ------------------------------------------------------------------ sea
    private val shoreBins = 96
    private val shore = FloatArray(shoreBins)
    private var cx = 0f; private var cz = 0f

    init {
        add?.scale = particleScale; alpha?.scale = particleScale
        if (theme.water != null) buildSea(theme.water)
    }

    private fun shoreAt(a: Float): Float {
        val u = ((a / (2 * PI.toFloat())) % 1f + 1f) % 1f * shoreBins
        val i = u.toInt() % shoreBins; val f = u - u.toInt()
        return shore[i] + (shore[(i + 1) % shoreBins] - shore[i]) * f
    }

    private fun buildSea(waterHex: Int) {
        // centre of the island and how far the road reaches in each direction
        val f = Frame()
        val pts = ArrayList<FloatArray>()
        var s = 0f
        while (s < track.length) { track.frame(s, f); pts.add(floatArrayOf(f.x, f.z)); s += 8f }
        cx = pts.sumOf { it[0].toDouble() }.toFloat() / pts.size; cz = pts.sumOf { it[1].toDouble() }.toFloat() / pts.size
        val reach = FloatArray(shoreBins)
        for (p in pts) {
            val dx = p[0] - cx; val dz = p[1] - cz
            val a = ((atan2(dz, dx) / (2 * PI.toFloat())) % 1f + 1f) % 1f
            val b = (a * shoreBins).toInt() % shoreBins
            reach[b] = max(reach[b], hypot(dx, dz))
        }
        // widen (max over neighbours), smooth, then add a beach wide enough for all the scenery plus a wiggle
        val wide = FloatArray(shoreBins) { i -> (-4..4).maxOf { reach[(i + it + shoreBins) % shoreBins] } }
        for (i in 0 until shoreBins) {
            val avg = (-2..2).sumOf { wide[(i + it + shoreBins) % shoreBins].toDouble() }.toFloat() / 5f
            shore[i] = avg + track.halfWidth + 245f + sin(i * 0.7f) * 18f + sin(i * 0.23f + 1f) * 25f
        }

        val wc = Rgba.hex(waterHex)
        val shallow = Rgba(min(1f, wc.r * 1.6f + 0.1f), min(1f, wc.g * 1.4f + 0.15f), min(1f, wc.b * 1.2f + 0.1f))
        val deep = Rgba(wc.r * 0.55f, wc.g * 0.6f, wc.b * 0.8f)
        val foam = Rgba(1f, 1f, 1f)
        // rings: (offset from shore, height, colour)
        val rings = listOf(Triple(-3f, 0.42f, foam), Triple(2.5f, 0.42f, foam), Triple(3.5f, waterY, shallow), Triple(60f, waterY, wc), Triple(400f, waterY, wc), Triple(4000f, waterY, deep))
        val m = MeshData()
        for (i in 0..shoreBins) {
            val a = i.toFloat() / shoreBins * 2 * PI.toFloat()
            val sr = shore[i % shoreBins]
            for ((k, r) in rings.withIndex()) {
                val rad = if (k == rings.size - 1) max(sr + 600f, r.first) else sr + r.first
                m.vertex(cx + cos(a) * rad, r.second, cz + sin(a) * rad, 0f, 1f, 0f, r.third)
            }
        }
        val n = rings.size
        for (i in 0 until shoreBins) for (k in 0 until n - 1) {
            val a = i * n + k; val b = (i + 1) * n + k
            m.quadFacing(a, b, b + 1, a + 1)
        }
        waterEnt = entity(m, litMi(0.1f), false)

        // islets out at sea, boats and ships bobbing between them
        val lib = SceneryLibrary.builders
        val props = MeshData()
        val pirate = theme.name == "Pirate Cove"
        lib["islet"]?.let { b ->
            val isl = b(1).lit
            repeat(7) {
                val a = rnd.f() * 6.283f; val d = shoreAt(a) + 180f + rnd.f() * 600f
                val sc = 0.8f + rnd.f() * 1.4f
                props.append(isl, Mat.trs(cx + cos(a) * d, waterY - 0.3f, cz + sin(a) * d, 0f, rnd.f() * 6.283f, 0f, sc, sc, sc))
            }
        }
        if (props.vCount > 0) entity(props, litMi(0.85f), true)
        val shipLod0 = lib[if (pirate) "pirateShip" else "sailboat"]?.invoke(0)
        if (shipLod0 != null) {
            val g = Gpu.upload(engine, shipLod0.lit); meshes.add(g)
            val mi = litMi(0.7f)
            repeat(if (pirate) 5 else 8) {
                val a = rnd.f() * 6.283f; val d = shoreAt(a) + 40f + rnd.f() * 220f
                val e = entity(g, mi, true)
                floaters.add(Floater(e, cx + cos(a) * d, cz + sin(a) * d, rnd.f() * 6.283f, rnd.f() * 6.283f, if (pirate) -0.6f else -0.2f))
            }
        }
        // seagulls circling above the beach
        val gm = MeshData()
        Prims.sphere(gm, 0.35f, Rgba.WHITE, Mat.trs(0f, 0f, 0f, sz = 2f), 6, 4)
        Prims.sphere(gm, 0.22f, Rgba.WHITE, Mat.trs(0f, 0.15f, 0.6f), 6, 4)
        Prims.cone(gm, 0.08f, 0.25f, Rgba.hex(0xffb300), Mat.trs(0f, 0.12f, 0.9f, rx = PI.toFloat() / 2), 4)
        for (s in intArrayOf(-1, 1)) {
            Prims.box(gm, 1.2f, 0.05f, 0.5f, Rgba.WHITE, Mat.trs(s * 0.6f, 0.15f, 0f, rz = s * 0.35f))
            Prims.box(gm, 1.1f, 0.05f, 0.4f, Rgba.hex(0x9e9e9e), Mat.trs(s * 1.6f, 0.55f, -0.05f, rz = -s * 0.3f))
        }
        val gg = Gpu.upload(engine, gm); meshes.add(gg)
        val gmi = litMi(0.6f)
        repeat(10) {
            track.frame(rnd.f() * track.length, f)
            gulls.add(Gull(entity(gg, gmi, false), f.x + (rnd.f() - 0.5f) * 60f, f.z + (rnd.f() - 0.5f) * 60f,
                14f + rnd.f() * 30f, f.y + 18f + rnd.f() * 20f, (0.25f + rnd.f() * 0.2f) * (if (rnd.f() < 0.5f) -1f else 1f), rnd.f() * 6.283f))
        }
    }

    // ------------------------------------------------------------------ per frame
    fun update(dt: Float, ex: Float, ey: Float, ez: Float, rX: Float, rY: Float, rZ: Float, uX: Float, uY: Float, uZ: Float) {
        time += dt
        if (waterEnt != 0) {
            tm.setTransform(tm.getInstance(waterEnt), Mat.trs(0f, waterY - 0.35f + sin(time * 0.8f) * 0.12f, 0f))
            for (b in floaters) {
                val t = time * 1.1f + b.phase
                Mat.trs(b.x, waterY + b.y + sin(t) * 0.35f, b.z, sin(t * 0.8f) * 0.05f, b.yaw, sin(t * 0.9f + 1f) * 0.08f).copyInto(mtx)
                tm.setTransform(tm.getInstance(b.e), mtx)
            }
            for (g in gulls) {
                val a = g.phase + time * g.speed
                val dir = if (g.speed > 0) 1f else -1f
                // face along the circle and bank into the turn
                val yaw = atan2(-sin(a) * dir, cos(a) * dir)
                Mat.trs(g.cx + cos(a) * g.r, g.h + sin(time * 0.7f + g.phase) * 1.5f, g.cz + sin(a) * g.r, 0f, yaw, dir * 0.35f).copyInto(mtx)
                tm.setTransform(tm.getInstance(g.e), mtx)
            }
        }
        when (theme.name) {
            "Bubble Reef" -> reefFx(dt, ex, ey, ez)
            "Jungle Jam" -> jungleFx(dt, ex, ey, ez)
        }
        add?.update(dt, ex, ey, ez, rX, rY, rZ, uX, uY, uZ)
        alpha?.update(dt, ex, ey, ez, rX, rY, rZ, uX, uY, uZ)
    }

    private fun r(k: Float) = (rnd.f() - 0.5f) * 2f * k

    private var shaftTimer = 0f
    private fun reefFx(dt: Float, ex: Float, ey: Float, ez: Float) {
        val a = alpha ?: return; val g = add ?: return
        // bubbles wobbling up all around
        var n = dt * 40f
        while (n > 0f) {
            if (rnd.f() < n) a.spawn(ex + r(35f), ey - 4f + r(3f), ez + r(35f), r(0.4f), 2.5f + rnd.f() * 2f, r(0.4f),
                5f, 0.25f + rnd.f() * 0.25f, 0.5f, 0.85f, 0.97f, 1f, 0.55f)
            n -= 1f
        }
        // soft beams of sunlight from the surface
        shaftTimer -= dt
        if (shaftTimer <= 0f) {
            shaftTimer = 0.35f
            g.spawn(ex + r(45f), ey + 14f, ez + r(45f), 0f, -0.4f, 0f, 6f, 3.5f, 4.5f, 0.55f, 0.9f, 1f, 0.07f, 0f, 0f, 70f)
        }
    }

    private fun jungleFx(dt: Float, ex: Float, ey: Float, ez: Float) {
        val a = alpha ?: return; val g = add ?: return
        if (rnd.f() < dt * 10f) {
            val y = rnd.f() < 0.5f
            a.spawn(ex + r(30f), ey + 8f + rnd.f() * 8f, ez + r(30f), r(1.2f), -1.1f, r(1.2f), 7f, 0.35f, 0.3f,
                if (y) 0.9f else 0.25f, if (y) 0.75f else 0.6f, 0.1f, 0.95f)
        }
        if (rnd.f() < dt * 14f)
            g.spawn(ex + r(25f), ey + r(4f) + 1f, ez + r(25f), r(0.3f), 0.15f, r(0.3f), 4f, 0.15f, 0.2f, 1.6f, 1.4f, 0.5f, 0.6f)
    }

    fun destroy() {
        for (e in ents) { scene.remove(e); engine.destroyEntity(e) }
        meshes.forEach { it.destroy(engine) }
        mis.forEach { engine.destroyMaterialInstance(it) }
        add?.destroy(scene); alpha?.destroy(scene)
    }
}
