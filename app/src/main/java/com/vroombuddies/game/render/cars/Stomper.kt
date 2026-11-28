package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.VehicleParams

object Stomper : CarDesign {
    override val id = "stomper"
    override val name = "Stomper"
    override val tagline = "Wide-body rally GT"
    override val params = VehicleParams(
        mass = 1.1f, topSpeed = 50f, boostSpeed = 78f, accel = 15f, grip = 2.15f,
        frontAxle = 1.4f, rearAxle = 1.4f, halfWidth = 1.45f, halfLength = 2.3f,
    )

    override fun build() = SupercarBody.build(params, RaceLivery.Summit,
        wheelRadius = 0.65f, noseWidth = 0.81f, belt = 1.1f, roofRise = 0.78f,
        cockpitZ = -0.1f, cockpitLength = 0.55f, cockpitWidth = 0.6f)
}
