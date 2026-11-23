package com.vroombuddies.game.render

import com.google.android.filament.*
import com.vroombuddies.game.sim.VehicleParams
import com.vroombuddies.game.render.cars.*
import kotlin.math.*

/** Geometry for one car design, split by material. Model space: +Z forward, +Y up, origin on the ground under the centre. */
class CarParts {
    val paint = MeshData()   // painted body (paint material, colour chosen by the child)
    val trim = MeshData()    // bumpers, seats, grille, mirrors (lit, vertex colour)
    val glass = MeshData()   // windows (glossy dark)
    val lights = MeshData()  // headlights / tail lights (unlit bright)
    val wheel = MeshData()   // ONE wheel, axle along X, centred at origin
    var wheelRadius = 0.5f
    val wheelPos = ArrayList<FloatArray>()      // x, y, z of each wheel centre; first two are the front (steering) wheels
    val exhausts = ArrayList<FloatArray>()      // x, y, z of each boost flame
}

/** A car design: name, look and handling. New designs are added to [CarDesigns.all]. */
interface CarDesign {
    val id: String
    val name: String
    val tagline: String
    val params: VehicleParams
    fun build(): CarParts
}

private val TIRE = Rgba.hex(0x1d1e24)
private val RIM = Rgba.hex(0xd9dde3)
private val DARK = Rgba.hex(0x2a2c33)
private val WHITE = Rgba.hex(0xf7f7f7)
private val BLACK = Rgba.hex(0x101014)

object CarParts3 {
    /** Standard cartoon wheel: chunky tyre, shiny rim, hub cap. */
    fun wheel(m: MeshData, r: Float, w: Float, rim: Rgba = RIM) {
        val rot = Mat.trs(rz = (PI / 2).toFloat())
        Prims.cylinder(m, r, r, w, TIRE, rot, 22)
        Prims.torus(m, r * 0.86f, w * 0.32f, TIRE, Mat.trs(w * 0.32f, 0f, 0f, 0f, (PI / 2).toFloat(), 0f), 8, 22)
        Prims.torus(m, r * 0.86f, w * 0.32f, TIRE, Mat.trs(-w * 0.32f, 0f, 0f, 0f, (PI / 2).toFloat(), 0f), 8, 22)
        Prims.cylinder(m, r * 0.64f, r * 0.64f, w * 1.04f, DARK, rot, 28)
        Prims.cylinder(m, r * 0.22f, r * 0.22f, w * 1.12f, DARK, rot, 10)
        for (side in floatArrayOf(-1f, 1f)) {
            Prims.torus(m, r * 0.60f, r * 0.044f, rim,
                Mat.trs(side * w * 0.53f, 0f, 0f, ry = (PI / 2).toFloat()), 6, 28)
        }
        // spokes make the spin visible
        for (k in 0 until 5) Prims.box(m, w * 1.08f, r * 0.12f, r * 1.0f, rim, Mat.trs(rx = (k * PI * 2 / 5).toFloat()))
    }

    /** Friendly face: round headlight "eyes" with pupils and a smiling grille. */
    fun face(c: CarParts, z: Float, y: Float, eyeX: Float, eyeR: Float, smileW: Float) {
        for (s in floatArrayOf(-1f, 1f)) {
            Prims.sphere(c.trim, eyeR, WHITE, Mat.trs(s * eyeX, y, z, sx = 1.17f, sy = 0.84f, sz = 0.48f))
            Prims.sphere(c.glass, eyeR * 0.64f, Rgba.hex(0x27838b), Mat.trs(s * eyeX * 0.94f, y, z + eyeR * 0.29f, sz = 0.48f))
            Prims.sphere(c.trim, eyeR * 0.42f, BLACK, Mat.trs(s * eyeX * 0.94f, y + eyeR * 0.1f, z + eyeR * 0.53f, sz = 0.5f))
            Prims.sphere(c.lights, eyeR * 0.16f, Rgba(1f, 1f, 1f), Mat.trs(s * eyeX * 0.9f, y + eyeR * 0.3f, z + eyeR * 0.69f))
        }
        Prims.torus(c.trim, smileW, 0.07f, DARK, Mat.trs(0f, y - 0.05f + smileW * 0.6f, z + 0.02f, rz = (PI + 0.6).toFloat()), 6, 16, (PI - 1.2).toFloat())
    }
}

/** Sample design: Zoomy, a low cheeky sports car. */
object Zoomy : CarDesign {
    override val id = "zoomy"
    override val name = "Zoomy"
    override val tagline = "Super speedy!"
    override val params = VehicleParams(mass = 1f, topSpeed = 53f, boostSpeed = 82f)

    override fun build(): CarParts {
        val c = CarParts()
        c.wheelRadius = 0.52f
        // Loft a continuous shell from tail to nose. Wider shoulders rise over each wheel.
        val rings = arrayOf(
            floatArrayOf(-2.18f, 0.70f, 0.57f, 0.94f),
            floatArrayOf(-2.04f, 1.01f, 0.46f, 1.14f),
            floatArrayOf(-1.48f, 1.18f, 0.40f, 1.30f),
            floatArrayOf(-0.86f, 1.12f, 0.38f, 1.23f),
            floatArrayOf(0.10f, 1.07f, 0.38f, 1.18f),
            floatArrayOf(1.12f, 1.16f, 0.40f, 1.21f),
            floatArrayOf(1.72f, 1.12f, 0.43f, 1.12f),
            floatArrayOf(2.09f, 0.95f, 0.50f, 1.00f),
            floatArrayOf(2.21f, 0.70f, 0.58f, 0.91f))
        val segments = 32
        fun contour(value: Float) = sign(value) * abs(value).pow(0.56f)
        fun point(row: Int, angle: Float): FloatArray {
            val (z, width, low, high) = rings[row]
            return floatArrayOf(width * contour(cos(angle)),
                (low + high + (high - low) * contour(sin(angle))) * 0.5f, z)
        }
        for (row in rings.indices) {
            for (side in 0 until segments) {
                val angle = (side * 2.0 * PI / segments).toFloat()
                val p = point(row, angle)
                val a = point(row, angle - 0.02f)
                val b = point(row, angle + 0.02f)
                val rear = point(max(0, row - 1), angle)
                val fore = point(min(rings.lastIndex, row + 1), angle)
                val dx = b[0] - a[0]
                val dy = b[1] - a[1]
                val dz = fore[2] - rear[2]
                val nx = dy * dz
                val ny = -dx * dz
                val nz = dx * (fore[1] - rear[1]) - dy * (fore[0] - rear[0])
                val length = sqrt(nx * nx + ny * ny + nz * nz)
                c.paint.vertex(p[0], p[1], p[2], nx / length, ny / length, nz / length, Rgba.WHITE)
            }
        }
        for (row in 0 until rings.lastIndex) for (side in 0 until segments) {
            val a = row * segments + side
            val b = row * segments + (side + 1) % segments
            c.paint.quad(a, b, b + segments, a + segments)
        }
        for (row in listOf(0, rings.lastIndex)) {
            val ring = rings[row]
            val cap = c.paint.vertex(0f, (ring[2] + ring[3]) / 2f, ring[0], 0f, 0f,
                if (row == 0) -1f else 1f, Rgba.WHITE)
            for (side in 0 until segments) {
                val a = row * segments + side
                val b = row * segments + (side + 1) % segments
                if (row == 0) c.paint.tri(cap, b, a) else c.paint.tri(cap, a, b)
            }
        }
        Prims.sphere(c.glass, 1f, Rgba.hex(0x244e61),
            Mat.trs(0f, 1.10f, -0.31f, sx = 0.88f, sy = 0.67f, sz = 1.22f), 32, 18)
        Prims.sphere(c.paint, 1f, Rgba.WHITE,
            Mat.trs(0f, 1.65f, -0.46f, sx = 0.60f, sy = 0.15f, sz = 0.74f), 28, 14)
        for (s in floatArrayOf(-1f, 1f)) {
            Prims.sphere(c.paint, 1f, Rgba.WHITE,
                Mat.trs(s * 0.91f, 1.30f, 0.30f, sx = 0.19f, sy = 0.10f, sz = 0.25f))
        }
        // spoiler
        Prims.roundBox(c.trim, 2.2f, 0.12f, 0.55f, 0.05f, DARK, Mat.trs(0f, 1.62f, -2.0f))
        for (s in floatArrayOf(-0.7f, 0.7f)) Prims.box(c.trim, 0.12f, 0.45f, 0.25f, DARK, Mat.trs(s, 1.35f, -1.95f))
        // bumpers and side skirts
        Prims.roundBox(c.trim, 2.36f, 0.3f, 0.4f, 0.14f, DARK, Mat.trs(0f, 0.45f, 2.1f))
        Prims.roundBox(c.trim, 2.36f, 0.3f, 0.4f, 0.14f, DARK, Mat.trs(0f, 0.45f, -2.1f))
        CarParts3.face(c, 2.18f, 0.88f, 0.62f, 0.3f, 0.35f)
        // tail lights
        for (s in floatArrayOf(-0.75f, 0.75f)) Prims.roundBox(c.lights, 0.45f, 0.18f, 0.08f, 0.06f, Rgba(1f, 0.08f, 0.05f), Mat.trs(s, 0.92f, -2.21f))
        // racing number disc on the doors
        for (s in floatArrayOf(-1f, 1f)) Prims.cylinder(c.trim, 0.42f, 0.42f, 0.04f, WHITE, Mat.trs(s * 1.16f, 0.82f, 0f, rz = (PI / 2).toFloat()), 18)
        CarParts3.wheel(c.wheel, c.wheelRadius, 0.42f)
        c.wheelPos.add(floatArrayOf(-1.08f, c.wheelRadius, 1.35f)); c.wheelPos.add(floatArrayOf(1.08f, c.wheelRadius, 1.35f))
        c.wheelPos.add(floatArrayOf(-1.08f, c.wheelRadius, -1.35f)); c.wheelPos.add(floatArrayOf(1.08f, c.wheelRadius, -1.35f))
        c.exhausts.add(floatArrayOf(-0.5f, 0.5f, -2.3f)); c.exhausts.add(floatArrayOf(0.5f, 0.5f, -2.3f))
        for (e in c.exhausts) Prims.cylinder(c.trim, 0.14f, 0.16f, 0.35f, RIM, Mat.trs(e[0], e[1], e[2] + 0.1f, rx = (PI / 2).toFloat()), 12)
        return c
    }
}

object CarDesigns {
    /** Every car in the picker, in order. Designs live in render/cars/. */
    val all: List<CarDesign> = listOf(Zoomy, Bubbles, Stomper, Rocket, Scoot, Hopper, Bumble)
}

data class Paint(val name: String, val color: Int, val metallic: Float = 0.2f, val roughness: Float = 0.32f, val rainbow: Boolean = false)

object Paints {
    /** Two rows in the picker: bright colours first, then the fancy shiny ones. Keep the count even. */
    val all = listOf(
        Paint("Cherry Red", 0xe8222e), Paint("Orange Pop", 0xff7a1a), Paint("Sunny Yellow", 0xffc61a), Paint("Lime Green", 0x45d14a),
        Paint("Mint", 0x2fe0b0), Paint("Sky Blue", 0x37c8ff), Paint("Ocean Blue", 0x1e6fff), Paint("Grape", 0x8a3dff),
        Paint("Bubblegum", 0xff5fb2), Paint("Snow White", 0xf4f6fa), Paint("Midnight", 0x1c2140), Paint("Candy Apple", 0xd8103a, 0.85f, 0.18f),
        Paint("Sparkle Pink", 0xff4fae, 0.8f, 0.2f), Paint("Shiny Gold", 0xffc23a, 1f, 0.25f), Paint("Chrome", 0xe6ebf0, 1f, 0.08f),
        Paint("Rainbow", 0xffffff, rainbow = true),
    )
}

/**
 * A car in the scene: body parts under a root transform, four wheels that steer and spin,
 * and boost flames with a real light that lights up the road.
 */
class CarInstance(private val engine: Engine, private val scene: Scene, mats: MaterialLib, design: CarDesign, paint: Paint, private val withLight: Boolean) {
    val root = EntityManager.get().create()
    private val body = EntityManager.get().create()
    private val parts: CarParts = design.build()
    private val meshes = ArrayList<GpuMesh>()
    private val ents = ArrayList<Int>()
    private val mis = ArrayList<MaterialInstance>()
    private val wheels = IntArray(4)
    private val flames = ArrayList<Int>()
    val boostLight = EntityManager.get().create()
    val paintMi: MaterialInstance

    init {
        android.util.Log.i("Vroom", "car geometry built")
        val tm = engine.transformManager
        tm.create(root); tm.create(body, tm.getInstance(root))
        paintMi = mats.paint.createInstance().also { mis.add(it) }
        setPaint(paint)
        val trimMi = lit(mats, 0.45f, 0f)
        val glassMi = lit(mats, 0.04f, 0.1f)
        val lightMi = mats.glow.createInstance().also { it.setParameter("color", 6f, 6f, 6f, 1f); mis.add(it) }
        addPart(parts.paint, paintMi, body); addPart(parts.trim, trimMi, body); addPart(parts.glass, glassMi, body)
        addPart(parts.lights, lightMi, body, shadows = false)
        android.util.Log.i("Vroom", "car body uploaded")
        val wheelMesh = Gpu.upload(engine, parts.wheel).also { meshes.add(it) }
        val wheelMi = lit(mats, 0.55f, 0f)
        for (i in 0 until 4) {
            val e = Gpu.renderable(engine, wheelMesh, wheelMi)
            tm.create(e, tm.getInstance(root)); wheels[i] = e; ents.add(e); scene.addEntity(e)
        }
        // boost flames: inner white-yellow core + outer orange cone
        android.util.Log.i("Vroom", "wheels uploaded")
        val fm = MeshData()
        Prims.cone(fm, 0.23f, 1.48f, Rgba(0.03f, 0.60f, 0.88f), Mat.trs(0f, 0f, -0.8f, rx = (-PI / 2).toFloat()))
        Prims.cone(fm, 0.14f, 1.75f, Rgba(0.72f, 1f, 0.94f), Mat.trs(0f, 0f, -0.5f, rx = (-PI / 2).toFloat()))
        val flameMesh = Gpu.upload(engine, fm).also { meshes.add(it) }
        val flameMi = mats.glow.createInstance().also { it.setParameter("color", 8f, 8f, 8f, 1f); mis.add(it) }
        for (ex in parts.exhausts) {
            val e = Gpu.renderable(engine, flameMesh, flameMi, castShadows = false, receiveShadows = false)
            tm.create(e, tm.getInstance(body), Mat.trs(ex[0], ex[1], ex[2], sx = 0f, sy = 0f, sz = 0f))
            flames.add(e); ents.add(e); scene.addEntity(e)
        }
        if (withLight) {
            LightManager.Builder(LightManager.Type.POINT).color(1f, 0.55f, 0.15f).intensity(0f).falloff(14f).castShadows(false).build(engine, boostLight)
            tm.create(boostLight, tm.getInstance(root), Mat.trs(0f, 1f, -3f))
            scene.addEntity(boostLight)
        }
        scene.addEntity(root)
        android.util.Log.i("Vroom", "car ready")
    }

    private fun lit(mats: MaterialLib, rough: Float, metal: Float) = mats.lit.createInstance().also {
        it.setParameter("baseColor", 1f, 1f, 1f, 1f); it.setParameter("roughness", rough); it.setParameter("metallic", metal)
        it.setParameter("emissive", 0f, 0f, 0f, 0f); it.setParameter("wind", 0f); mis.add(it)
    }

    private fun addPart(m: MeshData, mi: MaterialInstance, parent: Int, shadows: Boolean = true) {
        if (m.vCount == 0) return
        val g = Gpu.upload(engine, m).also { meshes.add(it) }
        val e = Gpu.renderable(engine, g, mi, castShadows = shadows)
        engine.transformManager.create(e, engine.transformManager.getInstance(parent))
        ents.add(e); scene.addEntity(e)
    }

    fun setPaint(p: Paint) {
        val c = Rgba.hex(p.color)
        paintMi.setParameter("baseColor", c.r, c.g, c.b, 1f)
        paintMi.setParameter("metallic", p.metallic)
        paintMi.setParameter("roughness", p.roughness)
        paintMi.setParameter("rainbow", if (p.rainbow) 1f else 0f)
        paintMi.setParameter("glow", 0f)
    }

    private val m = FloatArray(16); private val m2 = FloatArray(16)
    private val ma = FloatArray(16); private val mb = FloatArray(16)
    private var shownMask = -1
    private var flamesOn = true

    /** Place the car. [world] = root matrix; body motion and wheel spin/steer in car space. Allocation free. */
    fun update(world: FloatArray, pitch: Float, roll: Float, bounce: Float, steer: Float, spin: Float, boost: Float, time: Float, blink: Boolean) {
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(root), world)
        tm.setTransform(tm.getInstance(body), Mat.trsInto(m, 0f, bounce, 0f, pitch, 0f, -roll))
        for (i in 0 until 4) {
            val p = parts.wheelPos[i]
            val st = if (i < 2) steer else 0f
            tm.setTransform(tm.getInstance(wheels[i]), Mat.mul(Mat.trsInto(ma, p[0], p[1] + bounce * 0.3f, p[2], 0f, -st, 0f), Mat.trsInto(mb, rx = spin), m2))
        }
        val fl = if (boost > 0f) (0.8f + 0.35f * kotlin.math.sin(time * 47f) + 0.15f * kotlin.math.sin(time * 91f)) * boost else 0f
        val rm = engine.renderableManager
        // flames are hidden (not squashed to zero size, which leaves a degenerate matrix) when not boosting
        val wantFlames = fl > 0f && !blink
        if (wantFlames != flamesOn) { flamesOn = wantFlames; for (e in flames) rm.setLayerMask(rm.getInstance(e), 0xff, if (wantFlames) 0xff else 0) }
        if (wantFlames) for (k in flames.indices) {
            val ex = parts.exhausts[k]
            tm.setTransform(tm.getInstance(flames[k]), Mat.trsInto(m, ex[0], ex[1], ex[2], sx = fl, sy = fl, sz = fl * (1f + 0.3f * kotlin.math.sin(time * 33f + k))))
        }
        if (withLight) engine.lightManager.setIntensity(engine.lightManager.getInstance(boostLight), 450f * boost)
        // blink while respawning
        val vis = if (blink) 0 else 0xff
        if (vis != shownMask) {
            shownMask = vis
            for (i in ents.indices) { val e = ents[i]; if (e !in flames) rm.setLayerMask(rm.getInstance(e), 0xff, vis) }
        }
    }

    fun destroy() {
        for (e in ents) { scene.remove(e); engine.destroyEntity(e) }
        scene.remove(root); scene.remove(boostLight)
        if (withLight) engine.lightManager.destroy(boostLight)
        engine.transformManager.destroy(body); engine.transformManager.destroy(root)
        meshes.forEach { it.destroy(engine) }
        mis.forEach { engine.destroyMaterialInstance(it) }
    }
}
