package com.vroombuddies.game.sim

import kotlin.math.*

/** Kind of knock-over object (tyres, cones, crates...). Rendering looks it up by [id]. */
class KnockKind(val id: String, val radius: Float, val height: Float, val mass: Float, val stackMin: Int, val stackMax: Int, val row: Int = 1)

/**
 * One loose object. Lives in world space so it can fly anywhere, bounce off the road and walls,
 * tumble and roll. Sleeps until something hits it, so hundreds cost almost nothing.
 */
class PropBody(val kind: KnockKind, var x: Float, var y: Float, var z: Float, var sHint: Float, val stackId: Int, val tint: Int) {
    var vx = 0f; var vy = 0f; var vz = 0f
    // orientation quaternion (x, y, z, w) and angular velocity (world space, rad/s)
    var qx = 0f; var qy = 0f; var qz = 0f; var qw = 1f
    var wx = 0f; var wy = 0f; var wz = 0f
    var asleep = true
    var onGround = true
    var calm = 0f
    var gone = false
    val homeX = x; val homeY = y; val homeZ = z
    fun reset() { x = homeX; y = homeY; z = homeZ; vx = 0f; vy = 0f; vz = 0f; qx = 0f; qy = 0f; qz = 0f; qw = 1f; wx = 0f; wy = 0f; wz = 0f; asleep = true; gone = false }
}

class PropWorld(val track: TrackPath, val hasTerrain: Boolean, val gravity: Float) {
    val bodies = ArrayList<PropBody>()
    private val loc = FloatArray(2)
    private val pt = FloatArray(3)
    private val fr = Frame()

    fun build(kinds: List<KnockKind>, rng: Rng) {
        var stack = 0
        for (spot in track.knockSpots) {
            val kind = kinds[rng.int(kinds.size)]
            val count = kind.stackMin + rng.int(kind.stackMax - kind.stackMin + 1)
            track.frame(spot.s, fr)
            val rows = kind.row
            for (r in 0 until rows) {
                // a row of single items along the road (cones), or one tall stack (tyres)
                val s = spot.s + (r - (rows - 1) / 2f) * kind.radius * 2.6f
                track.pointAt(s, spot.x, pt)
                val ground = track.groundHeight(s)
                for (k in 0 until count) {
                    bodies.add(PropBody(kind, pt[0], ground + kind.height * (k + 0.5f), pt[2], s, stack, rng.int(1000)))
                }
            }
            // tyre pyramid: a second smaller stack beside the first
            if (kind.id == "tires" && count >= 4) {
                track.pointAt(spot.s + kind.radius * 2.1f, spot.x, pt)
                val ground = track.groundHeight(spot.s)
                for (k in 0 until count - 2) bodies.add(PropBody(kind, pt[0], ground + kind.height * (k + 0.5f), pt[2], spot.s, stack, rng.int(1000)))
            }
            stack++
        }
    }

    fun resetAll() = bodies.forEach { it.reset() }

    private fun wakeStack(id: Int) { for (b in bodies) if (b.stackId == id && b.asleep) { b.asleep = false; b.calm = 0f } }

    private fun groundAt(b: PropBody): Float {
        track.locate(b.x, b.z, b.sHint, loc, 12)
        b.sHint = loc[0]
        val ax = abs(loc[1])
        val road = track.groundHeight(loc[0])
        if (ax <= track.halfWidth + 1.2f) return road
        if (!hasTerrain) return -1e4f
        // embankment slopes down from the road to the ground at y = 0
        return max(0f, road - (ax - track.halfWidth - 1.2f) * 0.8f)
    }

    /** Car hits: the car loses a little speed, the prop flies off with spin. */
    fun hitByCars(cars: List<Vehicle>, events: MutableList<GameEvent>) {
        for (c in cars) {
            val cs = track.wrap(c.progress)
            track.pointAt(cs, c.x, pt)
            val f = track.frame(cs, fr)
            val ch = cos(c.heading); val sh = sin(c.heading)
            // car world forward and velocity
            val fx = f.tx * ch + f.rx * sh; val fz = f.tz * ch + f.rz * sh
            val rxw = -fz; val rzw = fx
            val cvx = fx * c.vf + rxw * c.vl; val cvz = fz * c.vf + rzw * c.vl
            for (b in bodies) {
                if (b.gone) continue
                if (abs(track.delta(cs, b.sHint)) > 12f) continue
                if (b.y > c.y + 2.2f || b.y + b.kind.height < c.y - 0.5f) continue
                // closest point on the car's centre segment
                val dx = b.x - pt[0]; val dz = b.z - pt[2]
                val along = (dx * fx + dz * fz).coerceIn(-(c.params.halfLength - c.params.halfWidth), c.params.halfLength - c.params.halfWidth)
                val qx = dx - fx * along; val qz = dz - fz * along
                val dist = hypot(qx, qz)
                val minD = c.params.halfWidth + b.kind.radius
                if (dist >= minD || dist < 1e-4f) continue
                val nx = qx / dist; val nz = qz / dist
                // push out
                b.x += nx * (minD - dist); b.z += nz * (minD - dist)
                val relN = (cvx - b.vx) * nx + (cvz - b.vz) * nz
                if (relN <= 0f) continue
                val mc = c.params.mass * 4f // a car is far heavier than a tyre
                val mp = b.kind.mass
                val e = 0.55f
                val jp = (1 + e) * relN * mc / (mc + mp)
                b.vx += nx * jp + cvx * 0.35f
                b.vz += nz * jp + cvz * 0.35f
                b.vy += 3f + relN * 0.25f + (b.y - c.y) * 0.8f
                // tumble: spin around the axis perpendicular to the hit and up
                b.wx += nz * relN * 0.6f; b.wz -= nx * relN * 0.6f; b.wy += (b.tint % 7 - 3) * 0.8f
                if (b.asleep) wakeStack(b.stackId)
                b.asleep = false; b.onGround = false
                // car slows a little (momentum goes into the prop)
                val jc = jp * mp / mc
                c.vf -= (nx * fx + nz * fz) * jc * 0.6f
                c.vl -= (nx * rxw + nz * rzw) * jc * 0.6f
                events.add(GameEvent(EventType.PROP_HIT, c, relN, cs, c.x, b.y))
            }
        }
    }

    fun step(dt: Float) {
        for (b in bodies) {
            if (b.asleep || b.gone) continue
            b.vy -= gravity * dt
            b.x += b.vx * dt; b.y += b.vy * dt; b.z += b.vz * dt
            // integrate orientation
            val hx = b.wx * dt * 0.5f; val hy = b.wy * dt * 0.5f; val hz = b.wz * dt * 0.5f
            val qx = b.qx + (hx * b.qw + hy * b.qz - hz * b.qy)
            val qy = b.qy + (hy * b.qw + hz * b.qx - hx * b.qz)
            val qz = b.qz + (hz * b.qw + hx * b.qy - hy * b.qx)
            val qw = b.qw - (hx * b.qx + hy * b.qy + hz * b.qz)
            val inv = 1f / sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
            b.qx = qx * inv; b.qy = qy * inv; b.qz = qz * inv; b.qw = qw * inv

            val g = groundAt(b)
            if (g < -1e3f && b.y < -300f) { b.gone = true; continue }
            val half = b.kind.height * 0.5f
            if (b.y - half < g) {
                b.y = g + half
                if (b.vy < 0f) b.vy = if (b.vy < -2.5f) -b.vy * 0.4f else 0f
                // ground friction and rolling resistance
                val fr = exp(-3.5f * dt)
                b.vx *= fr; b.vz *= fr
                b.wx *= exp(-2.5f * dt); b.wy *= exp(-2.5f * dt); b.wz *= exp(-2.5f * dt)
                b.onGround = true
            } else b.onGround = false
            // walls: bounce back if low enough to hit them
            val ax = abs(loc[1])
            if (ax > track.halfWidth - b.kind.radius && ax < track.halfWidth + 1.2f && b.y - half < g + 1.4f) {
                track.frame(loc[0], fr)
                val side = sign(loc[1])
                val vn = (b.vx * fr.rx + b.vz * fr.rz) * side
                if (vn > 0f && b.y - half < track.groundHeight(loc[0]) + 1.3f) {
                    b.vx -= fr.rx * side * vn * 1.5f; b.vz -= fr.rz * side * vn * 1.5f
                    val push = ax - (track.halfWidth - b.kind.radius)
                    if (b.y - half < track.groundHeight(loc[0]) + 1.3f) { b.x -= fr.rx * side * push; b.z -= fr.rz * side * push }
                }
            }
            val sp = abs(b.vx) + abs(b.vy) + abs(b.vz)
            if (b.onGround && sp < 0.4f) { b.calm += dt; if (b.calm > 1.2f) { b.asleep = true; b.vx = 0f; b.vy = 0f; b.vz = 0f; b.wx = 0f; b.wy = 0f; b.wz = 0f } } else b.calm = 0f
        }
        // simple prop-prop contacts (only near awake bodies)
        val n = bodies.size
        for (i in 0 until n) {
            val a = bodies[i]; if (a.asleep || a.gone) continue
            for (k in 0 until n) {
                if (k == i) continue
                val b = bodies[k]; if (b.gone) continue
                val dx = b.x - a.x; val dz = b.z - a.z
                if (abs(dx) > 3f || abs(dz) > 3f) continue
                val dy = b.y - a.y
                val minR = a.kind.radius + b.kind.radius
                val hd = hypot(dx, dz)
                val minH = (a.kind.height + b.kind.height) * 0.5f
                if (hd >= minR * 0.95f || abs(dy) >= minH) continue
                if (hd < minR * 0.6f) {
                    // stacked: rest on top of each other
                    if (dy < 0f && a.vy < b.vy) { a.y = b.y + minH; a.vy = b.vy * 0.5f; a.vx = a.vx * 0.98f + b.vx * 0.02f }
                } else {
                    val nx = dx / hd; val nz = dz / hd
                    val pen = minR * 0.95f - hd
                    a.x -= nx * pen * 0.5f; a.z -= nz * pen * 0.5f
                    if (!b.asleep) { b.x += nx * pen * 0.5f; b.z += nz * pen * 0.5f }
                    val rel = (a.vx - b.vx) * nx + (a.vz - b.vz) * nz
                    if (rel > 0f) {
                        a.vx -= nx * rel * 0.8f; a.vz -= nz * rel * 0.8f
                        b.vx += nx * rel * 0.8f; b.vz += nz * rel * 0.8f
                        if (b.asleep && rel > 1.5f) { b.asleep = false }
                    }
                }
            }
        }
    }
}
