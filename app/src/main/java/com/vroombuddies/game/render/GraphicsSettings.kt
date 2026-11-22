package com.vroombuddies.game.render

import android.content.Context
import com.google.android.filament.*

enum class AaMode(val label: String) { OFF("Off"), FXAA("FXAA"), MSAA("MSAA 4x"), TAA("TAA") }
enum class Level(val label: String) { OFF("Off"), LOW("Low"), MEDIUM("Medium"), HIGH("High") }

/**
 * Every graphics option in the settings menu. Saved on the device. Defaults are picked per phone
 * from a quick first-run benchmark-free guess (screen size / RAM) and Dynamic Resolution keeps it smooth.
 */
class GraphicsSettings(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("graphics", Context.MODE_PRIVATE)

    var antiAliasing = AaMode.valueOf(prefs.getString("aa", AaMode.MSAA.name)!!)
    var shadows = Level.valueOf(prefs.getString("shadows", Level.HIGH.name)!!)
    var ssao = Level.valueOf(prefs.getString("ssao", Level.MEDIUM.name)!!)
    var textureQuality = Level.valueOf(prefs.getString("tex", Level.HIGH.name)!!)
    var anisotropic = Level.valueOf(prefs.getString("aniso", Level.HIGH.name)!!)
    var bloom = prefs.getBoolean("bloom", true)
    var reflections = prefs.getBoolean("ssr", false)
    var motionBlur = prefs.getBoolean("mblur", true)
    var depthOfField = prefs.getBoolean("dof", true)
    var effects = Level.valueOf(prefs.getString("fx", Level.HIGH.name)!!)
    var foliageWind = prefs.getBoolean("wind", true)
    var dynamicResolution = prefs.getBoolean("dynres", true)
    var fog = prefs.getBoolean("fog", true)
    var targetFps = prefs.getInt("fps", 60)

    fun save() = prefs.edit()
        .putString("aa", antiAliasing.name).putString("shadows", shadows.name).putString("ssao", ssao.name)
        .putString("tex", textureQuality.name).putString("aniso", anisotropic.name)
        .putBoolean("bloom", bloom).putBoolean("ssr", reflections).putBoolean("mblur", motionBlur).putBoolean("dof", depthOfField)
        .putString("fx", effects.name).putBoolean("wind", foliageWind).putBoolean("dynres", dynamicResolution).putBoolean("fog", fog)
        .putInt("fps", targetFps).apply()

    val shadowMapSize get() = when (shadows) { Level.OFF -> 0; Level.LOW -> 1024; Level.MEDIUM -> 2048; Level.HIGH -> 2048 }
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
        view.shadowType = when (shadows) { Level.HIGH -> View.ShadowType.PCSS; Level.MEDIUM -> View.ShadowType.DPCF; else -> View.ShadowType.PCF }
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
        view.dynamicResolutionOptions = View.DynamicResolutionOptions().apply {
            enabled = dynamicResolution
            minScale = 0.6f; maxScale = 1f
            quality = View.QualityLevel.MEDIUM
            sharpness = 0.6f
        }
        renderer.frameRateOptions = Renderer.FrameRateOptions().apply {
            interval = if (targetFps >= 60) 1 else 2
            headRoomRatio = 0.1f
        }
        view.renderQuality = View.RenderQuality().apply { hdrColorBuffer = View.QualityLevel.HIGH }
        Textures.anisotropy = anisotropy
        Textures.quality = textureScale
    }

    /** Depth of field: only switched on during cinematic shots, so the race itself stays crisp. */
    fun setDof(view: View, on: Boolean, focus: Float, camera: Camera) {
        view.depthOfFieldOptions = View.DepthOfFieldOptions().apply {
            enabled = on && depthOfField
            cocScale = 6f
            maxApertureDiameter = 0.02f
        }
        camera.setFocusDistance(focus)
    }
}
