package com.vroombuddies.game.sim

import kotlin.math.*

class Racer(val vehicle: Vehicle, val isPlayer: Boolean, val carIndex: Int, val paint: Int) {
    var laneTarget = 0f
    var laneTimer = 0f
    var place = 0
    var collectedGates = 0
    var gateLap = -1
    var aiBoostCooldown = 0f
}

enum class RacePhase { COUNTDOWN, RACING, FINISHED }

/**
 * Everything that happens in one race, without any drawing.
 * The render layer reads cars, props and [events]; the UI feeds [steerInput] and [boostPressed].
 */
class Race(
    val track: TrackPath,
    val gravity: Float,
    val laps: Int,
    hasTerrain: Boolean,
    knockKinds: List<KnockKind>,
    playerParams: VehicleParams,
    playerCar: Int,
    playerPaint: Int,
    aiCars: List<Pair<Int, VehicleParams>>,
    aiPaints: List<Int>,
    seed: Int,
) {
    val racers = ArrayList<Racer>()
    val player: Racer
    val props = PropWorld(track, hasTerrain, gravity)
    val events = ArrayList<GameEvent>()
    var phase = RacePhase.COUNTDOWN
    var countdown = 3.6f
    var raceTime = 0f
    /** Slow motion factor; the camera sets this during jump cinematics. */
    var timeScale = 1f
    var steerInput = 0f
    var boostPressed = false
    var finishOrder = ArrayList<Racer>()
    private val rng = Rng(seed)

    init {
        // grid: 2 per row, player starts in the middle of the pack so there's someone to overtake
        val all = ArrayList<Racer>()
        aiCars.forEachIndexed { i, (car, prm) -> all.add(Racer(Vehicle(prm), false, car, aiPaints[i % aiPaints.size])) }
        player = Racer(Vehicle(playerParams), true, playerCar, playerPaint)
        all.add(min(3, all.size), player)
        all.forEachIndexed { i, r ->
            val row = i / 2; val col = i % 2
            r.vehicle.placeAt(track, -8f - row * 9f, if (col == 0) -4.5f else 4.5f)
            r.laneTarget = r.vehicle.x
        }
        racers.addAll(all)
        props.build(knockKinds, rng)
    }

    val playerVehicle get() = player.vehicle

    fun update(realDt: Float) {
        events.clear()
        var remaining = realDt
        if (phase == RacePhase.COUNTDOWN) {
            val waiting = min(countdown, remaining)
            countdown -= waiting
            remaining -= waiting
            if (countdown > 0f) {
                updatePlaces()
                return
            }
            phase = RacePhase.RACING
        }
        // Respect missed frames without replaying a whole system suspension on the UI thread.
        remaining = min(remaining, 0.5f)
        while (remaining > 0f) {
            val slice = min(remaining, 0.05f)
            advance(slice * timeScale)
            remaining -= slice
        }
    }

    private fun advance(dt: Float) {
        raceTime += dt
        // fixed sub-steps for stable tyre and collision maths
        val steps = max(1, ceil(dt / (1f / 240f)).toInt())
        val h = dt / steps
        for (r in racers) driveInput(r, dt)
        repeat(steps) {
            for (r in racers) {
                val v = r.vehicle
                val steer = if (r.isPlayer && !v.finished) steerInput else aiSteer(r)
                VehiclePhysics.step(v, steer, h, track, gravity, events)
                VehiclePhysics.collideWalls(v, track, events)
            }
            for (i in racers.indices) for (k in i + 1 until racers.size) VehiclePhysics.collideCars(racers[i].vehicle, racers[k].vehicle, track, events)
        }
        props.hitByCars(racers.map { it.vehicle }, events)
        props.step(dt)
        for (r in racers) postStep(r, dt)
        updatePlaces()
    }

    private fun driveInput(r: Racer, dt: Float, racing: Boolean) {
        val v = r.vehicle
        if (!racing) return
        // boost button / AI boost
        if (r.isPlayer) {
            v.energy = min(100f, v.energy + 5f * dt)
            if (boostPressed && v.energy >= 25f && v.boostTimer < 0.2f) {
                v.boostTimer = min(3f, v.energy / 33f)
                v.energy = 0f
                events.add(GameEvent(EventType.BOOST, v, 1f, v.progress, v.x, v.y))
            }
            boostPressed = false
            v.throttle = if (v.finished) 0.35f else 1f
            v.speedScale = 1f
        } else {
            // rubber band: keep the pack close to the child so racing stays exciting, but let them win
            val gap = v.progress - playerVehicle.progress
            v.speedScale = when {
                v.finished -> 0.6f
                gap > 40f -> 0.8f
                gap > 10f -> 0.88f
                gap < -150f -> 1.12f
                gap < -60f -> 1.04f
                else -> 0.95f
            } * (0.95f + (r.carIndex % 3) * 0.015f)
            v.throttle = 1f
            r.aiBoostCooldown -= dt
            if (r.aiBoostCooldown <= 0f && gap < -40f && !v.boosting) { v.boostTimer = 1.6f; r.aiBoostCooldown = 9f + rng.f() * 6f }
        }
    }

    private fun aiSteer(r: Racer): Float {
        val v = r.vehicle
        r.laneTimer -= 1f / 60f
        if (r.laneTimer <= 0f) {
            r.laneTimer = 2f + rng.f() * 3f
            // aim for a boost pad if one is coming up, otherwise wander between lanes
            val s = track.wrap(v.progress)
            val ahead = v.progress > playerVehicle.progress
            val pad = if (ahead) null else track.pads.firstOrNull { val d = track.delta(s, it.s); d in 20f..120f }
            r.laneTarget = pad?.x ?: ((rng.f() * 2 - 1) * (track.halfWidth - 4f))
        }
        // dodge the car right ahead
        for (o in racers) {
            if (o === r) continue
            val d = track.delta(v.progress, o.vehicle.progress)
            if (d in 0f..14f && abs(o.vehicle.x - v.x) < 3f) { r.laneTarget = if (o.vehicle.x > 0) o.vehicle.x - 5f else o.vehicle.x + 5f; break }
        }
        r.laneTarget = r.laneTarget.coerceIn(-(track.halfWidth - 3f), track.halfWidth - 3f)
        return ((r.laneTarget - v.x) / 5f - v.heading * 1.5f).coerceIn(-1f, 1f)
    }

    private fun postStep(r: Racer, dt: Float) {
        val v = r.vehicle
        val s = track.wrap(v.progress)
        if (r.isPlayer && phase == RacePhase.RACING && v.progress >= 0f && !v.finished) {
            val lap = (v.progress / track.length).toInt()
            if (lap != r.gateLap) {
                r.collectedGates = 0
                r.gateLap = lap
            }
            for ((i, gate) in track.chargeGates.withIndex()) {
                val bit = 1 shl i
                if ((r.collectedGates and bit) != 0 || abs(track.delta(gate.s, s)) > gate.halfLength) continue
                if (abs(v.x - gate.x) > gate.halfWidth || !v.onGround) continue
                r.collectedGates = r.collectedGates or bit
                v.energy = min(100f, v.energy + 25f)
                v.boostTimer = max(v.boostTimer, 1.8f)
                events.add(GameEvent(EventType.BOOST, v, 2f, s, v.x, v.y))
            }
        }
        // boost pads
        for ((i, pad) in track.pads.withIndex()) {
            if (v.onGround && abs(track.delta(pad.s, s)) < pad.halfLength && abs(v.x - pad.x) < pad.halfWidth + 0.6f) {
                if (v.lastPad != i) {
                    v.lastPad = i
                    v.boostTimer = max(v.boostTimer, 1.3f)
                    if (r.isPlayer) v.energy = min(100f, v.energy + 12f)
                    events.add(GameEvent(EventType.PAD, v, 1f, s, v.x, v.y))
                }
            }
        }
        if (v.lastPad >= 0 && abs(track.delta(track.pads[v.lastPad].s, s)) > 20f) v.lastPad = -1
        // big jump landing charges the boost button
        if (r.isPlayer) for (e in events) if (e.type == EventType.LAND && e.car === v && v.lastAirTime > 0.6f) v.energy = min(100f, v.energy + 10f)

        // wrong way / stuck: put the car back facing the right way
        val backwards = cos(v.heading) < 0.15f
        val stuck = v.onGround && v.vf < 3f && phase == RacePhase.RACING && !v.finished
        if (backwards || stuck) v.wrongWayTimer += dt else v.wrongWayTimer = max(0f, v.wrongWayTimer - dt)
        if (v.wrongWayTimer > (if (backwards) 0.7f else 2.5f)) {
            v.wrongWayTimer = 0f
            v.heading = 0f; v.yawRate = 0f; v.vl = 0f; v.vf = 24f
            v.x = v.x.coerceIn(-(track.halfWidth - 4f), track.halfWidth - 4f)
            v.y = track.groundHeight(v.progress); v.vy = 0f; v.onGround = true
            v.resetFlash = 1f
            events.add(GameEvent(EventType.RESET, v, 1f, s, v.x, v.y))
        }
        if (v.resetFlash > 0f) v.resetFlash = max(0f, v.resetFlash - dt)

        // laps and finish
        if (!v.finished && v.progress >= laps * track.length) {
            v.finished = true; v.finishTime = raceTime
            finishOrder.add(r)
            events.add(GameEvent(EventType.FINISH, v, finishOrder.size.toFloat(), s, v.x, v.y))
            if (r.isPlayer) phase = RacePhase.FINISHED
        }
    }

    private val order = ArrayList<Racer>()
    private fun updatePlaces() {
        order.clear(); order.addAll(racers)
        order.sortWith { a, b ->
            val fa = finishOrder.indexOf(a); val fb = finishOrder.indexOf(b)
            when {
                fa >= 0 && fb >= 0 -> fa - fb
                fa >= 0 -> -1
                fb >= 0 -> 1
                else -> b.vehicle.progress.compareTo(a.vehicle.progress)
            }
        }
        order.forEachIndexed { i, r -> r.place = i + 1 }
    }

    val lap get() = (floor(playerVehicle.progress / track.length).toInt() + 1).coerceIn(1, laps)
}
