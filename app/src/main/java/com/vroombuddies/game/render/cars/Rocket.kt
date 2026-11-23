package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.render.cars.CarKit.HALF_PI
import com.vroombuddies.game.sim.VehicleParams

/** Rocket: a long, low space-racer with a bubble cockpit, a tail fin and two rocket pods. Fastest top speed, a bit less grip. */
object Rocket : CarDesign {
    override val id = "rocket"
    override val name = "Rocket"
    override val tagline = "Blast off!"
    override val params = VehicleParams(
        mass = 0.95f, topSpeed = 56f, boostSpeed = 86f, accel = 15f, grip = 1.9f,
        frontAxle = 1.4f, rearAxle = 1.35f, halfWidth = 1.3f, halfLength = 2.5f,
    )

    override fun build(): CarParts {
        val c = CarParts()
        CarKit.wheels(c, 0.48f, 0.45f, 1.12f, 1.6f, -1.55f)
        // long low body with a pointy, sloping nose
        Prims.roundBox(c.paint, 2.0f, 0.56f, 4.4f, 0.26f, Rgba.WHITE, Mat.trs(0f, 0.72f, -0.2f))
        CarKit.ellipsoid(c.paint, 1.0f, 0.3f, 0.9f, Rgba.WHITE, Mat.trs(0f, 0.72f, 2.0f))
        CarKit.fenders(c, c.paint, Rgba.WHITE, gap = 0.08f)
        // bubble cockpit
        CarKit.ellipsoid(c.glass, 0.62f, 0.6f, 1.0f, CarKit.GLASS, Mat.trs(0f, 0.95f, -0.35f), 24, 8, HALF_PI)
        // tail fin
        Prims.roundBox(c.paint, 0.1f, 0.75f, 0.7f, 0.05f, Rgba.WHITE, Mat.trs(0f, 1.3f, -2.0f, rx = -0.45f), 3)
        Prims.sphere(c.lights, 0.09f, Rgba(1f, 0.3f, 0.2f), Mat.trs(0f, 1.66f, -2.3f), 8, 6)
        // rocket pods on the shoulders, with nose cones and flame pipes
        for (s in floatArrayOf(-1f, 1f)) {
            Prims.cylinder(c.trim, 0.22f, 0.22f, 1.6f, CarKit.WHITE, Mat.trs(s * 0.82f, 1.18f, -0.85f, rx = HALF_PI), 14)
            Prims.cone(c.trim, 0.22f, 0.45f, Rgba.hex(0xff4f4f), Mat.trs(s * 0.82f, 1.18f, 0.17f, rx = HALF_PI), 14)
            Prims.torus(c.trim, 0.22f, 0.04f, CarKit.DARK, Mat.trs(s * 0.82f, 1.18f, -0.4f), 4, 14)
            CarKit.exhaust(c, s * 0.82f, 1.18f, -1.75f, 0.17f)
        }
        // front splitter
        Prims.roundBox(c.trim, 2.1f, 0.1f, 0.5f, 0.04f, CarKit.DARK, Mat.trs(0f, 0.42f, 2.55f), 3)
        CarKit.face(c, eyeZ = 2.05f, eyeY = 1.02f, eyeX = 0.48f, eyeR = 0.3f,
            mouthZ = 2.78f, mouthY = 0.55f, mouthW = 0.24f, brows = true)
        CarKit.tailLights(c, 0.6f, 0.78f, -2.42f, 0.4f, 0.16f)
        return c
    }
}
