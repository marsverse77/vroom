package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.VehicleParams

object Scoot : CarDesign {
    override val id = "scoot"
    override val name = "Scoot"
    override val tagline = "Pocket supercar"
    override val params = VehicleParams(
        mass = 0.92f, topSpeed = 49f, boostSpeed = 78f, accel = 17.5f, grip = 2.2f,
        frontAxle = 1.18f, rearAxle = 1.18f, halfWidth = 1.05f, halfLength = 1.75f,
    )

    override fun build() = SupercarBody.build(params, RaceLivery.Courier,
        wheelRadius = 0.44f, noseWidth = 0.72f, belt = 0.87f, roofRise = 0.84f,
        cockpitZ = 0.1f, cockpitLength = 0.45f, cockpitWidth = 0.71f)
}
