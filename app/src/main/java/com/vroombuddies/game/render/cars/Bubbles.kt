package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.VehicleParams

object Bubbles : CarDesign {
    override val id = "bubbles"
    override val name = "Bubbles"
    override val tagline = "Bubble-top speedster"
    override val params = VehicleParams(
        mass = 0.95f, topSpeed = 50f, boostSpeed = 79f, accel = 17.5f, grip = 2.15f,
        frontAxle = 1.2f, rearAxle = 1.2f, halfWidth = 1.15f, halfLength = 2.0f,
    )

    override fun build() = SupercarBody.build(params, RaceLivery.Pearl,
        wheelRadius = 0.46f, noseWidth = 0.74f, belt = 0.78f, roofRise = 0.85f,
        cockpitZ = 0.04f, cockpitLength = 0.43f, cockpitWidth = 0.72f)
}
