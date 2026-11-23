package com.vroombuddies.game.render

import android.util.Half
import com.google.android.filament.*
import com.google.android.filament.utils.IBLPrefilterContext
import com.vroombuddies.game.content.Theme
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Sky, sun and sky light for a theme:
 *  - a gradient sky dome mesh (unlit, follows the camera),
 *  - image based lighting: the same sky rendered into an HDR panorama, prefiltered by Filament
 *    into glossy reflections, plus 9-term spherical harmonics fitted on the CPU for soft ambient light
 *    (this is the "global illumination" approximation: light from every direction of the sky),
 *  - a shadow-casting sun with soft shadows,
 *  - height fog tinted by the sky.
 */
class Environment(private val engine: Engine, private val scene: Scene, private val mats: MaterialLib, val theme: Theme) {
    val sunEntity: Int = EntityManager.get().create()
    var skyEntity = 0
    private var skyMesh: GpuMesh? = null
    private var skyMi: MaterialInstance? = null
    private var ibl: IndirectLight? = null
    private var reflections: Texture? = null
    val sunDir = FloatArray(3)

    private val top = Rgba.hex(theme.skyTop); private val hor = Rgba.hex(theme.skyHorizon); private val bot = Rgba.hex(theme.skyBottom)
    private val sunCol = Rgba.hex(theme.sunColor)

    fun skyColor(dx: Float, dy: Float, dz: Float, withSun: Boolean): FloatArray {
        val out = FloatArray(3)
        if (dy >= 0f) {
            val t = dy.pow(0.55f)
            out[0] = hor.r + (top.r - hor.r) * t; out[1] = hor.g + (top.g - hor.g) * t; out[2] = hor.b + (top.b - hor.b) * t
        } else {
            val t = min(1f, (-dy * 4f))
            out[0] = hor.r + (bot.r - hor.r) * t; out[1] = hor.g + (bot.g - hor.g) * t; out[2] = hor.b + (bot.b - hor.b) * t
        }
        if (withSun) {
            val cosA = max(0f, -(dx * sunDir[0] + dy * sunDir[1] + dz * sunDir[2]))
            val glow = cosA.pow(64f) * 1.5f + cosA.pow(8f) * 0.25f + (if (cosA > 0.9995f) 30f else 0f)
            out[0] += sunCol.r * glow; out[1] += sunCol.g * glow; out[2] += sunCol.b * glow
        }
        return out
    }

    fun build(shadowMapSize: Int, cascades: Int) {
        val el = theme.sunElevation; val az = theme.sunAzimuth
        // direction the light travels (from sun toward ground)
        sunDir[0] = -cos(el) * cos(az); sunDir[1] = -sin(el); sunDir[2] = -cos(el) * sin(az)
        val n = sqrt(sunDir[0] * sunDir[0] + sunDir[1] * sunDir[1] + sunDir[2] * sunDir[2])
        for (i in 0..2) sunDir[i] /= n
        buildSun(shadowMapSize, cascades)
        buildSkyDome()
        buildIbl()
    }

    fun buildSun(shadowMapSize: Int, cascades: Int) {
        if (engine.lightManager.hasComponent(sunEntity)) { scene.remove(sunEntity); engine.lightManager.destroy(sunEntity) }
        val so = LightManager.ShadowOptions().apply {
            mapSize = shadowMapSize
            shadowCascades = cascades
            shadowFar = 160f
            // stable cascades: texels snap to the world so shadow edges don't crawl or shimmer while
            // the camera races along (LiSPSM sharpens a little but swims with every camera move)
            stable = true
            lispsm = false
            constantBias = 0.003f
            normalBias = 0.8f
            screenSpaceContactShadows = shadowMapSize >= 2048
            stepCount = 8
            maxShadowDistance = 0.4f
            penumbraScale = 1.4f
            penumbraRatioScale = 1.2f
        }
        LightManager.Builder(LightManager.Type.SUN)
            .color(sunCol.r, sunCol.g, sunCol.b)
            .intensity(theme.sunIntensity)
            .direction(sunDir[0], sunDir[1], sunDir[2])
            .sunAngularRadius(1.4f).sunHaloSize(6f).sunHaloFalloff(40f)
            .castShadows(shadowMapSize > 0)
            .shadowOptions(so)
            .build(engine, sunEntity)
        scene.addEntity(sunEntity)
    }

    private fun buildSkyDome() {
        val m = MeshData()
        val ws = 48; val hs = 24; val r = 2600f
        for (j in 0..hs) {
            val th = j.toFloat() / hs * PI.toFloat()
            for (i in 0..ws) {
                val ph = i.toFloat() / ws * PI.toFloat() * 2
                val x = sin(th) * sin(ph); val y = cos(th); val z = sin(th) * cos(ph)
                val c = skyColor(x, y, z, true)
                // inward facing normals; unlit so they only matter for winding
                m.vertex(x * r, y * r, z * r, -x, -y, -z, Rgba(min(c[0], 8f), min(c[1], 8f), min(c[2], 8f)))
            }
        }
        for (j in 0 until hs) for (i in 0 until ws) { val a = j * (ws + 1) + i; m.quad(a, a + 1, a + ws + 2, a + ws + 1) }
        skyMesh = Gpu.upload(engine, m)
        skyMi = mats.glow.createInstance().also { it.setParameter("color", 1f, 1f, 1f, 1f) }
        skyEntity = Gpu.renderable(engine, skyMesh!!, skyMi!!, castShadows = false, receiveShadows = false, priority = 7, culling = false)
        engine.renderableManager.setFogEnabled(engine.renderableManager.getInstance(skyEntity), false)
        scene.addEntity(skyEntity)
        // starry night skies
        if (theme.night || theme.ground == null) addStars()
    }

    private fun addStars() {
        val m = MeshData(); val rnd = kotlin.random.Random(theme.shape.seed)
        repeat(700) {
            var x: Float; var y: Float; var z: Float
            do { x = rnd.nextFloat() * 2 - 1; y = rnd.nextFloat(); z = rnd.nextFloat() * 2 - 1 } while (x * x + y * y + z * z > 1f || y < 0.05f)
            val l = sqrt(x * x + y * y + z * z); val r = 2400f
            val s = 2f + rnd.nextFloat() * 4f
            val b = 2f + rnd.nextFloat() * 6f
            Prims.box(m, s, s, s, Rgba(b, b, b * 1.1f), Mat.trs(x / l * r, y / l * r, z / l * r, rnd.nextFloat(), rnd.nextFloat(), 0f))
        }
        val gm = Gpu.upload(engine, m)
        val mi = mats.glow.createInstance().also { it.setParameter("color", 1f, 1f, 1f, 1f) }
        val e = Gpu.renderable(engine, gm, mi, castShadows = false, receiveShadows = false, priority = 7, culling = false)
        engine.renderableManager.setFogEnabled(engine.renderableManager.getInstance(e), false)
        scene.addEntity(e); starEntity = e
    }
    var starEntity = 0

    /** Keep the sky centred on the camera so it never gets closer. */
    private val followMtx = Mat.identity()
    fun follow(cx: Float, cy: Float, cz: Float) {
        val t = followMtx; t[12] = cx; t[13] = cy; t[14] = cz
        val tm = engine.transformManager
        if (skyEntity != 0) tm.setTransform(tm.getInstance(skyEntity), t)
        if (starEntity != 0) tm.setTransform(tm.getInstance(starEntity), t)
    }

    @android.annotation.SuppressLint("HalfFloat") // raw half-float bits are what the RGBA16F texture wants
    private fun buildIbl() {
        // HDR panorama of the sky (row 0 = straight up)
        val w = 256; val h = 128
        val buf = ByteBuffer.allocateDirect(w * h * 4 * 2).order(ByteOrder.nativeOrder())
        val sb = buf.asShortBuffer()
        for (j in 0 until h) {
            val th = (j + 0.5f) / h * PI.toFloat()
            for (i in 0 until w) {
                val ph = (i + 0.5f) / w * PI.toFloat() * 2
                val x = sin(th) * cos(ph); val y = cos(th); val z = sin(th) * sin(ph)
                val c = skyColor(x, y, z, true)
                sb.put(Half.toHalf(c[0])); sb.put(Half.toHalf(c[1])); sb.put(Half.toHalf(c[2])); sb.put(Half.toHalf(1f))
            }
        }
        // The reflection prefilter generates and samples the full panorama mip chain.
        val equirect = Texture.Builder().width(w).height(h).levels(0xff)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
            .format(Texture.InternalFormat.RGBA16F).build(engine)
        equirect.setImage(engine, 0, Texture.PixelBufferDescriptor(buf, Texture.Format.RGBA, Texture.Type.HALF))
        val ctx = IBLPrefilterContext(engine)
        val toCube = IBLPrefilterContext.EquirectangularToCubemap(ctx)
        val cube = toCube.run(equirect)
        val spec = IBLPrefilterContext.SpecularFilter(ctx)
        reflections = spec.run(cube)
        spec.destroy(); toCube.destroy(); ctx.destroy()
        engine.destroyTexture(equirect); engine.destroyTexture(cube)

        ibl = IndirectLight.Builder()
            .reflections(reflections!!)
            .irradiance(3, fitIrradianceSH())
            .intensity(theme.ambient)
            .build(engine)
        scene.indirectLight = ibl
    }

    /**
     * Fit Filament's 9 irradiance coefficients by least squares to the cosine-convolved sky (divided by π),
     * using the exact basis the shader evaluates. Sun is left out: the directional light already adds it.
     */
    private fun fitIrradianceSH(): FloatArray {
        val dirs = ArrayList<FloatArray>()
        val nTh = 24; val nPh = 48
        for (j in 0 until nTh) for (i in 0 until nPh) {
            val th = (j + 0.5f) / nTh * PI.toFloat(); val ph = (i + 0.5f) / nPh * PI.toFloat() * 2
            dirs.add(floatArrayOf(sin(th) * cos(ph), cos(th), sin(th) * sin(ph), sin(th) * (PI.toFloat() / nTh) * (2 * PI.toFloat() / nPh)))
        }
        val skyc = dirs.map { skyColor(it[0], it[1], it[2], false) }
        fun basis(x: Float, y: Float, z: Float) = floatArrayOf(1f, y, z, x, y * x, y * z, 3 * z * z - 1, z * x, x * x - y * y)
        // sample normals
        val normals = ArrayList<FloatArray>()
        for (j in 0 until 12) for (i in 0 until 24) {
            val th = (j + 0.5f) / 12 * PI.toFloat(); val ph = (i + 0.5f) / 24 * PI.toFloat() * 2
            normals.add(floatArrayOf(sin(th) * cos(ph), cos(th), sin(th) * sin(ph)))
        }
        val ata = Array(9) { DoubleArray(9) }
        val atb = Array(3) { DoubleArray(9) }
        for (nv in normals) {
            val e = DoubleArray(3)
            for ((k, d) in dirs.withIndex()) {
                val c = nv[0] * d[0] + nv[1] * d[1] + nv[2] * d[2]
                if (c <= 0f) continue
                val wgt = c * d[3]
                e[0] += skyc[k][0] * wgt; e[1] += skyc[k][1] * wgt; e[2] += skyc[k][2] * wgt
            }
            for (ch in 0..2) e[ch] /= PI
            val b = basis(nv[0], nv[1], nv[2])
            for (r in 0 until 9) { for (c in 0 until 9) ata[r][c] += (b[r] * b[c]).toDouble(); for (ch in 0..2) atb[ch][r] += b[r] * e[ch] }
        }
        val out = FloatArray(27)
        for (ch in 0..2) {
            val x = solve(ata.map { it.copyOf() }.toTypedArray(), atb[ch].copyOf())
            for (r in 0 until 9) out[r * 3 + ch] = x[r].toFloat()
        }
        return out
    }

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        for (c in 0 until n) {
            var p = c; for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[p][c])) p = r
            val t = a[c]; a[c] = a[p]; a[p] = t; val tb = b[c]; b[c] = b[p]; b[p] = tb
            val d = a[c][c].takeIf { abs(it) > 1e-12 } ?: 1e-12
            for (r in 0 until n) if (r != c) { val f = a[r][c] / d; if (f != 0.0) { for (k in c until n) a[r][k] -= f * a[c][k]; b[r] -= f * b[c] } }
        }
        return DoubleArray(n) { b[it] / a[it][it] }
    }

    fun applyFog(view: View, enabled: Boolean) {
        val fc = Rgba.hex(theme.fogColor)
        view.fogOptions = View.FogOptions().apply {
            this.enabled = enabled
            distance = theme.fogDistance
            density = theme.fogDensity
            maximumOpacity = 0.85f
            height = 0f
            heightFalloff = 0.012f
            color = floatArrayOf(fc.r, fc.g, fc.b)
            inScatteringStart = 0f
            inScatteringSize = 40f
            fogColorFromIbl = false
        }
    }

    fun destroy() {
        scene.remove(sunEntity); engine.lightManager.destroy(sunEntity)
        if (skyEntity != 0) { scene.remove(skyEntity); engine.destroyEntity(skyEntity) }
        if (starEntity != 0) { scene.remove(starEntity); engine.destroyEntity(starEntity) }
        skyMesh?.destroy(engine); skyMi?.let { engine.destroyMaterialInstance(it) }
        ibl?.let { scene.indirectLight = null; engine.destroyIndirectLight(it) }
        reflections?.let { engine.destroyTexture(it) }
    }
}
