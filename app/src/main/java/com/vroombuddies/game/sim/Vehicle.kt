package com.vroombuddies.game.sim

import kotlin.math.*

/**
 * Tuning for one car. Units are metres, seconds and "mass units" (1 = a normal car).
 * The handling model is a real single-track (bicycle) tyre model with slip angles and a grip limit,
 * so cars slide, bump and spin believably. A steering assistant turns the child's simple
 * left / right input into steering-wheel angles, so the car is always easy to drive.
 */
data class VehicleParams(
    val mass: Float = 1f,
    val topSpeed: Float = 52f,       // m/s at full throttle
    val boostSpeed: Float = 80f,
    val accel: Float = 16f,          // m/s² from standstill
    val boostAccel: Float = 30f,
    val frontAxle: Float = 1.3f,     // centre of mass to front axle
    val rearAxle: Float = 1.3f,
    val halfWidth: Float = 1.15f,
    val halfLength: Float = 2.2f,
    val grip: Float = 2.0f,          // tyre friction coefficient (arcade high grip)
    val corneringStiffness: Float = 9f, // lateral force per load per radian of slip
    val laneSwing: Float = 0.34f,    // how far (radians) a full left/right press turns the car off the road direction
)

class Vehicle(var params: VehicleParams) {
    // --- track-space state ---
    var progress = 0f       // total distance driven along the loop, counts laps (can start negative behind the line)
    var x = 0f              // sideways offset from the road centre, + is right
    var y = 0f              // world height of the car's base
    var vy = 0f
    var heading = 0f        // angle between the car and the road direction, + is pointing right
    var vf = 0f             // forward speed in the car's frame
    var vl = 0f             // sideways (slide) speed in the car's frame, + is right
    var yawRate = 0f        // + is turning right
    var steerAngle = 0f     // front wheel angle (visual + physics)
    var onGround = true
    var airTime = 0f
    var lastAirTime = 0f
    var boostTimer = 0f
    var energy = 30f        // boost button charge 0..100
    var lastPad = -1
    var wrongWayTimer = 0f
    var speedScale = 1f     // AI rubber band / difficulty
    var throttle = 1f
    var finished = false
    var finishTime = 0f
    var resetFlash = 0f
    // visual-only body motion (spring-damped) driven by real accelerations
    var bodyPitch = 0f; var bodyRoll = 0f; var bodyBounce = 0f
    internal var pitchVel = 0f; internal var rollVel = 0f; internal var bounceVel = 0f
    var wheelSpin = 0f
    var latAccel = 0f; var lonAccel = 0f

    val speed get() = hypot(vf, vl)
    val boosting get() = boostTimer > 0f

    fun s(track: TrackPath) = track.wrap(progress)

    fun placeAt(track: TrackPath, progress: Float, x: Float) {
        this.progress = progress; this.x = x
        y = track.groundHeight(progress); vy = 0f
        heading = 0f; vf = 0f; vl = 0f; yawRate = 0f; onGround = true
    }
}

enum class EventType { WALL_HIT, CAR_HIT, PROP_HIT, LAUNCH, LAND, PAD, RESET, LAP, FINISH, BOOST }

class GameEvent(val type: EventType, val car: Vehicle?, val strength: Float, val s: Float, val x: Float, val y: Float)

object VehiclePhysics {
    const val TYRE_G = 20f          // load used for tyre forces (cartoon downforce keeps cars planted)

    /**
     * Advance one car by [dt] seconds.
     * [steer] is the child's input (-1 left .. +1 right). Gravity is per theme (space tracks float).
     */
    fun step(v: Vehicle, steer: Float, dt: Float, track: TrackPath, gravity: Float, events: MutableList<GameEvent>) {
        val p = v.params
        val fr = track.frame(v.progress, tmp)
        val kappa = fr.kappa
        val wheelBase = p.frontAxle + p.rearAxle
        val vfSafe = max(abs(v.vf), 4f)

        // ---- drive force ----
        val top = (if (v.boosting) p.boostSpeed else p.topSpeed) * v.speedScale
        val acc = if (v.boosting) p.boostAccel else p.accel
        var fx = if (v.vf < top) acc * v.throttle * (1f - (v.vf / top).let { it * it * 0.7f }) else -(v.vf - top) * 0.9f
        fx -= 0.0025f * v.vf * abs(v.vf) // air drag
        if (!v.onGround) fx = 0f

        // ---- steering assistant: aim for a heading a little left/right of the road, and follow its curve ----
        val ds = (v.vf * cos(v.heading) - v.vl * sin(v.heading)) / max(0.3f, 1f - kappa * v.x)
        var targetHeading = steer * p.laneSwing
        // soft guard rails: drifting toward a wall without pressing that way steers gently back
        val edge = track.halfWidth - 4f
        if (v.x > edge && steer < 0.5f) targetHeading -= (v.x - edge) * 0.07f
        if (v.x < -edge && steer > -0.5f) targetHeading += (-edge - v.x) * 0.07f
        val wantYaw = (targetHeading - v.heading) * 2.6f + kappa * ds
        var wantSteer = atan2(wheelBase * wantYaw, vfSafe) + (wantYaw - v.yawRate) * 0.06f
        // counter-steer a little when the rear slides, like a real driver would
        wantSteer += atan2(v.vl, vfSafe) * 0.5f
        wantSteer = wantSteer.coerceIn(-0.55f, 0.55f)
        val maxRate = 4f * dt
        v.steerAngle += (wantSteer - v.steerAngle).coerceIn(-maxRate, maxRate)
        val d = v.steerAngle

        // ---- tyre forces (single-track model) ----
        var fyF = 0f; var fyR = 0f
        if (v.onGround) {
            val loadF = p.mass * TYRE_G * p.rearAxle / wheelBase
            val loadR = p.mass * TYRE_G * p.frontAxle / wheelBase
            val dir = if (v.vf >= 0) 1f else -1f
            val slipF = atan2(v.vl + p.frontAxle * v.yawRate, vfSafe) - d * dir
            val slipR = atan2(v.vl - p.rearAxle * v.yawRate, vfSafe)
            fyF = (-p.corneringStiffness * slipF).coerceIn(-p.grip, p.grip) * loadF
            fyR = (-p.corneringStiffness * slipR).coerceIn(-p.grip, p.grip) * loadR
            // rolling resistance on the slide so sideways motion settles
            fyR -= v.vl * 0.4f * p.mass
        }
        val inertia = p.mass * p.frontAxle * p.rearAxle
        val cosD = cos(d); val sinD = sin(d)
        val dvf = (fx * p.mass - fyF * sinD) / p.mass + v.vl * v.yawRate
        val dvl = (fyR + fyF * cosD) / p.mass - v.vf * v.yawRate
        val dw = if (v.onGround) (p.frontAxle * fyF * cosD - p.rearAxle * fyR) / inertia else -v.yawRate * 1.5f
        v.lonAccel = dvf - v.vl * v.yawRate
        v.latAccel = dvl + v.vf * v.yawRate
        v.vf += dvf * dt
        v.vl += dvl * dt
        v.yawRate += dw * dt
        if (v.onGround && abs(v.vf) < 1f && abs(v.vl) < 1f) v.yawRate *= 0.9f

        // ---- move in track space ----
        val vt = v.vf * cos(v.heading) - v.vl * sin(v.heading)
        val vn = v.vf * sin(v.heading) + v.vl * cos(v.heading)
        val sDot = vt / max(0.3f, 1f - kappa * v.x)
        val oldS = v.progress
        v.progress += sDot * dt
        v.x += vn * dt
        v.heading += (v.yawRate - kappa * sDot) * dt
        v.wheelSpin += v.vf * dt / 0.45f

        // ---- height: road, ramps, jumps ----
        val oldGround = track.groundHeight(oldS)
        val newGround = track.groundHeight(v.progress)
        if (v.onGround) {
            val leftRamp = track.rampHeight(oldS) > 0.8f && track.rampHeight(v.progress) <= 0.01f
            if (leftRamp && sDot > 5f) {
                val j = track.jumpAt(oldS)
                val slope = (j?.height ?: 3f) / (j?.length ?: 22f)
                v.vy = sDot * slope * 1.25f + 6f + (if (v.boosting) 4f else 0f)
                v.onGround = false
                v.airTime = 0f
                events.add(GameEvent(EventType.LAUNCH, v, v.vy, v.progress, v.x, v.y))
            } else {
                v.vy = (newGround - oldGround) / dt
                v.y = newGround
            }
        }
        if (!v.onGround) {
            v.vy -= gravity * dt
            v.y += v.vy * dt
            v.airTime += dt
            if (v.y <= newGround) {
                val impact = -v.vy
                v.y = newGround; v.onGround = true
                v.lastAirTime = v.airTime
                v.bounceVel -= impact * 0.06f
                v.vy = 0f
                if (v.airTime > 0.25f) events.add(GameEvent(EventType.LAND, v, impact, v.progress, v.x, v.y))
            }
        }

        // ---- boost bookkeeping ----
        if (v.boostTimer > 0f) {
            v.boostTimer -= dt
            if (v.boostTimer < 0f) v.boostTimer = 0f
        }

        // ---- spring-damped body motion from real accelerations (for visuals and feel) ----
        val wantPitch = (-v.lonAccel * 0.006f).coerceIn(-0.08f, 0.08f)
        val wantRoll = (v.latAccel * 0.006f).coerceIn(-0.1f, 0.1f)
        v.pitchVel += ((wantPitch - v.bodyPitch) * 120f - v.pitchVel * 14f) * dt
        v.rollVel += ((wantRoll - v.bodyRoll) * 120f - v.rollVel * 14f) * dt
        v.bounceVel += ((0f - v.bodyBounce) * 200f - v.bounceVel * 12f) * dt
        v.bodyPitch += v.pitchVel * dt
        v.bodyRoll += v.rollVel * dt
        v.bodyBounce += v.bounceVel * dt
        if (!v.onGround) v.bodyPitch += ((-atan2(v.vy, max(5f, sDot)) * 0.4f) - v.bodyPitch) * min(1f, dt * 3f)
    }

    private val tmp = Frame()

    /** Keep the car inside the walls with a real impulse (bounce, scrape and spin). */
    fun collideWalls(v: Vehicle, track: TrackPath, events: MutableList<GameEvent>) {
        val p = v.params
        val ch = cos(v.heading); val sh = sin(v.heading)
        // corner positions in (t, n) track coordinates relative to the car centre
        for (side in intArrayOf(1, -1)) {
            val limit = track.halfWidth - 0.3f
            var worstPen = 0f; var pt = 0f; var pn = 0f
            for (cl in floatArrayOf(p.halfLength, -p.halfLength)) for (cw in floatArrayOf(p.halfWidth, -p.halfWidth)) {
                val ct = cl * ch - cw * sh
                val cn = cl * sh + cw * ch
                val pen = (v.x + cn) * side - limit
                if (pen > worstPen) { worstPen = pen; pt = ct; pn = cn }
            }
            if (worstPen <= 0f) continue
            v.x -= worstPen * side
            // velocity of the contact point
            var vt = v.vf * ch - v.vl * sh
            var vn = v.vf * sh + v.vl * ch
            val cvt = vt - v.yawRate * pn
            val cvn = vn + v.yawRate * pt
            val nN = -side.toFloat()           // wall normal points back to the road
            val vrel = cvn * nN
            if (vrel < 0f) {
                val inertia = p.mass * p.frontAxle * p.rearAxle
                val rxn = pt * nN                  // 2D cross of contact arm with the normal (normal has only an n component)
                val e = 0.3f
                val j = -(1f + e) * vrel / (1f / p.mass + rxn * rxn / inertia)
                vn += j * nN / p.mass
                v.yawRate += rxn * j / inertia
                // scrape friction along the wall
                val jt = (-cvt * 0.15f).coerceIn(-0.3f * j, 0.3f * j)
                vt += jt / p.mass
                v.vf = vt * ch + vn * sh
                v.vl = -vt * sh + vn * ch
                if (j > 3f) events.add(GameEvent(EventType.WALL_HIT, v, j, v.progress, v.x + pn, v.y))
            }
        }
    }

    private val circ = floatArrayOf(-1f, 0f, 1f)

    /** Car-to-car contact using three circles per car and a proper impulse with masses and spin. */
    fun collideCars(a: Vehicle, b: Vehicle, track: TrackPath, events: MutableList<GameEvent>) {
        val ds = track.delta(a.progress, b.progress)
        if (abs(ds) > 8f || abs(b.x - a.x) > 5f || abs(a.y - b.y) > 2.2f) return
        val pa = a.params; val pb = b.params
        val ra = pa.halfWidth * 1.05f; val rb = pb.halfWidth * 1.05f
        val cha = cos(a.heading); val sha = sin(a.heading); val chb = cos(b.heading); val shb = sin(b.heading)
        var best = 0f; var nT = 0f; var nN = 0f; var aT = 0f; var aN = 0f; var bT = 0f; var bN = 0f
        for (i in circ) for (k in circ) {
            val oa = i * (pa.halfLength - ra); val ob = k * (pb.halfLength - rb)
            val at = oa * cha; val an = oa * sha
            val bt = ds + ob * chb; val bn = (b.x - a.x) + ob * shb
            val dt = bt - at; val dn = bn - an
            val dist = hypot(dt, dn)
            val pen = ra + rb - dist
            if (pen > best && dist > 1e-4f) { best = pen; nT = dt / dist; nN = dn / dist; aT = at + nT * ra; aN = an + nN * ra; bT = aT - ds; bN = aN - (b.x - a.x) }
        }
        if (best <= 0f) return
        // separate by mass
        val ima = 1f / pa.mass; val imb = 1f / pb.mass
        val split = ima / (ima + imb)
        a.progress -= nT * best * split; a.x -= nN * best * split
        b.progress += nT * best * (1 - split); b.x += nN * best * (1 - split)
        // velocities in track frame
        var avt = a.vf * cha - a.vl * sha; var avn = a.vf * sha + a.vl * cha
        var bvt = b.vf * chb - b.vl * shb; var bvn = b.vf * shb + b.vl * chb
        val rbT = aT - ds; val rbN = aN - (b.x - a.x)
        val cav_t = avt - a.yawRate * aN; val cav_n = avn + a.yawRate * aT
        val cbv_t = bvt - b.yawRate * rbN; val cbv_n = bvn + b.yawRate * rbT
        val vrel = (cbv_t - cav_t) * nT + (cbv_n - cav_n) * nN
        if (vrel >= 0f) return
        val ia = pa.mass * pa.frontAxle * pa.rearAxle; val ib = pb.mass * pb.frontAxle * pb.rearAxle
        val raXn = aT * nN - aN * nT
        val rbXn = rbT * nN - rbN * nT
        val e = 0.45f
        val j = -(1 + e) * vrel / (ima + imb + raXn * raXn / ia + rbXn * rbXn / ib)
        avt -= j * nT * ima; avn -= j * nN * ima
        bvt += j * nT * imb; bvn += j * nN * imb
        a.yawRate -= raXn * j / ia * 0.6f
        b.yawRate += rbXn * j / ib * 0.6f
        a.vf = avt * cha + avn * sha; a.vl = -avt * sha + avn * cha
        b.vf = bvt * chb + bvn * shb; b.vl = -bvt * shb + bvn * chb
        if (j > 2f) events.add(GameEvent(EventType.CAR_HIT, a, j, a.progress + aT, a.x + aN, a.y))
    }
}
