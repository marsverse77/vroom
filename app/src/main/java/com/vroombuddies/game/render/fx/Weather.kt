package com.vroombuddies.game.render

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.Scene
import com.vroombuddies.game.content.Theme
import kotlin.random.Random

/**
 * Track batch D weather: gentle falling snow on Snowy Peaks, light snow with icy glints on Frozen Lake,
 * and tumbling fall leaves in Autumn Woods. Particles spawn in a box around and ahead of the camera,
 * so they cost the same anywhere on the track. Owns its own small particle pool so it never steals
 * particles from boost sparks or confetti.
 */
class Weather private constructor(engine: Engine, scene: Scene, mats: MaterialLib, private val kind: Kind, private val quality: Float) {
    enum class Kind { SNOW, LIGHT_SNOW, LEAVES }

    private val soft = Particles(engine, scene, mats.particleAlpha, 420).also { it.scale = quality }
    private val glint = if (kind == Kind.LIGHT_SNOW) Particles(engine, scene, mats.particleAdd, 60).also { it.scale = quality } else null
    private val rnd = Random(53)
    private var debt = 0f; private var glintDebt = 0f; private var time = 0f
    private val fwd = FloatArray(3); private val left = FloatArray(3); private val up = FloatArray(3)

    private val leafColours = arrayOf(
        floatArrayOf(1f, 0.45f, 0.05f), floatArrayOf(0.95f, 0.2f, 0.06f), floatArrayOf(1f, 0.75f, 0.1f), floatArrayOf(0.8f, 0.3f, 0.1f),
    )

    /** [dt] is game time (slows down during jump slow motion), [eye] the camera position. */
    fun update(dt: Float, eye: FloatArray, camera: Camera) {
        time += dt
        camera.getForwardVector(fwd); camera.getLeftVector(left); camera.getUpVector(up)
        // flatten forward so flakes spread out ahead along the ground, not up into the sky
        val fl = kotlin.math.sqrt(fwd[0] * fwd[0] + fwd[2] * fwd[2]).coerceAtLeast(0.01f)
        val fx = fwd[0] / fl; val fz = fwd[2] / fl
        val rate = when (kind) { Kind.SNOW -> 150f; Kind.LIGHT_SNOW -> 70f; Kind.LEAVES -> 45f }
        debt += rate * dt
        while (debt >= 1f) {
            debt -= 1f
            val ahead = -8f + rnd.nextFloat() * 78f
            val side = (rnd.nextFloat() - 0.5f) * (24f + ahead * 0.9f)
            val x = eye[0] + fx * ahead - fz * side
            val z = eye[2] + fz * ahead + fx * side
            val y = eye[1] + 2f + rnd.nextFloat() * 22f
            when (kind) {
                Kind.SNOW, Kind.LIGHT_SNOW -> {
                    val s = 0.18f + rnd.nextFloat() * 0.18f
                    soft.spawn(x, y, z, 0.8f + (rnd.nextFloat() - 0.5f) * 1.2f, -2.6f - rnd.nextFloat() * 1.4f, (rnd.nextFloat() - 0.5f) * 1.2f,
                        3.2f + rnd.nextFloat() * 1.5f, s, s, 1f, 1f, 1f, 0.9f)
                }
                Kind.LEAVES -> {
                    val c = leafColours[rnd.nextInt(leafColours.size)]
                    val s = 0.35f + rnd.nextFloat() * 0.25f
                    val sway = kotlin.math.sin(time * 1.7f + x * 0.05f) * 2.5f
                    soft.spawn(x, y, z, 1.6f + sway, -1.4f - rnd.nextFloat() * 0.8f, (rnd.nextFloat() - 0.5f) * 2f,
                        4.5f + rnd.nextFloat() * 2f, s, s * 0.8f, c[0], c[1], c[2], 1f)
                }
            }
        }
        glint?.let { g ->
            // tiny twinkles hanging in the cold air, like diamond dust
            glintDebt += 18f * dt
            while (glintDebt >= 1f) {
                glintDebt -= 1f
                val ahead = 6f + rnd.nextFloat() * 40f
                val side = (rnd.nextFloat() - 0.5f) * 30f
                g.spawn(eye[0] + fx * ahead - fz * side, eye[1] - 1f + rnd.nextFloat() * 8f, eye[2] + fz * ahead + fx * side,
                    0f, -0.3f, 0f, 0.8f + rnd.nextFloat() * 0.6f, 0.05f, 0.35f, 0.7f, 0.9f, 1.4f, 1f)
            }
            g.update(dt, eye[0], eye[1], eye[2], -left[0], -left[1], -left[2], up[0], up[1], up[2])
        }
        soft.update(dt, eye[0], eye[1], eye[2], -left[0], -left[1], -left[2], up[0], up[1], up[2])
    }

    fun destroy(scene: Scene) {
        soft.destroy(scene); glint?.destroy(scene)
    }

    companion object {
        /** The weather for [theme], or null when it has none. */
        fun forTheme(engine: Engine, scene: Scene, mats: MaterialLib, theme: Theme, quality: Float): Weather? {
            val kind = when (theme.name) {
                "Snowy Peaks" -> Kind.SNOW
                "Frozen Lake" -> Kind.LIGHT_SNOW
                "Autumn Woods" -> Kind.LEAVES
                else -> return null
            }
            return Weather(engine, scene, mats, kind, quality)
        }
    }
}
