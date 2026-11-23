package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.VehicleParams

/** Bumble: a chubby little minibus with a white stripe, lots of windows and a roof rack. Big, steady and friendly. */
object Bumble : CarDesign {
    override val id = "bumble"
    override val name = "Bumble"
    override val tagline = "Room for all my friends!"
    override val params = VehicleParams(
        mass = 1.1f, topSpeed = 50f, boostSpeed = 79f, accel = 15.2f, grip = 2.1f,
        frontAxle = 1.4f, rearAxle = 1.4f, halfWidth = 1.15f, halfLength = 2.4f,
    )

    override fun build(): CarParts {
        val c = CarParts()
        CarKit.wheels(c, 0.5f, 0.42f, 1.02f, 1.55f, -1.55f)
        // tall bus body
        Prims.roundBox(c.paint, 2.2f, 1.85f, 4.6f, 0.48f, Rgba.WHITE, Mat.trs(0f, 1.5f, 0f))
        CarKit.fenders(c, c.paint, Rgba.WHITE, gap = 0.08f)
        // white belt stripe and roof cap
        Prims.roundBox(c.trim, 2.24f, 0.2f, 4.5f, 0.1f, CarKit.WHITE, Mat.trs(0f, 1.3f, 0f), 4)
        Prims.roundBox(c.trim, 2.05f, 0.16f, 4.1f, 0.08f, CarKit.WHITE, Mat.trs(0f, 2.44f, -0.05f), 4)
        // windows with pillars, and a big windscreen
        Prims.roundBox(c.glass, 2.24f, 0.62f, 3.5f, 0.18f, CarKit.GLASS, Mat.trs(0f, 1.88f, -0.4f))
        for (z in floatArrayOf(-1.3f, -0.4f, 0.5f)) Prims.box(c.paint, 2.27f, 0.64f, 0.14f, Rgba.WHITE, Mat.trs(0f, 1.88f, z))
        Prims.roundBox(c.glass, 1.8f, 0.65f, 0.08f, 0.04f, CarKit.GLASS_LIGHT, Mat.trs(0f, 1.9f, 2.29f), 3)
        // roof rack with a beach ball
        for (s in floatArrayOf(-0.75f, 0.75f)) Prims.box(c.trim, 0.08f, 0.1f, 2.6f, CarKit.DARK, Mat.trs(s, 2.58f, -0.4f))
        for (z in floatArrayOf(-1.5f, 0.7f)) Prims.box(c.trim, 1.6f, 0.08f, 0.08f, CarKit.DARK, Mat.trs(0f, 2.58f, z))
        Prims.sphere(c.trim, 0.34f, Rgba.hex(0xff4f4f), Mat.trs(0f, 2.92f, -0.9f), 12, 8)
        Prims.torus(c.trim, 0.345f, 0.05f, CarKit.WHITE, Mat.trs(0f, 2.92f, -0.9f), 4, 18)
        // bumpers
        Prims.roundBox(c.trim, 2.3f, 0.28f, 0.34f, 0.12f, CarKit.DARK, Mat.trs(0f, 0.52f, 2.3f), 4)
        Prims.roundBox(c.trim, 2.3f, 0.28f, 0.34f, 0.12f, CarKit.DARK, Mat.trs(0f, 0.52f, -2.3f), 4)
        CarKit.face(c, eyeZ = 2.28f, eyeY = 1.2f, eyeX = 0.62f, eyeR = 0.33f,
            mouthZ = 2.31f, mouthY = 0.76f, mouthW = 0.27f, cheeks = true)
        CarKit.tailLights(c, 0.78f, 1.15f, -2.31f, 0.3f, 0.42f)
        CarKit.exhaust(c, -0.6f, 0.45f, -2.4f, 0.12f)
        return c
    }
}
