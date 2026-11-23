package com.vroombuddies.game.render

import com.google.android.filament.*
import com.vroombuddies.game.content.Themes
import kotlin.math.*

/** The 3D backdrop of the menus: the chosen car spinning on a turntable under a bright sky. */
class Showroom(private val engine: Engine, private val scene: Scene, private val view: View, private val camera: Camera, private val mats: MaterialLib, private val settings: GraphicsSettings) {
    private val theme = Themes.all[0]
    private val env = Environment(engine, scene, mats, theme)
    private var car: CarInstance? = null
    private val ents = ArrayList<Int>()
    private val meshes = ArrayList<GpuMesh>()
    private val mis = ArrayList<MaterialInstance>()
    private var t = 0f
    private var aspect = 1.8f
    private var spinBoost = 0f
    private var grading: ColorGrading? = null
    var design: CarDesign = CarDesigns.all[0]; private set
    var paint: Paint = Paints.all[0]; private set
    /** Shift the car sideways on screen (menus put buttons on one side). */
    var offsetX = 0f
    /** Shift the car up on screen (pickers keep their buttons along the bottom). */
    var offsetY = 0f

    init {
        env.build(0, 1)
        env.applyFog(view, false)
        val m = MeshData()
        Prims.cylinder(m, 5.2f, 5.4f, 0.5f, Rgba.hex(0xffffff), Mat.trs(0f, -0.25f, 0f), 48)
        Prims.torus(m, 5.3f, 0.18f, Rgba.hex(0xffd23f), Mat.trs(0f, 0.02f, 0f, rx = (PI / 2).toFloat()), 8, 48)
        val g = Gpu.upload(engine, m).also { meshes.add(it) }
        val mi = mats.lit.createInstance().also {
            it.setParameter("baseColor", 1f, 1f, 1f, 1f); it.setParameter("roughness", 0.25f); it.setParameter("metallic", 0f)
            it.setParameter("emissive", 0f, 0f, 0f, 0f); it.setParameter("wind", 0f); mis.add(it)
        }
        val e = Gpu.renderable(engine, g, mi); ents.add(e); scene.addEntity(e)
        // a lush field so the showroom isn't empty
        val f = MeshData()
        Prims.plane(f, 400f, 400f, Rgba.hex(0x6cc24a), Mat.trs(0f, -0.5f, 0f))
        val gb = SceneryLibrary.builders.getValue("roundTree")(0)
        val bush = SceneryLibrary.builders.getValue("flower")(0)
        val rng = com.vroombuddies.game.sim.Rng(4)
        for (i in 0 until 26) { val a = rng.f() * 6.28f; val r = 22f + rng.f() * 40f; f.append(gb.lit, Mat.trs(cos(a) * r, -0.5f, sin(a) * r - 10f, ry = rng.f() * 6f, sx = 1.2f, sy = 1.2f, sz = 1.2f), Rgba.hex(0x5cc160)) }
        for (i in 0 until 160) { val a = rng.f() * 6.28f; val r = 7f + rng.f() * 25f; f.append(bush.lit, Mat.trs(cos(a) * r, -0.5f, sin(a) * r, ry = rng.f() * 6f), Rgba.hex(listOf(0xff5fa2, 0xffffff, 0xffe14d, 0x8a7dff)[rng.int(4)])) }
        val fg = Gpu.upload(engine, f).also { meshes.add(it) }
        val fmi = mats.lit.createInstance().also {
            it.setParameter("baseColor", 1f, 1f, 1f, 1f); it.setParameter("roughness", 0.85f); it.setParameter("metallic", 0f)
            it.setParameter("emissive", 0f, 0f, 0f, 0f); it.setParameter("wind", 1f); mis.add(it)
        }
        val fe = Gpu.renderable(engine, fg, fmi); ents.add(fe); scene.addEntity(fe)
        grading = ColorGrading.Builder().toneMapper(ToneMapper.Agx()).saturation(1.2f).vibrance(1.15f).build(engine)
        view.colorGrading = grading
        setCar(design, paint)
    }

    fun setAspect(a: Float) { aspect = a }

    fun setCar(d: CarDesign, p: Paint) {
        if (d !== design || car == null) {
            car?.destroy()
            car = CarInstance(engine, scene, mats, d, p, false)
            spinBoost = 1f
        } else car?.setPaint(p)
        design = d; paint = p
    }

    fun setPaint(p: Paint) { paint = p; car?.setPaint(p); spinBoost = 0.5f }

    private val mtx = FloatArray(16)
    fun update(dt: Float) {
        t += dt
        spinBoost = max(0f, spinBoost - dt)
        val a = t * 0.5f + spinBoost * spinBoost * 4f
        val hop = abs(sin(t * 2.2f)) * 0.05f
        car?.update(Mat.trs(0f, hop, 0f, 0f, a, 0f), 0f, 0f, 0f, sin(t) * 0.3f, t * 4f, 0f, t, false)
        val ex = 6.6f; val ey = 2.6f; val ez = 6.6f
        camera.setProjection(38.0, aspect.toDouble(), 0.3, 3000.0, Camera.Fov.VERTICAL)
        camera.setShift(offsetX.toDouble(), offsetY.toDouble())
        camera.lookAt(ex.toDouble(), ey.toDouble(), ez.toDouble(), 0.0, 0.9, 0.0, 0.0, 1.0, 0.0)
        env.follow(ex, ey, ez)
        settings.setDof(view, false, 12f, camera)
    }

    fun destroy() {
        car?.destroy()
        for (e in ents) { scene.remove(e); engine.destroyEntity(e) }
        meshes.forEach { it.destroy(engine) }; mis.forEach { engine.destroyMaterialInstance(it) }
        env.destroy()
        camera.setShift(0.0, 0.0)
        grading?.let { view.colorGrading = null; engine.destroyColorGrading(it) }
    }
}
