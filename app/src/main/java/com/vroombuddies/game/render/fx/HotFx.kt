package com.vroombuddies.game.render

import com.vroombuddies.game.content.Theme
import kotlin.random.Random

/**
 * Ambient air effects for track batch A (Hot & dry), spawned around the camera each frame:
 * - Volcano Run: glowing embers drifting up, and soft grey ash falling.
 * - Desert Canyon / Sunset Highway: little sandy dust puffs rolling across the ground.
 * - Dino Valley: floating golden pollen.
 * Counts are small and go through the effects-quality [Particles.scale], so phones stay smooth.
 */
object HotFx {
    fun update(theme: Theme, dt: Float, eye: FloatArray, look: FloatArray, add: Particles, alpha: Particles, rnd: Random) {
        // a point ~25 m ahead of the camera, so new particles appear in view
        var fx = look[0] - eye[0]; var fz = look[2] - eye[2]
        val l = kotlin.math.sqrt(fx * fx + fz * fz).coerceAtLeast(1e-3f); fx /= l; fz /= l
        val cx = eye[0] + fx * 25f; val cz = eye[2] + fz * 25f
        fun r(k: Float) = (rnd.nextFloat() - 0.5f) * k
        when (theme.name) {
            "Volcano Run" -> {
                if (rnd.nextFloat() < dt * 40f) add.spawn(cx + r(70f), eye[1] - 3f + r(4f), cz + r(70f), r(2f), 2.5f + rnd.nextFloat() * 3f, r(2f),
                    2.5f + rnd.nextFloat() * 1.5f, 0.22f, 0.05f, 3.2f, 0.9f, 0.15f, 1f, -0.4f, 0.2f)
                if (rnd.nextFloat() < dt * 14f) alpha.spawn(cx + r(60f), eye[1] + 8f + rnd.nextFloat() * 6f, cz + r(60f), r(1.5f), -1.2f, r(1.5f),
                    5f, 0.25f, 0.2f, 0.35f, 0.32f, 0.3f, 0.6f, 0f, 0.3f)
            }
            "Desert Canyon", "Sunset Highway" -> {
                if (rnd.nextFloat() < dt * 6f) {
                    val warm = theme.name == "Sunset Highway"
                    alpha.spawn(cx + r(60f), eye[1] - 2.5f, cz + r(60f), 3f + rnd.nextFloat() * 2f, 0.4f, r(2f),
                        2.5f, 1.2f, 4f, 0.92f, if (warm) 0.68f else 0.78f, if (warm) 0.5f else 0.58f, 0.35f, 0f, 0.4f)
                }
            }
            "Dino Valley" -> {
                if (rnd.nextFloat() < dt * 12f) add.spawn(cx + r(50f), eye[1] - 1f + r(5f), cz + r(50f), r(0.8f), 0.3f, r(0.8f),
                    4f, 0.12f, 0.12f, 1.4f, 1.2f, 0.4f, 0.7f, 0f, 0.1f)
            }
        }
    }
}
