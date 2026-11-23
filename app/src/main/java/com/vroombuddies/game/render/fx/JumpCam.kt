package com.vroombuddies.game.render.fx

import kotlin.math.*
import kotlin.random.Random

/**
 * Cinematic shots for big jumps. The race picks one when the player's car leaves a ramp; the camera follows it
 * in slow motion with letterbox bars and depth of field until the car lands.
 *
 * Shots (chosen so the same one doesn't repeat):
 *  - FLYBY: a camera planted on the ground past the ramp; the car flies right over it.
 *  - SIDE: tracks alongside at car height with a slight dutch tilt, like a chase helicopter.
 *  - ORBIT: swings around the car while it hangs in the air (longer jumps only).
 *  - CRANE: rises from behind to look down on the car and the landing.
 */
class JumpCam {
    enum class Shot { FLYBY, SIDE, ORBIT, CRANE }

    var shot = Shot.SIDE; private set
    private var last = Shot.CRANE
    private var side = 1f
    private var duration = 1f
    private var elapsed = 0f
    private val anchor = FloatArray(3)
    private val rnd = Random(17)

    /** Field of view (degrees) and camera roll (radians) for this frame; read after [frame]. */
    var fov = 50f; private set
    var roll = 0f; private set
    /** How quickly the camera should move toward the shot (per second). */
    var stiffness = 4f; private set

    /**
     * Start a shot. [air] = expected seconds in the air (sim time), p = car position, d = flat travel direction,
     * r = road right vector, [speed] in m/s, [groundAhead] = road height where it should land.
     */
    fun start(air: Float, px: Float, py: Float, pz: Float, dx: Float, dz: Float, rx: Float, rz: Float, speed: Float, groundAhead: Float) {
        val options = ArrayList<Shot>()
        options += Shot.FLYBY; options += Shot.SIDE; options += Shot.CRANE
        if (air > 1.1f) options += Shot.ORBIT
        options.remove(last)
        shot = options[rnd.nextInt(options.size)]
        last = shot
        side = if (rnd.nextBoolean()) 1f else -1f
        duration = max(0.5f, air); elapsed = 0f
        // FLYBY: plant the camera about two thirds of the way along the flight, a little off the line
        val ahead = speed * air * 0.65f
        anchor[0] = px + dx * ahead + rx * side * 4.5f
        anchor[1] = groundAhead + 0.9f
        anchor[2] = pz + dz * ahead + rz * side * 4.5f
    }

    /** Fills eye/look for this frame. [dt] is sim (slow-motion) time so the move matches the car. */
    fun frame(dt: Float, px: Float, py: Float, pz: Float, dx: Float, dz: Float, rx: Float, rz: Float, eye: FloatArray, look: FloatArray) {
        elapsed += dt
        val u = (elapsed / duration).coerceIn(0f, 1.2f)
        val ease = u * u * (3f - 2f * min(u, 1f))
        look[0] = px; look[1] = py + 1f; look[2] = pz
        when (shot) {
            Shot.FLYBY -> {
                eye[0] = anchor[0]; eye[1] = anchor[1]; eye[2] = anchor[2]
                // zoom in as it approaches, out as it passes
                val dist = sqrt((px - anchor[0]).pow(2) + (pz - anchor[2]).pow(2))
                fov = (34f + dist * 0.9f).coerceIn(34f, 62f)
                roll = 0f; stiffness = 30f
            }
            Shot.SIDE -> {
                val back = 2f - ease * 6f        // slides from slightly ahead to slightly behind
                eye[0] = px + rx * side * 11f + dx * back; eye[2] = pz + rz * side * 11f + dz * back
                eye[1] = py + 1.2f + ease * 1.5f
                fov = 48f; roll = side * 0.12f; stiffness = 6f
            }
            Shot.ORBIT -> {
                val a = (-0.6f + ease * 2.4f) * side
                val ca = cos(a); val sa = sin(a)
                // start in front-side, end behind-side
                val ox = dx * ca + rx * sa; val oz = dz * ca + rz * sa
                eye[0] = px + ox * 9f; eye[2] = pz + oz * 9f; eye[1] = py + 2f + sin(ease * PI.toFloat()) * 2f
                fov = 52f; roll = -side * 0.06f; stiffness = 8f
            }
            Shot.CRANE -> {
                val h = 3f + ease * 11f
                val back = 8f - ease * 4f
                eye[0] = px - dx * back + rx * side * 3f; eye[2] = pz - dz * back + rz * side * 3f; eye[1] = py + h
                look[0] = px + dx * 6f; look[2] = pz + dz * 6f; look[1] = py
                fov = 56f; roll = 0f; stiffness = 5f
            }
        }
    }
}
