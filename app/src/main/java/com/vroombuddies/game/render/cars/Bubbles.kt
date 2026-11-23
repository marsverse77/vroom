package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.render.cars.CarKit.HALF_PI
import com.vroombuddies.game.sim.VehicleParams

/** Bubbles: a round, bouncy egg car with a glass bubble roof and a wobbly antenna. Quick off the line, very grippy. */
object Bubbles : CarDesign {
    override val id = "bubbles"
    override val name = "Bubbles"
    override val tagline = "Round and bouncy!"
    override val params = VehicleParams(
        mass = 0.95f, topSpeed = 50f, boostSpeed = 79f, accel = 17.5f, grip = 2.15f,
        frontAxle = 1.2f, rearAxle = 1.2f, halfWidth = 1.15f, halfLength = 2.0f,
    )

    override fun build(): CarParts {
        val c = CarParts()
        CarKit.wheels(c, 0.45f, 0.36f, 1.02f, 1.2f, -1.2f, RaceLivery.Pearl)
        // egg body and a dark rubber band around its middle
        CarKit.ellipsoid(c.paint, 1.15f, 0.74f, 2.0f, Rgba.WHITE, Mat.trs(0f, 1.05f, 0f))
        Prims.torus(c.trim, 1f, 0.1f, CarKit.DARK, Mat.trs(0f, 0.8f, 0f, rx = HALF_PI, sx = 1.1f, sy = 1.92f), 6, 40)
        CarKit.fenders(c, c.paint, Rgba.WHITE, gap = 0.08f)
        // glass bubble roof with a shiny ring
        CarKit.ellipsoid(c.glass, 0.84f, 0.82f, 1.05f, CarKit.GLASS, Mat.trs(0f, 1.3f, -0.2f), 24, 8, HALF_PI)
        Prims.torus(c.trim, 1f, 0.06f, CarKit.CHROME, Mat.trs(0f, 1.32f, -0.2f, rx = HALF_PI, sx = 0.85f, sy = 1.06f), 5, 32)
        // springy antenna with a ball on top
        Prims.cylinder(c.trim, 0.03f, 0.04f, 0.9f, CarKit.DARK, Mat.trs(0.42f, 2.15f, -0.95f, rx = -0.2f), 6)
        Prims.sphere(c.trim, 0.14f, Rgba.hex(0xffd23f), Mat.trs(0.42f, 2.6f, -1.04f), 10, 8)
        CarKit.face(c, eyeZ = 1.76f, eyeY = 1.2f, eyeX = 0.48f, eyeR = 0.33f,
            mouthZ = 1.9f, mouthY = 0.66f, mouthW = 0.26f, cheeks = true, tongue = true)
        CarKit.tailLights(c, 0.55f, 1.05f, -1.93f, 0.3f, 0.22f)
        CarKit.exhaust(c, 0f, 0.55f, -1.92f, 0.13f)
        RaceLivery.Pearl.apply(c)
        return c
    }
}
