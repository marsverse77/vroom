package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.render.cars.CarKit.HALF_PI
import com.vroombuddies.game.sim.VehicleParams

/** Stomper: a tall, happy monster truck on giant wheels with a roof light bar. Heavy and steady. */
object Stomper : CarDesign {
    override val id = "stomper"
    override val name = "Stomper"
    override val tagline = "Big wheels, big fun!"
    override val params = VehicleParams(
        mass = 1.1f, topSpeed = 50f, boostSpeed = 78f, accel = 15f, grip = 2.15f,
        frontAxle = 1.4f, rearAxle = 1.4f, halfWidth = 1.45f, halfLength = 2.3f,
    )

    override fun build(): CarParts {
        val c = CarParts()
        CarKit.wheels(c, 0.85f, 0.7f, 1.3f, 1.45f, -1.45f)
        // frame and axles under the body
        Prims.roundBox(c.trim, 1.5f, 0.35f, 3.8f, 0.12f, CarKit.DARK, Mat.trs(0f, 1.05f, 0f), 3)
        for (z in floatArrayOf(1.45f, -1.45f)) Prims.cylinder(c.trim, 0.12f, 0.12f, 2.2f, CarKit.DARK, Mat.trs(0f, 0.85f, z, rz = HALF_PI), 8)
        // springs between the frame and the axles
        for (sx in floatArrayOf(-0.7f, 0.7f)) for (z in floatArrayOf(1.45f, -1.45f))
            for (k in 0 until 3) Prims.torus(c.trim, 0.13f, 0.035f, Rgba.hex(0xffd23f), Mat.trs(sx, 0.98f + k * 0.11f, z, rx = HALF_PI), 4, 10)
        // body: tub, hood and a tall cab
        Prims.roundBox(c.paint, 2.3f, 0.8f, 4.3f, 0.28f, Rgba.WHITE, Mat.trs(0f, 1.65f, 0f))
        Prims.roundBox(c.paint, 2.0f, 0.95f, 1.85f, 0.32f, Rgba.WHITE, Mat.trs(0f, 2.45f, -0.35f))
        // windows: side band and windscreen
        Prims.roundBox(c.glass, 2.04f, 0.5f, 1.45f, 0.18f, CarKit.GLASS, Mat.trs(0f, 2.55f, -0.38f))
        Prims.roundBox(c.glass, 1.66f, 0.52f, 0.1f, 0.05f, CarKit.GLASS_LIGHT, Mat.trs(0f, 2.52f, 0.57f, rx = -0.25f))
        // bed rails at the back
        for (s in floatArrayOf(-1f, 1f)) Prims.roundBox(c.trim, 0.12f, 0.22f, 1.2f, 0.05f, CarKit.CHROME, Mat.trs(s * 1.05f, 2.15f, -1.55f), 3)
        Prims.roundBox(c.trim, 2.2f, 0.22f, 0.12f, 0.05f, CarKit.CHROME, Mat.trs(0f, 2.15f, -2.12f), 3)
        // roof light bar
        Prims.roundBox(c.trim, 1.6f, 0.2f, 0.3f, 0.08f, CarKit.DARK, Mat.trs(0f, 3.0f, 0.15f), 3)
        for (k in -2..2) Prims.sphere(c.lights, 0.1f, Rgba(1f, 0.95f, 0.7f), Mat.trs(k * 0.32f, 3.0f, 0.3f, sz = 0.5f), 8, 6)
        // chunky front bumper bar
        Prims.roundBox(c.trim, 2.2f, 0.26f, 0.3f, 0.12f, CarKit.CHROME, Mat.trs(0f, 1.22f, 2.2f), 4)
        CarKit.face(c, eyeZ = 2.15f, eyeY = 1.82f, eyeX = 0.6f, eyeR = 0.36f,
            mouthZ = 2.16f, mouthY = 1.36f, mouthW = 0.34f, brows = true)
        CarKit.tailLights(c, 0.8f, 1.75f, -2.16f, 0.4f, 0.24f)
        // tall stack exhausts behind the cab
        for (s in floatArrayOf(-1f, 1f)) {
            Prims.cylinder(c.trim, 0.11f, 0.11f, 1.1f, CarKit.CHROME, Mat.trs(s * 0.95f, 2.6f, -1.35f), 10)
            CarKit.exhaust(c, s * 0.95f, 3.1f, -1.45f, 0.13f)
        }
        return c
    }
}
