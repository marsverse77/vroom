package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.VehicleParams

object Rocket : CarDesign {
    override val id = "rocket"
    override val name = "Rocket"
    override val tagline = "Long-tail prototype"
    override val params = VehicleParams(
        mass = 0.95f, topSpeed = 56f, boostSpeed = 86f, accel = 15f, grip = 1.9f,
        frontAxle = 1.4f, rearAxle = 1.35f, halfWidth = 1.3f, halfLength = 2.5f,
    )

    override fun build() = SupercarBody.build(params, RaceLivery.Comet,
        wheelRadius = 0.48f, noseWidth = 0.43f, belt = 0.82f, roofRise = 0.51f,
        cockpitZ = 0.12f, cockpitLength = 0.55f, cockpitWidth = 0.47f)
}
