package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.VehicleParams

/** Scoot: a tiny, tall city car with a white roof and a suitcase on top. Nimble and grippy, gentle top speed. */
object Scoot : CarDesign {
    override val id = "scoot"
    override val name = "Scoot"
    override val tagline = "Small but mighty!"
    override val params = VehicleParams(
        mass = 0.92f, topSpeed = 49f, boostSpeed = 78f, accel = 17.5f, grip = 2.2f,
        frontAxle = 1.18f, rearAxle = 1.18f, halfWidth = 1.05f, halfLength = 1.75f,
    )

    override fun build(): CarParts {
        val c = CarParts()
        CarKit.wheels(c, 0.42f, 0.36f, 0.9f, 1.05f, -1.05f)
        // tall rounded box body
        Prims.roundBox(c.paint, 1.9f, 1.5f, 3.1f, 0.42f, Rgba.WHITE, Mat.trs(0f, 1.27f, 0f))
        CarKit.fenders(c, c.paint, Rgba.WHITE, gap = 0.07f)
        // white roof cap
        Prims.roundBox(c.trim, 1.94f, 0.2f, 2.7f, 0.1f, CarKit.WHITE, Mat.trs(0f, 2.05f, -0.1f), 4)
        // windows: wrap-around band and a big windscreen
        Prims.roundBox(c.glass, 1.94f, 0.55f, 2.3f, 0.16f, CarKit.GLASS, Mat.trs(0f, 1.67f, -0.25f))
        Prims.roundBox(c.glass, 1.5f, 0.55f, 0.08f, 0.04f, CarKit.GLASS_LIGHT, Mat.trs(0f, 1.67f, 1.53f), 3)
        // suitcase on a roof rack
        for (s in floatArrayOf(-0.6f, 0.6f)) Prims.box(c.trim, 0.08f, 0.08f, 1.4f, CarKit.DARK, Mat.trs(s, 2.19f, -0.3f))
        Prims.roundBox(c.trim, 1.0f, 0.38f, 0.8f, 0.08f, Rgba.hex(0xc8874a), Mat.trs(0f, 2.42f, -0.35f), 3)
        Prims.box(c.trim, 1.02f, 0.4f, 0.1f, Rgba.hex(0x7a4a24), Mat.trs(0f, 2.42f, -0.35f))
        Prims.torus(c.trim, 0.14f, 0.03f, CarKit.DARK, Mat.trs(0f, 2.63f, -0.35f, ry = CarKit.HALF_PI), 4, 10, CarKit.PI_F)
        // bumpers
        Prims.roundBox(c.trim, 2.0f, 0.26f, 0.3f, 0.12f, CarKit.DARK, Mat.trs(0f, 0.5f, 1.56f), 4)
        Prims.roundBox(c.trim, 2.0f, 0.26f, 0.3f, 0.12f, CarKit.DARK, Mat.trs(0f, 0.5f, -1.56f), 4)
        CarKit.face(c, eyeZ = 1.53f, eyeY = 1.06f, eyeX = 0.48f, eyeR = 0.29f,
            mouthZ = 1.56f, mouthY = 0.7f, mouthW = 0.2f, cheeks = true)
        CarKit.tailLights(c, 0.62f, 1.05f, -1.56f, 0.32f, 0.3f)
        CarKit.exhaust(c, 0.5f, 0.45f, -1.62f, 0.11f)
        return c
    }
}
