package com.vroombuddies.game.render

import android.content.Context
import com.google.android.filament.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class AaMode(val label: String) { OFF("Off"), FXAA("FXAA"), MSAA("MSAA 4x"), TAA("TAA") }
enum class Level(val label: String) { OFF("Off"), LOW("Low"), MEDIUM("Medium"), HIGH("High") }

/**
 * Every graphics option in the settings menu. Saved on the device. Defaults suit a strong tablet
 * (60 fps, MSAA, soft shadows, every effect on); resolution is capped for dense screens and
 * Dynamic Resolution steps in if a scene is ever too heavy, so frame rate wins over pixels.
 */
class GraphicsSettings(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("graphics", Context.MODE_PRIVATE)

    var antiAliasing = AaMode.valueOf(prefs.getString("aa", AaMode.FXAA.name)!!)
    var shadows = Level.valueOf(prefs.getString("shadows", Level.LOW.name)!!)
    var ssao = Level.valueOf(prefs.getString("ssao", Level.OFF.name)!!)
    var textureQuality = Level.valueOf(prefs.getString("tex", Level.HIGH.name)!!)
    var anisotropic = Level.valueOf(prefs.getString("aniso", Level.MEDIUM.name)!!)
    var bloom = prefs.getBoolean("bloom", true)
    var reflections = prefs.getBoolean("ssr", false)
    var motionBlur = prefs.getBoolean("mblur", false)
    var godRays = prefs.getBoolean("godrays", false)
    var depthOfField = prefs.getBoolean("dof", false)
    var effects = Level.valueOf(prefs.getString("fx", Level.MEDIUM.name)!!)
    var foliageWind = prefs.getBoolean("wind", true)
    var dynamicResolution = prefs.getBoolean("dynres", true)
    var fog = prefs.getBoolean("fog", true)
    var targetFps = prefs.getInt("fps", 60)

    fun save() = prefs.edit()
        .putString("aa", antiAliasing.name).putString("shadows", shadows.name).putString("ssao", ssao.name)
        .putString("tex", textureQuality.name).putString("aniso", anisotropic.name)
        .putBoolean("bloom", bloom).putBoolean("ssr", reflections).putBoolean("mblur", motionBlur).putBoolean("godrays", godRays).putBoolean("dof", depthOfField)
        .putString("fx", effects.name).putBoolean("wind", foliageWind).putBoolean("dynres", dynamicResolution).putBoolean("fog", fog)
        .putInt("fps", targetFps).apply()

    val shadowMapSize get() = when (shadows) { Level.OFF -> 0; Level.LOW -> 512; Level.MEDIUM -> 1024; Level.HIGH -> 2048 }
    val shadowCascades get() = when (shadows) { Level.HIGH -> 3; Level.MEDIUM -> 2; else -> 1 }
    val anisotropy get() = when (anisotropic) { Level.OFF -> 1f; Level.LOW -> 2f; Level.MEDIUM -> 4f; Level.HIGH -> 16f }
    val textureScale get() = when (textureQuality) { Level.OFF, Level.LOW -> 0.5f; Level.MEDIUM -> 0.75f; Level.HIGH -> 1f }
    val particleScale get() = when (effects) { Level.OFF -> 0f; Level.LOW -> 0.35f; Level.MEDIUM -> 0.7f; Level.HIGH -> 1f }

    /** Apply view-level options. Called when settings change and when a race starts. */
    fun apply(view: View, renderer: Renderer) {
        view.antiAliasing = if (antiAliasing == AaMode.FXAA) View.AntiAliasing.FXAA else View.AntiAliasing.NONE
        view.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply { enabled = antiAliasing == AaMode.MSAA; sampleCount = 4 }
        view.temporalAntiAliasingOptions = View.TemporalAntiAliasingOptions().apply { enabled = antiAliasing == AaMode.TAA; feedback = 0.12f; filterWidth = 1f }
        view.dithering = View.Dithering.TEMPORAL

        view.isShadowingEnabled = shadows != Level.OFF
        // DPCF gives soft, contact-hardening edges for a fraction of PCSS's cost (PCSS's blocker search is
        // the single most expensive thing a mobile GPU could do here), so High uses it.
        view.shadowType = if (shadows == Level.HIGH) View.ShadowType.DPCF else View.ShadowType.PCF
        view.softShadowOptions = View.SoftShadowOptions().apply { penumbraScale = 1.6f; penumbraRatioScale = 1.3f }

        view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply {
            enabled = ssao != Level.OFF
            radius = 0.9f
            intensity = 1.1f
            power = 1.2f
            resolution = if (ssao == Level.HIGH) 1f else 0.5f
            quality = when (ssao) { Level.HIGH -> View.QualityLevel.HIGH; Level.MEDIUM -> View.QualityLevel.MEDIUM; else -> View.QualityLevel.LOW }
            lowPassFilter = if (ssao == Level.LOW) View.QualityLevel.LOW else View.QualityLevel.MEDIUM
            upsampling = if (ssao == Level.HIGH) View.QualityLevel.HIGH else View.QualityLevel.LOW
            bentNormals = ssao == Level.HIGH
            // screen-space cone tracing: soft contact shadows from the sun (cheap GI-like grounding)
            ssctEnabled = ssao == Level.HIGH
        }

        view.bloomOptions = View.BloomOptions().apply {
            enabled = bloom
            strength = 0.12f
            levels = 6
            resolution = 384
            threshold = true
            highlight = 600f
            quality = View.QualityLevel.MEDIUM
            lensFlare = bloom && effects == Level.HIGH
            starburst = true
            chromaticAberration = 0.004f
            ghostCount = 4
            ghostSpacing = 0.6f
            ghostThreshold = 8f
            haloThickness = 0.08f
            haloRadius = 0.4f
            haloThreshold = 8f
        }
        view.setScreenSpaceReflectionsOptions(View.ScreenSpaceReflectionsOptions().apply { enabled = reflections; thickness = 0.3f; maxDistance = 25f; stride = 2f })
        // Tablets have very dense screens: shading every one of 4 million pixels with MSAA, SSAO and HDR
        // buys little sharpness and costs a lot. Render at most ~2.5 MP and upscale (FSR-style) to the screen.
        // Dynamic resolution then only drops lower when a frame is about to miss its deadline.
        val cap = resolutionCap
        view.dynamicResolutionOptions = View.DynamicResolutionOptions().apply {
            enabled = true
            maxScale = cap
            minScale = if (dynamicResolution) min(cap, 0.6f) else cap
            quality = View.QualityLevel.MEDIUM
            sharpness = 0.6f
        }
        applyFrameRate(renderer)
        view.renderQuality = View.RenderQuality().apply { hdrColorBuffer = if (effects == Level.HIGH) View.QualityLevel.HIGH else View.QualityLevel.MEDIUM }
        Textures.anisotropy = anisotropy
        Textures.quality = textureScale
        resetCached()
    }

    /** Screen size in pixels (set by the activity) and the display's refresh rate. */
    var screenW = 1920; var screenH = 1080
    var displayHz = 60f

    /** Largest render scale that keeps the 3D image within [PIXEL_BUDGET] pixels. */
    val resolutionCap: Float get() {
        val px = screenW.toFloat() * screenH
        return if (px <= 0f) 1f else sqrt(PIXEL_BUDGET / px).coerceIn(0.5f, 1f)
    }

    /** Display refreshes per drawn frame: a 120 Hz screen running the game at 60 fps draws every 2nd refresh. */
    val frameInterval: Int get() = max(1, (displayHz / targetFps + 0.25f).toInt())

    /** Frame pacing for dynamic resolution: aim for the frame rate setting with 15% headroom. */
    fun applyFrameRate(renderer: Renderer) {
        renderer.frameRateOptions = Renderer.FrameRateOptions().apply {
            interval = frameInterval
            headRoomRatio = 0.15f
            scaleRate = 1f / 10f
            history = 15
        }
    }

    private val dof = View.DepthOfFieldOptions().apply { cocScale = 6f; maxApertureDiameter = 0.02f }
    private var dofOn: Boolean? = null

    /** Depth of field: only switched on during cinematic shots, so the race itself stays crisp. */
    fun setDof(view: View, on: Boolean, focus: Float, camera: Camera) {
        val want = on && depthOfField
        if (want != dofOn) { dofOn = want; dof.enabled = want; view.depthOfFieldOptions = dof }
        if (want) camera.setFocusDistance(focus)
    }

    /** Forget cached view state (after [apply] or when a new race starts). */
    fun resetCached() { dofOn = null }

    companion object {
        /** ~2560x1000 or 2000x1250: sharp on a tablet, and leaves the GPU room for every effect. */
        const val PIXEL_BUDGET = 1_600_000f
    }
}
