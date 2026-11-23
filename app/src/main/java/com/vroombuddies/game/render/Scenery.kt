package com.vroombuddies.game.render

import com.google.android.filament.*
import com.vroombuddies.game.content.PropRule
import com.vroombuddies.game.content.Theme
import com.vroombuddies.game.sim.Frame
import com.vroombuddies.game.sim.Rng
import com.vroombuddies.game.sim.TrackPath
import kotlin.math.*

/** One scenery model at a given level of detail. */
class SceneryModel(val lit: MeshData, val glow: MeshData? = null, val radius: Float, val small: Boolean = false, val trackside: Boolean = false, val tints: List<Int> = emptyList())

/**
 * Scenery builders by id. lod = 0 (near, detailed) or 1 (far, simple).
 * To add scenery for a theme, add a builder here with the id used in [com.vroombuddies.game.content.Themes].
 */
object SceneryLibrary {
    private fun c(h: Int) = Rgba.hex(h)
    private val P2 = (PI / 2).toFloat()

    /** Every scenery id: the shared core set below plus each theme pack in [ScenerySets.packs]. */
    val builders: Map<String, (Int) -> SceneryModel> by lazy { ScenerySets.packs.fold(core) { acc, pack -> acc + pack } }

    /** Shared basics any theme can use. Theme-specific models go in their own file under render/scenery/. */
    private val core: Map<String, (Int) -> SceneryModel> = mapOf(
        "roundTree" to { lod ->
            val m = MeshData(); val s = if (lod == 0) 10 else 5
            Prims.cylinder(m, 0.45f, 0.7f, 5f, c(0x7a4a2a), Mat.trs(0f, 2.5f, 0f), s)
            if (lod == 0) {
                Prims.sphere(m, 3.4f, Rgba.WHITE, Mat.trs(0f, 6.8f, 0f), 12, 8)
                Prims.sphere(m, 2.5f, Rgba(0.9f, 1.05f, 0.9f), Mat.trs(1.6f, 8.2f, 0.6f), 10, 7)
                Prims.sphere(m, 2.2f, Rgba(0.8f, 0.9f, 0.8f), Mat.trs(-1.4f, 6.3f, -1f), 10, 7)
            } else Prims.rock(m, 3.6f, Rgba.WHITE, Mat.trs(0f, 7f, 0f))
            m.windByHeight(0.35f)
            SceneryModel(m, radius = 4f, tints = listOf(0x4caf50, 0x5cc160, 0x3e9a44, 0x7bc95a))
        },
        "pine" to { lod ->
            val m = MeshData(); val s = if (lod == 0) 9 else 5
            Prims.cylinder(m, 0.45f, 0.65f, 3f, c(0x6d4527), Mat.trs(0f, 1.5f, 0f), s)
            Prims.cone(m, 4.2f, 6f, Rgba(0.85f, 1f, 0.9f), Mat.trs(0f, 5f, 0f), s)
            Prims.cone(m, 3.3f, 5f, Rgba(0.95f, 1.05f, 0.95f), Mat.trs(0f, 7.8f, 0f), s)
            Prims.cone(m, 2.3f, 4f, Rgba.WHITE, Mat.trs(0f, 10.4f, 0f), s)
            m.windByHeight(0.25f)
            SceneryModel(m, radius = 4f, tints = listOf(0x2e8b57, 0x37a564, 0x2a7a4c))
        },
        "flower" to { lod ->
            val m = MeshData()
            Prims.cylinder(m, 0.07f, 0.07f, 1.3f, c(0x3c9a3c), Mat.trs(0f, 0.65f, 0f), 4, false)
            Prims.sphere(m, 0.24f, c(0xffe14d), Mat.trs(0f, 1.4f, 0f), 6, 4)
            if (lod == 0) for (i in 0 until 5) { val a = i * 1.256f; Prims.sphere(m, 0.27f, Rgba.WHITE, Mat.trs(cos(a) * 0.4f, 1.4f, sin(a) * 0.4f, sy = 0.5f), 6, 4) }
            m.windByHeight(0.25f)
            SceneryModel(m, radius = 0.6f, small = true, tints = listOf(0xff5fa2, 0xffffff, 0x8a7dff, 0xff7043, 0x4fc3f7))
        },
        "bush" to { lod ->
            val m = MeshData()
            if (lod == 0) {
                Prims.sphere(m, 1.4f, Rgba.WHITE, Mat.trs(0f, 0.9f, 0f), 10, 7)
                Prims.sphere(m, 1.1f, Rgba(1.1f, 1.1f, 1.1f), Mat.trs(1f, 0.9f, 0.4f), 8, 6)
                Prims.sphere(m, 1f, Rgba(0.85f, 0.85f, 0.85f), Mat.trs(-0.9f, 0.8f, -0.3f), 8, 6)
            } else Prims.rock(m, 1.5f, Rgba.WHITE, Mat.trs(0f, 0.9f, 0f))
            m.windByHeight(0.12f)
            SceneryModel(m, radius = 2f, small = true, tints = listOf(0x4caf50, 0x43a047, 0x66bb6a))
        },
        "rock" to { _ ->
            val m = MeshData()
            Prims.rock(m, 2.2f, c(0x9e9e9e), Mat.trs(0f, 1f, 0f, 0.3f, 0.5f, 0f, 1.2f, 0.75f, 1f), 3)
            Prims.rock(m, 1.2f, c(0xaaaaaa), Mat.trs(1.8f, 0.5f, 0.6f), 9)
            SceneryModel(m, radius = 3f, tints = listOf(0xffffff, 0xd8c8b0, 0xc2b8a8))
        },
        "windmill" to { lod ->
            val m = MeshData(); val s = if (lod == 0) 10 else 6
            Prims.cylinder(m, 2f, 3.4f, 18f, c(0xf5f0e6), Mat.trs(0f, 9f, 0f), s)
            Prims.cone(m, 3f, 4f, c(0xd84315), Mat.trs(0f, 20f, 0f), s)
            for (i in 0 until 4) Prims.box(m, 1.2f, 9f, 0.2f, c(0xfff8e1), Mat.trs(cos(i * P2) * 4.5f, 16f + sin(i * P2) * 4.5f, 3.2f, rz = i * P2 + P2))
            SceneryModel(m, radius = 5f)
        },
        "balloon" to { _ ->
            val m = MeshData()
            Prims.sphere(m, 3f, Rgba.WHITE, Mat.trs(0f, 4f, 0f, sy = 1.15f), 16, 12)
            Prims.cylinder(m, 1f, 0.7f, 1.3f, c(0x8d6e63), Mat.trs(0f, -0.6f, 0f), 8)
            for (k in 0 until 4) Prims.cylinder(m, 0.04f, 0.04f, 2.4f, c(0x555555), Mat.trs(cos(k * P2) * 0.8f, 0.6f, sin(k * P2) * 0.8f), 4, false)
            SceneryModel(m, radius = 3.5f, tints = listOf(0xff5252, 0xffeb3b, 0x40c4ff, 0x69f0ae, 0xff80ab, 0xb388ff))
        },
        "mountain" to { _ ->
            val m = MeshData()
            Prims.cone(m, 160f, 190f, c(0x7d8aa0), Mat.trs(0f, 95f, 0f), 9)
            Prims.cone(m, 58f, 70f, c(0xffffff), Mat.trs(0f, 155f, 0f), 9)
            SceneryModel(m, radius = 170f)
        },
        "grandstand" to { lod ->
            // stepped seats packed with a colourful cheering crowd, facing the road (+Z)
            val m = MeshData(); val glow = MeshData()
            val len = 40f
            for (row in 0 until 6) Prims.box(m, len, 0.9f, 1.6f, c(if (row % 2 == 0) 0xe0e0e0 else 0xbdbdbd), Mat.trs(0f, 0.45f + row * 0.9f, -row * 1.6f))
            Prims.box(m, len, 9f, 0.6f, c(0x3949ab), Mat.trs(0f, 4.5f, -10f))
            Prims.box(m, len + 2f, 0.5f, 12f, c(0xff3b3b), Mat.trs(0f, 11f, -4.5f, rx = -0.12f))
            for (k in -1..1) Prims.cylinder(m, 0.25f, 0.25f, 10f, c(0xcfd8dc), Mat.trs(k * 18f, 6f, 1f), 6)
            if (lod == 0) {
                val r = Rng(len.toInt())
                val shirts = intArrayOf(0xff5252, 0xffeb3b, 0x40c4ff, 0x69f0ae, 0xff80ab, 0xffffff, 0xff9800)
                for (row in 0 until 6) {
                    var x = -len / 2 + 0.8f
                    while (x < len / 2 - 0.6f) {
                        val y = 0.9f + row * 0.9f; val z = -row * 1.6f - 0.3f
                        Prims.box(m, 0.6f, 0.8f, 0.4f, c(shirts[r.int(shirts.size)]), Mat.trs(x, y + 0.4f, z))
                        Prims.sphere(m, 0.26f, c(if (r.f() < 0.5f) 0xffcc99 else 0x8d5524), Mat.trs(x, y + 1.05f, z), 6, 4)
                        x += 0.75f + r.f() * 0.2f
                    }
                }
                // big light-up sign
                Prims.box(glow, len * 0.6f, 1.2f, 0.2f, Rgba(1f, 0.85f, 0.2f), Mat.trs(0f, 12f, 1.3f))
            }
            SceneryModel(m, glow, radius = 22f, trackside = true)
        },
        "flagRow" to { _ ->
            val m = MeshData()
            val cols = intArrayOf(0xff3b3b, 0xffd740, 0x40c4ff, 0x69f0ae, 0xff80ab)
            for (k in 0 until 5) {
                val x = (k - 2) * 3f
                Prims.cylinder(m, 0.08f, 0.08f, 6f, c(0xdddddd), Mat.trs(x, 3f, 0f), 5)
                Prims.box(m, 0.05f, 1.2f, 1.8f, c(cols[k]), Mat.trs(x, 5.3f, 0.9f))
            }
            m.windByHeight(0.3f)
            SceneryModel(m, radius = 7f, small = true, trackside = true)
        },
    )
}

/**
 * Places a theme's scenery around the track and batches it into static meshes per 300 m cell,
 * each with a detailed (near) and a simple (far) version: level of detail switching per frame.
 */
/**
 * Where every scenery model goes, batched per 300 m cell into near (detailed) and far (simple) meshes.
 * Pure geometry with no GPU calls. [missing] lists ids that have no builder yet.
 */
class SceneryLayout(private val track: TrackPath, private val theme: Theme, val cellSize: Float = 300f) {
    class Cell(val cx: Float, val cz: Float) {
        val near = MeshData(); val far = MeshData(); val glow = MeshData()
    }
    val cells = HashMap<Long, Cell>()
    val missing = ArrayList<String>()

    private fun cellOf(x: Float, z: Float): Cell {
        val ix = floor(x / cellSize).toInt(); val iz = floor(z / cellSize).toInt()
        val key = (ix.toLong() shl 32) or (iz.toLong() and 0xffffffffL)
        return cells.getOrPut(key) { Cell((ix + 0.5f) * cellSize, (iz + 0.5f) * cellSize) }
    }

    fun place(): SceneryLayout {
        val rng = Rng(theme.shape.seed * 31 + 7)
        // coarse copy of the centre line for distance tests
        val pts = ArrayList<FloatArray>()
        val f = Frame()
        var s = 0f
        while (s < track.length) { track.frame(s, f); pts.add(floatArrayOf(f.x, f.z)); s += 8f }
        val edge = track.halfWidth + 1f
        for (rule in theme.props) {
            val b = SceneryLibrary.builders[rule.id]
            if (b == null) { missing.add(rule.id); continue }
            val hi = b(0); val lo = b(1)
            var placed = 0; var tries = 0
            while (placed < rule.count && tries < rule.count * 20) {
                tries++
                val sc = rule.scaleMin + rng.f() * (rule.scaleMax - rule.scaleMin)
                val ps = rng.f() * track.length
                track.frame(ps, f)
                val side = if (rng.f() < 0.5f) -1f else 1f
                if (hi.trackside && abs(f.kappa) > 0.0025f) continue
                val r = if (hi.trackside) 0.5f else hi.radius * sc
                val dist = edge + r + rule.minDist + rng.f() * (rule.maxDist - rule.minDist)
                val x = f.x + f.rx * side * dist; val z = f.z + f.rz * side * dist
                // keep clear of every part of the road
                var ok = true
                val clear = edge + r * 0.8f
                for (p in pts) { val dx = p[0] - x; val dz = p[1] - z; if (dx * dx + dz * dz < clear * clear) { ok = false; break } }
                if (!ok) continue
                val ground = if (theme.ground != null) 0f else f.y - 6f
                val y = ground + rule.yMin + rng.f() * (rule.yMax - rule.yMin)
                // trackside things face the road; the rest get a random turn
                val yaw = if (hi.trackside) atan2(-f.rx * side, -f.rz * side) else rng.f() * 6.283f
                val t = Mat.trs(x, y, z, 0f, yaw, 0f, sc, sc, sc)
                val tint = if (hi.tints.isNotEmpty()) Rgba.hex(hi.tints[rng.int(hi.tints.size)]) else null
                val cell = cellOf(x, z)
                cell.near.append(hi.lit, t, tint)
                if (!hi.small) cell.far.append(lo.lit, t, tint)
                hi.glow?.let { cell.glow.append(it, t, tint) }
                placed++
            }
        }
        return this
    }
}

class Scenery(private val engine: Engine, private val scene: Scene, private val mats: MaterialLib, private val track: TrackPath, private val theme: Theme) {
    private class CellEnts(val cell: SceneryLayout.Cell) { var eNear = 0; var eFar = 0; var eGlow = 0; var near: Boolean? = null }
    private val cells = ArrayList<CellEnts>()
    private val meshes = ArrayList<GpuMesh>()
    private val ents = ArrayList<Int>()
    private lateinit var litMi: MaterialInstance
    private lateinit var glowMi: MaterialInstance
    var missing: List<String> = emptyList()

    fun build(windOn: Boolean) {
        val layout = SceneryLayout(track, theme).place()
        missing = layout.missing
        litMi = mats.lit.createInstance().also {
            it.setParameter("baseColor", 1f, 1f, 1f, 1f); it.setParameter("roughness", 0.8f); it.setParameter("metallic", 0f)
            it.setParameter("emissive", 0f, 0f, 0f, 0f); it.setParameter("wind", if (windOn) 1f else 0f)
        }
        glowMi = mats.glow.createInstance().also { it.setParameter("color", 3f, 3f, 3f, 1f) }
        for (cell in layout.cells.values) {
            val c = CellEnts(cell); cells.add(c)
            if (cell.near.vCount > 0) c.eNear = make(cell.near, litMi, true)
            if (cell.far.vCount > 0) c.eFar = make(cell.far, litMi, false)
            if (cell.glow.vCount > 0) c.eGlow = make(cell.glow, glowMi, false)
        }
    }

    private fun make(m: MeshData, mi: MaterialInstance, shadows: Boolean): Int {
        val g = Gpu.upload(engine, m); meshes.add(g)
        val e = Gpu.renderable(engine, g, mi, castShadows = shadows)
        ents.add(e); scene.addEntity(e)
        return e
    }

    fun setWind(on: Boolean) = litMi.setParameter("wind", if (on) 1f else 0f)

    /**
     * Level of detail: near cells use the detailed batch, far cells the simple one. A 60 m band between the
     * switch-in and switch-out distances stops a cell flipping back and forth (visible popping) when the
     * camera sits right on the boundary, and only cells that actually change are touched.
     */
    fun updateLod(camX: Float, camZ: Float) {
        val rm = engine.renderableManager
        for (i in cells.indices) {
            val c = cells[i]
            val d = hypot(c.cell.cx - camX, c.cell.cz - camZ)
            val near = when (c.near) { true -> d < LOD_OUT; false -> d < LOD_IN; null -> d < (LOD_IN + LOD_OUT) / 2 }
            if (near == c.near) continue
            c.near = near
            if (c.eNear != 0) rm.setLayerMask(rm.getInstance(c.eNear), 0xff, if (near) 0xff else 0)
            if (c.eFar != 0) rm.setLayerMask(rm.getInstance(c.eFar), 0xff, if (near) 0 else 0xff)
        }
    }

    private companion object { const val LOD_IN = 500f; const val LOD_OUT = 560f }

    fun destroy() {
        for (e in ents) { scene.remove(e); engine.destroyEntity(e) }
        meshes.forEach { it.destroy(engine) }
        if (::litMi.isInitialized) engine.destroyMaterialInstance(litMi)
        if (::glowMi.isInitialized) engine.destroyMaterialInstance(glowMi)
    }
}
