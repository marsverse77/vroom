package com.vroombuddies.game.render.fx

import com.google.android.filament.*
import com.vroombuddies.game.render.Gpu
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What the race asks the screen pass to do this frame. All strengths are 0..1. */
class PostFxInputs {
    var blur = 0f
    var rays = 0f
    var boost = 0f
    var sunU = 0.5f; var sunV = 0.8f; var sunVisible = 0f
    var focusU = 0.5f; var focusV = 0.55f
    val rayColor = floatArrayOf(1f, 0.9f, 0.7f)
    val boostColor = floatArrayOf(1f, 0.45f, 0.1f)
    /** Run the pass even with nothing to show (warm-up frames build its shader and buffers ahead of time). */
    var force = false
    val active get() = force || blur > 0.01f || (rays > 0.01f && sunVisible > 0.01f) || boost > 0.01f
}

/**
 * Screen-space effects Filament doesn't have built in: true (radial) motion blur, volumetric-looking god rays
 * and the boost "warp". The race view is drawn into an offscreen image as usual (with all of Filament's own
 * effects: AA, SSAO, bloom, tone mapping), then one full-screen triangle samples it into the screen.
 *
 * When nothing is on (menus, slow cars, effects disabled) the race view goes straight to the screen, so this
 * costs nothing. If the phone couldn't build the shader, [available] is false and it's always skipped.
 */
class PostFx(private val engine: Engine, material: Material?) {
    private val mi: MaterialInstance? = material?.createInstance()
    val available get() = mi != null
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val camEntity = EntityManager.get().create()
    private val camera = engine.createCamera(camEntity)
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var tri = 0
    private var color: Texture? = null
    private var depth: Texture? = null
    private var target: RenderTarget? = null
    private var width = 0; private var height = 0
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)

    init {
        view.scene = scene; view.camera = camera
        view.isPostProcessingEnabled = false
        view.isShadowingEnabled = false
        view.blendMode = View.BlendMode.OPAQUE
        view.dithering = View.Dithering.NONE
        camera.setExposure(1f)
        if (mi != null) {
            // one triangle that covers the whole screen, already in clip space
            val v = VertexBuffer.Builder().vertexCount(3).bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.UV0, 1, VertexBuffer.AttributeType.FLOAT2, 0, 8)
                .build(engine)
            v.setBufferAt(engine, 0, Gpu.floats(9).put(floatArrayOf(-1f, -1f, 0f, 3f, -1f, 0f, -1f, 3f, 0f)).flip())
            v.setBufferAt(engine, 1, Gpu.floats(6).put(floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f)).flip())
            val i = IndexBuffer.Builder().indexCount(3).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
            val ibuf = ByteBuffer.allocateDirect(6).order(ByteOrder.nativeOrder())
            ibuf.asShortBuffer().put(shortArrayOf(0, 1, 2))
            i.setBuffer(engine, ibuf)
            vb = v; ib = i
            tri = EntityManager.get().create()
            RenderableManager.Builder(1)
                .boundingBox(Box(0f, 0f, 0f, 1e4f, 1e4f, 1e4f))
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, v, i, 0, 3)
                .material(0, mi).culling(false).castShadows(false).receiveShadows(false)
                .build(engine, tri)
            scene.addEntity(tri)
        }
    }

    private var targetWidth = 0
    private var targetHeight = 0

    fun resize(w: Int, h: Int) {
        if (w == width && h == height) return
        width = w; height = h
        view.viewport = Viewport(0, 0, w, h)
    }

    private fun allocateTarget(material: MaterialInstance) {
        val w = width
        val h = height
        val c = Texture.Builder().width(w).height(h).levels(1).sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.RGBA8).usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE).build(engine)
        val d = Texture.Builder().width(w).height(h).levels(1).sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.DEPTH24).usage(Texture.Usage.DEPTH_ATTACHMENT).build(engine)
        val next = RenderTarget.Builder()
            .texture(RenderTarget.AttachmentPoint.COLOR, c)
            .texture(RenderTarget.AttachmentPoint.DEPTH, d)
            .build(engine)
        // The sampler must stop referencing the previous color texture before it is destroyed.
        material.setParameter("scene", c, sampler)
        releaseTarget()
        target = next
        color = c; depth = d
        targetWidth = w; targetHeight = h
    }

    private fun releaseTarget() {
        target?.let { engine.destroyRenderTarget(it) }; target = null
        color?.let { engine.destroyTexture(it) }; color = null
        depth?.let { engine.destroyTexture(it) }; depth = null
    }

    /** Draw [main] to the screen, through the effects pass when [fx] wants anything. Call between beginFrame/endFrame. */
    fun render(renderer: Renderer, main: View, fx: PostFxInputs?) {
        val m = mi
        if (m == null || fx == null || !fx.active || width <= 0 || height <= 0) {
            if (main.renderTarget != null) main.renderTarget = null
            renderer.render(main)
            return
        }
        if (target == null || targetWidth != width || targetHeight != height) {
            main.renderTarget = null
            allocateTarget(m)
        }
        val rt = target
        if (main.renderTarget !== rt) main.renderTarget = rt
        m.setParameter("params", fx.blur, fx.rays, fx.boost, 0f)
        m.setParameter("sun", fx.sunU, fx.sunV, fx.sunVisible, 0f)
        m.setParameter("focus", fx.focusU, fx.focusV)
        m.setParameter("rayColor", fx.rayColor[0], fx.rayColor[1], fx.rayColor[2], 1f)
        m.setParameter("boostColor", fx.boostColor[0], fx.boostColor[1], fx.boostColor[2], 1f)
        renderer.render(main)
        renderer.render(view)
    }

    fun destroy(main: View) {
        main.renderTarget = null
        if (tri != 0) { scene.remove(tri); engine.destroyEntity(tri) }
        vb?.let { engine.destroyVertexBuffer(it) }; ib?.let { engine.destroyIndexBuffer(it) }
        mi?.let { engine.destroyMaterialInstance(it) }
        releaseTarget()
        engine.destroyView(view); engine.destroyScene(scene)
        engine.destroyCameraComponent(camEntity); EntityManager.get().destroy(camEntity)
    }

    companion object {
        private val v = DoubleArray(16); private val p = DoubleArray(16)

        /**
         * Project a world point to normalised screen coordinates (0..1, origin bottom-left).
         * Returns false when it's behind the camera.
         */
        fun project(camera: Camera, x: Double, y: Double, z: Double, out: FloatArray): Boolean {
            camera.getViewMatrix(v); camera.getProjectionMatrix(p)
            // column-major: view space first, then clip space
            val vx = v[0] * x + v[4] * y + v[8] * z + v[12]
            val vy = v[1] * x + v[5] * y + v[9] * z + v[13]
            val vz = v[2] * x + v[6] * y + v[10] * z + v[14]
            val cx = p[0] * vx + p[4] * vy + p[8] * vz + p[12]
            val cy = p[1] * vx + p[5] * vy + p[9] * vz + p[13]
            val cw = p[3] * vx + p[7] * vy + p[11] * vz + p[15]
            if (cw <= 1e-4) return false
            out[0] = (cx / cw * 0.5 + 0.5).toFloat(); out[1] = (cy / cw * 0.5 + 0.5).toFloat()
            return true
        }
    }
}
