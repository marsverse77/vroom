package com.vroombuddies.game.render

import com.vroombuddies.game.content.Theme
import kotlin.math.*
import kotlin.random.Random

/**
 * Track batch B ambient effects, driven from GameScene.ambientFx with one line.
 * - Space Station / Galaxy Grand Prix: twinkly space dust drifting past the camera and shooting stars far away.
 * - Moon Base: an occasional shooting star over the horizon.
 * - Crystal Canyon: glowing spores floating up around the car.
 * All particles are additive and few (well under 100 alive), so they're cheap on phones.
 */
object SpaceFx {
    private var shootTimer = 1.5f

    fun update(theme: Theme, p: Particles, eye: FloatArray, fwd: FloatArray, dt: Float, rnd: Random) {
        when (theme.name) {
            "Space Station", "Galaxy Grand Prix" -> { dust(p, eye, fwd, dt, rnd, 22f); shootingStars(p, eye, dt, rnd, 2.2f) }
            "Moon Base" -> shootingStars(p, eye, dt, rnd, 4.5f)
            "Crystal Canyon" -> spores(p, eye, fwd, dt, rnd)
        }
    }

    private fun chance(rate: Float, dt: Float, rnd: Random): Int {
        val n = rate * dt
        return n.toInt() + if (rnd.nextFloat() < n - n.toInt()) 1 else 0
    }

    /** Tiny sparkles ahead of the camera, so flying through space feels fast. */
    private fun dust(p: Particles, eye: FloatArray, fwd: FloatArray, dt: Float, rnd: Random, rate: Float) {
        repeat(chance(rate, dt, rnd)) {
            val d = 25f + rnd.nextFloat() * 45f
            val x = eye[0] + fwd[0] * d + (rnd.nextFloat() - 0.5f) * 50f
            val y = eye[1] + fwd[1] * d + (rnd.nextFloat() - 0.4f) * 24f
            val z = eye[2] + fwd[2] * d + (rnd.nextFloat() - 0.5f) * 50f
            val hue = rnd.nextInt(3)
            val r = if (hue == 1) 0.6f else 1f; val g = if (hue == 2) 0.7f else 1f; val b = if (hue == 0) 1f else 0.9f
            p.spawn(x, y, z, (rnd.nextFloat() - 0.5f) * 0.6f, (rnd.nextFloat() - 0.5f) * 0.6f, (rnd.nextFloat() - 0.5f) * 0.6f,
                2.5f, 0.25f, 0.05f, r * 2.2f, g * 2.2f, b * 2.4f, 0.9f)
        }
    }

    /** A bright streak that zips across the sky every few seconds. */
    private fun shootingStars(p: Particles, eye: FloatArray, dt: Float, rnd: Random, every: Float) {
        shootTimer -= dt
        if (shootTimer > 0f) return
        shootTimer = every * (0.6f + rnd.nextFloat() * 0.8f)
        val a = rnd.nextFloat() * 6.283f
        val x = eye[0] + cos(a) * 320f; val z = eye[2] + sin(a) * 320f; val y = eye[1] + 90f + rnd.nextFloat() * 80f
        val da = a + 1.6f + (rnd.nextFloat() - 0.5f) * 0.6f
        val sp = 160f
        val vx = cos(da) * sp; val vz = sin(da) * sp; val vy = -25f - rnd.nextFloat() * 20f
        p.spawn(x, y, z, vx, vy, vz, 1.1f, 3f, 1.5f, 3f, 2.6f, 2f, 1f, stretch = 0.12f, force = true)
        repeat(6) { k ->
            p.spawn(x - vx * k * 0.012f, y - vy * k * 0.012f, z - vz * k * 0.012f, vx * 0.05f, vy * 0.05f, vz * 0.05f,
                0.8f + k * 0.05f, 1.6f, 0.2f, 1.2f, 1.6f, 3f, 0.7f, force = true)
        }
    }

    /** Glowing crystal spores that float slowly upwards. */
    private fun spores(p: Particles, eye: FloatArray, fwd: FloatArray, dt: Float, rnd: Random) {
        repeat(chance(16f, dt, rnd)) {
            val d = 10f + rnd.nextFloat() * 40f
            val x = eye[0] + fwd[0] * d + (rnd.nextFloat() - 0.5f) * 60f
            val y = eye[1] - 4f + rnd.nextFloat() * 10f
            val z = eye[2] + fwd[2] * d + (rnd.nextFloat() - 0.5f) * 60f
            val pink = rnd.nextFloat() < 0.5f
            p.spawn(x, y, z, (rnd.nextFloat() - 0.5f) * 1.2f, 1.2f + rnd.nextFloat(), (rnd.nextFloat() - 0.5f) * 1.2f,
                3.5f, 0.35f, 0.15f, if (pink) 2.4f else 0.6f, if (pink) 0.9f else 2.2f, 2.6f, 0.85f, gravity = -0.2f)
        }
    }
}
