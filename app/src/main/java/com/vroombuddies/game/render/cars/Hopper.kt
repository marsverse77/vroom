package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.VehicleParams

object Hopper : CarDesign {
    override val id = "hopper"
    override val name = "Hopper"
    override val tagline = "Safari roadster"
    override val params = VehicleParams(
        mass = 0.95f, topSpeed = 52f, boostSpeed = 81f, accel = 16.8f, grip = 2.05f,
        frontAxle = 1.35f, rearAxle = 1.3f, halfWidth = 1.3f, halfLength = 2.1f,
    )

    override fun build() = SupercarBody.build(params, RaceLivery.Rally,
        wheelRadius = 0.6f, noseWidth = 0.61f, belt = 1.04f, roofRise = 0.59f,
        cockpitZ = -0.12f, cockpitLength = 0.38f, cockpitWidth = 0.57f)
}
