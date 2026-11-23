package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.render.cars.CarKit.HALF_PI
import com.vroombuddies.game.render.cars.CarKit.PI_F
import com.vroombuddies.game.sim.VehicleParams

/** Hopper: an open dune buggy with a roll cage, knobbly tyres, a spare wheel and a flag. Loves jumps. */
object Hopper : CarDesign {
    override val id = "hopper"
    override val name = "Hopper"
    override val tagline = "Loves big jumps!"
    override val params = VehicleParams(
        mass = 0.95f, topSpeed = 52f, boostSpeed = 81f, accel = 16.8f, grip = 2.05f,
        frontAxle = 1.35f, rearAxle = 1.3f, halfWidth = 1.3f, halfLength = 2.1f,
    )

    private val CAGE = Rgba.hex(0x3a3f4b)

    override fun build(): CarParts {
        val c = CarParts()
        CarKit.wheels(c, 0.62f, 0.56f, 1.15f, 1.4f, -1.35f, rim = Rgba.hex(0xffd23f))
        // knobbly tread blocks so the tyres look off-road (part of the spinning wheel mesh)
        for (k in 0 until 12) {
            val a = k * PI_F * 2 / 12
            Prims.box(c.wheel, 0.5f, 0.1f, 0.16f, CarKit.TIRE, Mat.trs(0f, kotlin.math.cos(a) * 0.62f, kotlin.math.sin(a) * 0.62f, rx = a))
        }
        // tub and a sloping hood
        Prims.roundBox(c.paint, 1.8f, 0.6f, 3.5f, 0.26f, Rgba.WHITE, Mat.trs(0f, 0.98f, -0.1f))
        Prims.roundBox(c.paint, 1.7f, 0.42f, 1.1f, 0.2f, Rgba.WHITE, Mat.trs(0f, 1.18f, 1.45f, rx = 0.18f))
        // seat
        Prims.roundBox(c.trim, 0.9f, 0.3f, 0.8f, 0.12f, Rgba.hex(0x333a48), Mat.trs(0f, 1.32f, -0.45f), 3)
        Prims.roundBox(c.trim, 0.9f, 0.8f, 0.25f, 0.12f, Rgba.hex(0x333a48), Mat.trs(0f, 1.7f, -0.85f, rx = -0.15f), 3)
        // steering wheel
        Prims.torus(c.trim, 0.2f, 0.04f, CarKit.DARK, Mat.trs(0f, 1.55f, 0.35f, rx = -0.6f), 4, 14)
        // roll cage: two hoops and a spine
        Prims.torus(c.trim, 0.85f, 0.07f, CAGE, Mat.trs(0f, 1.28f, -1.0f), 6, 18, PI_F)
        Prims.torus(c.trim, 0.78f, 0.07f, CAGE, Mat.trs(0f, 1.28f, 0.45f, rx = 0.25f), 6, 18, PI_F)
        Prims.cylinder(c.trim, 0.07f, 0.07f, 1.5f, CAGE, Mat.trs(0f, 2.1f, -0.28f, rx = HALF_PI - 0.03f), 8)
        // whip antenna with a flag
        Prims.cylinder(c.trim, 0.025f, 0.03f, 1.8f, CarKit.DARK, Mat.trs(-0.72f, 2.05f, -1.55f), 6)
        Prims.box(c.trim, 0.04f, 0.38f, 0.55f, Rgba.hex(0xff7a1a), Mat.trs(-0.72f, 2.75f, -1.85f))
        // spare tyre on the back
        Prims.torus(c.trim, 0.32f, 0.14f, CarKit.TIRE, Mat.trs(0f, 1.32f, -1.98f), 8, 18)
        Prims.cylinder(c.trim, 0.2f, 0.2f, 0.12f, Rgba.hex(0xffd23f), Mat.trs(0f, 1.32f, -1.98f, rx = HALF_PI), 12)
        // bright skid plate with the smile on it
        Prims.roundBox(c.trim, 1.6f, 0.42f, 0.3f, 0.13f, CarKit.CHROME, Mat.trs(0f, 0.78f, 1.92f), 4)
        CarKit.face(c, eyeZ = 2.0f, eyeY = 1.24f, eyeX = 0.46f, eyeR = 0.27f,
            mouthZ = 2.08f, mouthY = 0.66f, mouthW = 0.2f, brows = true, tongue = true)
        CarKit.tailLights(c, 0.65f, 1.0f, -1.86f, 0.26f, 0.2f)
        CarKit.exhaust(c, 0.6f, 0.75f, -1.9f, 0.13f)
        CarKit.exhaust(c, -0.6f, 0.75f, -1.9f, 0.13f)
        return c
    }
}
