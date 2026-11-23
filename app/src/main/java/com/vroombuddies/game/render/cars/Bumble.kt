package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.VehicleParams

object Bumble : CarDesign {
    override val id = "bumble"
    override val name = "Bumble"
    override val tagline = "Grand touring fastback"
    override val params = VehicleParams(
        mass = 1.1f, topSpeed = 50f, boostSpeed = 79f, accel = 15.2f, grip = 2.1f,
        frontAxle = 1.4f, rearAxle = 1.4f, halfWidth = 1.15f, halfLength = 2.4f,
    )

    override fun build() = SupercarBody.build(params, RaceLivery.Honey,
        wheelRadius = 0.5f, noseWidth = 0.76f, belt = 1f, roofRise = 0.77f,
        cockpitZ = -0.21f, cockpitLength = 0.58f, cockpitWidth = 0.75f)
}
