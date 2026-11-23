package com.vroombuddies.game.render

import com.google.android.filament.*
import com.vroombuddies.game.content.Themes
import kotlin.math.*

/** The 3D backdrop of the menus: the chosen car spinning on a turntable under a bright sky. */
class Showroom(private val engine: Engine, private val scene: Scene, private val view: View, private val camera: Camera, private val mats: MaterialLib, private val settings: GraphicsSettings) {
    private val theme = Themes.all[0].copy(skyTop = 0x259bff, skyHorizon = 0xb1efff, skyBottom = 0x62cbdc, sunColor = 0xffeac6)
    private val env = Environment(engine, scene, mats, theme)
    private var car: CarInstance? = null
    private val ents = ArrayList<Int>()
    private val meshes = ArrayList<GpuMesh>()
    private val mis = ArrayList<MaterialInstance>()
    private var wheelEntity = 0
    private val wheelTransform = FloatArray(16)
    private var angle = 0.12f
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
        env.build(settings.shadowMapSize.coerceAtMost(1024), 1)
        env.applyFog(view, false)
        val m = MeshData()
        // An enamel launch deck with a striped bumper, track tiles and a recessed cream centre.
        val horizontal = (PI / 2).toFloat()
        Prims.cylinder(m, 4.48f, 4.72f, 0.51f, Rgba.hex(0xf68931), Mat.trs(0f,-0.32f,0f), 72)
        Prims.cylinder(m, 4.35f, 4.48f, 0.16f, Rgba.hex(0x1768ad), Mat.trs(0f,-0.09f,0f), 72)
        Prims.cylinder(m, 3.88f, 3.90f, 0.04f, Rgba.hex(0xb7edee), Mat.trs(0f,-0.024f,0f), 72)
        for (i in 0 until 48) {
            val a = (2 * PI * i / 48).toFloat()
            Prims.box(m, 0.38f, 0.04f, 0.25f, Rgba.hex(if(i % 2 == 0) 0xffee93 else 0x43b5db),
                Mat.trs(sin(a)*4.13f,-0.003f,cos(a)*4.13f,ry=a))
        }
        val g = Gpu.upload(engine, m).also { meshes.add(it) }
        val mi = mats.lit.createInstance().also {
            it.setParameter("baseColor", 1f, 1f, 1f, 1f); it.setParameter("roughness", 0.25f); it.setParameter("metallic", 0f)
            it.setParameter("emissive", 0f, 0f, 0f, 0f); it.setParameter("wind", 0f); mis.add(it)
        }
        val e = Gpu.renderable(engine, g, mi); ents.add(e); scene.addEntity(e)
        val f = MeshData()
        val lights = MeshData()
        // A seaside race festival behind the car. Keep the near deck open and the detail in the middle distance.
        fun box(w:Float,h:Float,d:Float,col:Int,x:Float,y:Float,z:Float, yaw:Float=0f) =
            Prims.box(f,w,h,d,Rgba.hex(col),Mat.trs(x,y,z,ry=yaw))
        Prims.plane(f, 260f, 260f, Rgba.hex(0x39c4cf), Mat.trs(0f,-4.2f,0f))
        Prims.cylinder(f, 10f, 10.3f, 0.52f, Rgba.hex(0xe2a873), Mat.trs(0f,-0.85f,0f),64)
        Prims.cylinder(f, 9.94f, 10f, 0.085f, Rgba.hex(0xffdc9c), Mat.trs(0f,-0.55f,0f),64)
        val rng = com.vroombuddies.game.sim.Rng(512)
        val facades = intArrayOf(0x50c9d0,0xffac78,0x9db7ee,0xffc96e,0x85d7af,0xfe91a2)
        for (i in 0 until 46) {
            val a = (2*PI*i/46).toFloat()
            val r = 35f + rng.f()*15f
            val x = sin(a)*r; val z = cos(a)*r
            val h = 4f + rng.f()*9f; val w = 2.4f + rng.f()*2.8f
            val col = facades[i % facades.size]
            box(w,h,3.4f,col,x,h/2-4f,z,a)
            box(w+0.22f,0.42f,3.62f,0xf4f3d8,x,h-4f,z,a)
            for (j in 1..3) {
                val wy = h * j / 4 - 3.4f
                box(w*0.76f,0.34f,0.1f,0x398ba7,x-sin(a)*1.72f,wy,z-cos(a)*1.72f,a)
            }
        }
        for (i in 0 until 13) {
            val a = (2*PI*i/13).toFloat()
            for (j in -1..1) Prims.sphere(f, 1f, Rgba.hex(0xfff7db),
                Mat.trs(sin(a)*57f + j*2.5f, 8f + i%4 + if(j==0) 1f else 0f, cos(a)*57f,
                    sx=3.7f,sy=1.35f,sz=1.8f),12,7)
        }
        // Pennants curve around the launch platform, below the car silhouette.
        for (i in 0 until 28) {
            val a = (2*PI*i/28).toFloat()
            val x = sin(a)*9f; val z = cos(a)*9f
            if(i % 4 == 0) Prims.cylinder(f,0.12f,0.15f,2.25f,Rgba.hex(0x287bb2),Mat.trs(x,0.49f,z),8)
            val b = (2*PI*(i+1)/28).toFloat()
            val y = 1.36f - 0.3f*sin((i%4)*PI.toFloat()/4f)
            val mx = (x+sin(b)*9f)/2; val mz = (z+cos(b)*9f)/2
            val pose = Mat.trs(mx,y,mz,ry=(a+b)*0.5f)
            Prims.box(f,2.07f,0.045f,0.048f,Rgba.hex(0x246e94),pose)
            val flag = MeshData()
            val col = Rgba.hex(facades[(i*5)%facades.size])
            val l = flag.vertex(-0.55f,0f,0f,0f,0f,1f,col)
            val r = flag.vertex(0.55f,0f,0f,0f,0f,1f,col)
            val tip = flag.vertex(0f,-0.78f,0.09f,0f,0f,1f,col)
            flag.tri(l,tip,r); flag.tri(l,r,tip)
            f.append(flag,pose)
        }
        val wheel = MeshData()
        for (rad in listOf(3.95f,4.3f)) Prims.torus(wheel,rad,0.12f,Rgba.hex(0xffa43d),null,7,56)
        for(i in 0 until 12) {
            val a=(2*PI*i/12).toFloat()
            Prims.box(wheel,0.08f,4.1f,0.10f,Rgba.hex(0xffeab1),
                Mat.trs(-sin(a)*2.05f,cos(a)*2.05f,0f,rz=a))
            Prims.sphere(wheel,0.53f,Rgba.hex(facades[i%facades.size]),
                Mat.trs(-sin(a)*4.33f,cos(a)*4.33f,0.10f,sx=1.2f,sy=0.86f,sz=0.8f),12,8)
        }
        Prims.cylinder(wheel,0.42f,0.42f,0.45f,Rgba.hex(0xfb6e5f),Mat.trs(rx=horizontal),20)
        val wheelMesh = Gpu.upload(engine,wheel).also { meshes.add(it) }
        wheelEntity = Gpu.renderable(engine,wheelMesh,mi)
        val tm = engine.transformManager
        if (!tm.hasComponent(wheelEntity)) tm.create(wheelEntity)
        ents.add(wheelEntity); scene.addEntity(wheelEntity)
        for (side in listOf(-1f,1f)) Prims.box(f,0.34f,8.8f,0.44f,Rgba.hex(0xf38c43),
            Mat.trs(-4f+side*1.24f,0.1f,-19.7f,rz=side*0.28f))
        Prims.torus(lights,3.93f,0.045f,Rgba.hex(0xffe667),
            Mat.trs(0f,0.002f,0f,rx=horizontal),6,72)
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
            angle = -0.22f
            spinBoost = 1f
        } else car?.setPaint(p)
        design = d; paint = p
    }

    fun setPaint(p: Paint) { paint = p; car?.setPaint(p); spinBoost = 0.5f }

    fun update(dt: Float) {
        t += dt
        spinBoost = max(0f, spinBoost - dt)
        engine.transformManager.setTransform(engine.transformManager.getInstance(wheelEntity),
            Mat.trsInto(wheelTransform,-4f,4.25f,-20f,ry=0.48f,rz=t*0.085f))
        angle += dt * (0.15f + spinBoost * 0.6f)
        car?.update(Mat.trs(0f, 0f, 0f, ry = angle), 0f, 0f, 0f, 0.09f, 0f, 0f, t, false)
        val ex = 5.1f; val ey = 2.25f; val ez = 5.1f
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
