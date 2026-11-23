package com.vroombuddies.game.render

import com.google.android.filament.*
import com.vroombuddies.game.content.Themes
import kotlin.math.*

/** The 3D backdrop of the menus: the chosen car spinning on a turntable under a bright sky. */
class Showroom(private val engine: Engine, private val scene: Scene, private val view: View, private val camera: Camera, private val mats: MaterialLib, private val settings: GraphicsSettings) {
    private val theme = Themes.all[0].copy(skyTop = 0x132238, skyHorizon = 0x698096, skyBottom = 0x344355)
    private val env = Environment(engine, scene, mats, theme)
    private var car: CarInstance? = null
    private val ents = ArrayList<Int>()
    private val meshes = ArrayList<GpuMesh>()
    private val mis = ArrayList<MaterialInstance>()
    private var angle = 0.22f
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
        Prims.cylinder(m, 5.2f, 5.4f, 0.5f, Rgba.hex(0x50606c), Mat.trs(0f, -0.25f, 0f), 48)
        Prims.torus(m, 5.3f, 0.18f, Rgba.hex(0x8cf8ed), Mat.trs(0f, 0.02f, 0f, rx = (PI / 2).toFloat()), 8, 48)
        val g = Gpu.upload(engine, m).also { meshes.add(it) }
        val mi = mats.lit.createInstance().also {
            it.setParameter("baseColor", 1f, 1f, 1f, 1f); it.setParameter("roughness", 0.25f); it.setParameter("metallic", 0f)
            it.setParameter("emissive", 0f, 0f, 0f, 0f); it.setParameter("wind", 0f); mis.add(it)
        }
        val e = Gpu.renderable(engine, g, mi); ents.add(e); scene.addEntity(e)
        val f = MeshData()
        val lights = MeshData()
        Prims.plane(f, 140f, 140f, Rgba.hex(0x293541), Mat.trs(0f, -0.51f, 0f))
        // Faceted walls, recessed light ribbons and overhead softboxes frame the turntable.
        for (i in 0 until 24) {
            val angle = (2.0 * PI * i / 24).toFloat()
            val x = sin(angle) * 13f
            val z = cos(angle) * 13f
            Prims.box(f, 3.52f, 9f, 0.32f, Rgba.hex(if (i % 2 == 0) 0x3a4858 else 0x323e50),
                Mat.trs(x, 3.94f, z, ry = angle))
            val lamp = Rgba.hex(if (i % 4 < 2) 0xc5fcff else 0xffba79)
            Prims.box(lights, 0.09f, 5.7f, 0.08f, lamp,
                Mat.trs(x * 0.975f, 3.3f, z * 0.975f, ry = angle, rz = 0.24f))
            if (i % 3 == 0) Prims.box(lights, 2.8f, 0.12f, 1.15f, Rgba.WHITE,
                Mat.trs(x * 0.60f, 6.5f, z * 0.60f, ry = angle))
        }
        for (radius in listOf(4.62f, 5.12f)) Prims.torus(lights, radius, 0.018f,
            Rgba.hex(0x70e5db), Mat.trs(0f, 0.017f, 0f, rx = (PI/2).toFloat()), 5, 96)
        val lg = Gpu.upload(engine, lights).also { meshes.add(it) }
        val lm = mats.glow.createInstance().also { it.setParameter("color", 2.1f, 2.1f, 2.1f, 1f); mis.add(it) }
        val le = Gpu.renderable(engine, lg, lm, castShadows = false, receiveShadows = false)
        ents.add(le)
        scene.addEntity(le)
        val fg = Gpu.upload(engine, f).also { meshes.add(it) }
        val fmi = mats.lit.createInstance().also {
            it.setParameter("baseColor", 1f, 1f, 1f, 1f); it.setParameter("roughness", 0.85f); it.setParameter("metallic", 0f)
            it.setParameter("emissive", 0f, 0f, 0f, 0f); it.setParameter("wind", 0f); mis.add(it)
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

    fun update(dt: Float) {
        t += dt
        spinBoost = max(0f, spinBoost - dt)
        angle += dt * (0.28f + spinBoost * 0.64f)
        car?.update(Mat.trs(0f, 0f, 0f, ry = angle), 0f, 0f, 0f, 0.09f, 0f, 0f, t, false)
        val ex = 5.8f; val ey = 2.12f; val ez = 5.8f
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
