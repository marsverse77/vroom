package com.vroombuddies.game.render

import com.google.android.filament.*
import com.vroombuddies.game.content.Knock
import com.vroombuddies.game.content.Theme
import com.vroombuddies.game.render.fx.*
import com.vroombuddies.game.sim.*
import kotlin.math.*
import kotlin.random.Random

/** Sounds the scene asks for; implemented by the audio layer. */
interface SoundSink {
    fun engine(speed: Float, boost: Boolean) {}
    fun tires(slide: Float, onGround: Boolean) {}
    fun play(name: String, strength: Float = 1f) {}
}

/** What the HUD needs each frame. */
class HudState {
    var place = 1; var racers = 6; var lap = 1; var laps = 2
    var energy = 0f; var speedKmh = 0f; var boosting = false
    var countdown = 0f; var phase = RacePhase.COUNTDOWN
    var letterbox = 0f; var flash = 0f; var whoops = 0f
    var progress = FloatArray(0); var playerIndex = 0
    var finishPlace = 0
}

/**
 * One race on screen: builds the world for a theme, runs the simulation, turns physics events into
 * effects, sound and camera moves, and feeds the HUD.
 */
class GameScene(
    private val engine: Engine, private val scene: Scene, private val view: View, private val camera: Camera,
    private val mats: MaterialLib, private val settings: GraphicsSettings, val theme: Theme,
    design: CarDesign, paint: Paint, private val sound: SoundSink, val track: TrackPath, sceneryLayout: SceneryLayout,
) {
    val race: Race
    private val env = Environment(engine, scene, mats, theme)
    private val trackR = TrackRenderer(engine, scene, mats, track, theme)
    private val scenery = Scenery(engine, scene, mats, track, theme)
    private val knock: KnockRenderer
    private val cars = ArrayList<CarInstance>()
    private val add = Particles(engine, scene, mats.particleAdd, 700)
    private val alpha = Particles(engine, scene, mats.particleAlpha, 500)
    private val tropics = TropicsFx.create(engine, scene, mats, track, theme, settings.particleScale)
    private val weather = Weather.forTheme(engine, scene, mats, theme, settings.particleScale)
    private val water = Water(engine, scene, mats, track, theme)
    private val trails: BoostTrails
    private val jumpCam = JumpCam()
    /** Inputs for the full-screen effects pass (motion blur, god rays, boost warp); read by the activity each frame. */
    val post = PostFxInputs()
    private var boostKick = 0f
    private var boostGlow = 0f
    val hud = HudState()
    private val rnd = Random(9)
    private var time = 0f
    /** Warm-up frames behind the loading screen: show boost flames and a few particles so their shaders get built. */
    var warming = false

    init {
        val aiPaints = Paints.all.filter { it.color != paint.color && !it.rainbow }.shuffled(Random(theme.shape.seed))
        val designs = CarDesigns.all
        val aiCars = (0 until 5).map { i -> val d = designs[(i + 1) % designs.size]; i to d.params.copy(grip = d.params.grip * theme.grip / 2f) }
        race = Race(track, theme.gravity, 2, theme.ground != null, Knock.kinds(theme.knock),
            design.params.copy(grip = design.params.grip * theme.grip / 2f), 0, 0, aiCars, aiPaints.indices.toList(), theme.shape.seed)
        env.build(settings.shadowMapSize, settings.shadowCascades)
        env.applyFog(view, settings.fog)
        trackR.build()
        scenery.build(settings.foliageWind, sceneryLayout)
        knock = KnockRenderer(engine, scene, mats, race.props)
        var ai = 0
        trails = BoostTrails(engine, scene, mats.particleAdd, race.racers.size)
        for ((i, r) in race.racers.withIndex()) {
            val p = if (r.isPlayer) paint else aiPaints[ai % aiPaints.size]
            val inst = if (r.isPlayer) CarInstance(engine, scene, mats, design, paint, true)
            else CarInstance(engine, scene, mats, designs[(ai + 1) % designs.size], aiPaints[ai++ % aiPaints.size], false)
            cars.add(inst)
            // trail colour: the paint, pushed bright so it blooms (rainbow cars get a magic magenta)
            val c = if (p.rainbow) Rgba(1f, 0.2f, 0.9f) else Rgba.hex(p.color)
            val m = max(c.r, max(c.g, c.b)).coerceAtLeast(0.05f)
            trails.setColor(i, c.r / m * 2.2f + 0.3f, c.g / m * 2.2f + 0.3f, c.b / m * 2.2f + 0.3f)
        }
        val sc = Rgba.hex(theme.sunColor)
        post.rayColor[0] = sc.r; post.rayColor[1] = sc.g; post.rayColor[2] = sc.b
        add.scale = settings.particleScale; alpha.scale = settings.particleScale
        applyGrading()
        hud.laps = race.laps; hud.racers = race.racers.size
        hud.playerIndex = race.racers.indexOf(race.player)
        hud.progress = FloatArray(race.racers.size)
    }

    fun applyGrading() {
        val warm = theme.warmth
        val cg = ColorGrading.Builder()
            .toneMapper(ToneMapper.Agx())
            .saturation(theme.saturation)
            .vibrance(1.1f)
            .contrast(theme.contrast)
            .whiteBalance(warm * 0.3f, 0f)
            .build(engine)
        view.colorGrading = cg
        grading?.let { engine.destroyColorGrading(it) }
        grading = cg
    }
    private var grading: ColorGrading? = null

    // ------------------------------------------------------------------ camera
    private enum class CamMode { INTRO, CHASE, CINEMA, ORBIT }
    private var camMode = CamMode.INTRO
    private val eye = FloatArray(3); private val look = FloatArray(3)
    private var fov = 62f
    private var shake = 0f
    private var cineTimer = 0f
    private var orbitAngle = 0f
    private var aspect = 1.8f
    private val fr = Frame()
    private val camE = FloatArray(3); private val camL = FloatArray(3)
    private var camRoll = 0f
    private val proj = FloatArray(2)

    /** Fill [post] for the full-screen pass: blur from speed, god rays from where the sun is on screen, boost warp. */
    private fun updatePost(realDt: Float, v: Vehicle, px: Float, py: Float, pz: Float, dx: Float, dz: Float) {
        val racing = camMode == CamMode.CHASE
        val speedBlur = ((v.speed - 28f) / 40f).coerceIn(0f, 1f)
        val wantBlur = if (settings.motionBlur && racing) speedBlur * 0.6f + (if (v.boosting) 0.4f else 0f) else 0f
        post.blur = lerp(post.blur, wantBlur, 1f - exp(-realDt * 5f))
        boostGlow = lerp(boostGlow, if (v.boosting && racing) 1f else 0f, 1f - exp(-realDt * (if (v.boosting) 10f else 3f)))
        post.boost = if (settings.effects == Level.OFF) 0f else min(1f, boostGlow + boostKick)
        // vanishing point: far ahead along the road
        if (PostFx.project(camera, (px + dx * 200f).toDouble(), (py + 2f).toDouble(), (pz + dz * 200f).toDouble(), proj)) {
            post.focusU = proj[0].coerceIn(0.2f, 0.8f); post.focusV = proj[1].coerceIn(0.3f, 0.8f)
        }
        post.rays = if (settings.godRays) (if (theme.night) 0.4f else 1f) * (theme.sunIntensity / 3.2f).coerceIn(0.4f, 1.3f) else 0f
        val sd = env.sunDir
        val seen = PostFx.project(camera, (eye[0] - sd[0] * 1000f).toDouble(), (eye[1] - sd[1] * 1000f).toDouble(), (eye[2] - sd[2] * 1000f).toDouble(), proj)
        val vis = if (seen) {
            val ox = max(0f, max(-proj[0], proj[0] - 1f)); val oy = max(0f, max(-proj[1], proj[1] - 1f))
            (1f - max(ox, oy) / 0.35f).coerceIn(0f, 1f)
        } else 0f
        if (seen) { post.sunU = proj[0]; post.sunV = proj[1] }
        post.sunVisible = lerp(post.sunVisible, vis, 1f - exp(-realDt * 6f))
    }

    fun setAspect(a: Float) { aspect = a }

    private fun carPose(v: Vehicle, out: FloatArray, fwd: FloatArray) {
        track.frame(v.progress, fr)
        val ca = cos(v.heading); val sa = sin(v.heading)
        var fx = fr.tx * ca + fr.rx * sa; var fz = fr.tz * ca + fr.rz * sa
        var fy = if (v.onGround) fr.slope * ca else 0f
        val l = sqrt(fx * fx + fy * fy + fz * fz); fx /= l; fy /= l; fz /= l
        out[0] = fr.x + fr.rx * v.x; out[1] = v.y; out[2] = fr.z + fr.rz * v.x
        fwd[0] = fx; fwd[1] = fy; fwd[2] = fz
    }

    private val tmpP = FloatArray(3); private val tmpF = FloatArray(3); private val mtx = FloatArray(16)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun updateCamera(realDt: Float) {
        val v = race.playerVehicle
        carPose(v, tmpP, tmpF)
        track.frame(v.progress, fr)
        val px = tmpP[0]; val py = tmpP[1]; val pz = tmpP[2]
        // blend car heading with road direction so slides don't swing the camera wildly
        var dx = tmpF[0] * 0.45f + fr.tx * 0.55f; var dz = tmpF[2] * 0.45f + fr.tz * 0.55f
        val dl = sqrt(dx * dx + dz * dz); dx /= dl; dz /= dl
        val boost = if (v.boosting) 1f else 0f
        val speedK = (v.speed / 60f).coerceIn(0f, 1.4f)
        var ex: Float; var ey: Float; var ez: Float; var lx: Float; var ly: Float; var lz: Float
        var follow = 1f - exp(-realDt * 6f)
        var wantFov = 60f + speedK * 8f + boost * 12f
        when (camMode) {
            CamMode.INTRO -> {
                val t = (1f - race.countdown / 3.6f).coerceIn(0f, 1f)
                val ease = t * t * (3f - 2f * t)
                val a = (1f - ease) * 2.8f
                val r = lerp(5.8f, 6.8f, ease) + 2.4f * sin(PI.toFloat() * ease)
                ex = px + (dx * cos(a) - fr.rx * sin(a)) * -r; ez = pz + (dz * cos(a) - fr.rz * sin(a)) * -r
                ey = py + lerp(1.45f, 2.5f, ease) + 1.8f * sin(PI.toFloat() * ease)
                wantFov = lerp(42f, 60f, ease)
                lx = px; ly = py + 1.2f; lz = pz
                follow = 1f
            }
            CamMode.CHASE -> {
                // close, low chase cam: the car fills more of the screen and speed reads stronger
                val kick = if (settings.effects == Level.OFF) 0f else boostKick
                val dist = 6.8f + boost * 1.2f - speedK * 0.4f + kick * 0.70f
                wantFov += kick * 4f
                ex = px - dx * dist; ez = pz - dz * dist; ey = py + 2.5f - boost * 0.22f
                lx = px + dx * 5f; ly = py + 1.2f; lz = pz + dz * 5f
            }
            CamMode.CINEMA -> {
                jumpCam.frame(realDt * race.timeScale, px, py, pz, dx, dz, fr.rx, fr.rz, camE, camL)
                ex = camE[0]; ey = camE[1]; ez = camE[2]; lx = camL[0]; ly = camL[1]; lz = camL[2]
                follow = 1f - exp(-realDt * jumpCam.stiffness)
                wantFov = jumpCam.fov
            }
            CamMode.ORBIT -> {
                orbitAngle += realDt * 0.45f
                ex = px + cos(orbitAngle) * 9f; ez = pz + sin(orbitAngle) * 9f; ey = py + 2.8f
                lx = px; ly = py + 1f; lz = pz
                follow = 1f - exp(-realDt * 3f)
                wantFov = 50f
            }
        }
        // never dip under the road
        ey = max(ey, track.groundHeight(v.progress) + 1f)
        eye[0] = lerp(eye[0], ex, follow); eye[1] = lerp(eye[1], ey, follow); eye[2] = lerp(eye[2], ez, follow)
        look[0] = lerp(look[0], lx, min(1f, follow * 2f)); look[1] = lerp(look[1], ly, min(1f, follow * 2f)); look[2] = lerp(look[2], lz, min(1f, follow * 2f))
        fov = lerp(fov, wantFov, 1f - exp(-realDt * 4f))
        val wantRoll = if (camMode == CamMode.CINEMA) jumpCam.roll else 0f
        camRoll = lerp(camRoll, wantRoll, 1f - exp(-realDt * 3f))
        shake = max(0f, shake - realDt * 2.5f)
        val sk = shake * shake * 0.5f
        val sx = (rnd.nextFloat() - 0.5f) * sk; val sy = (rnd.nextFloat() - 0.5f) * sk
        // near/far kept as tight as the scene allows (sky dome is at 2.6 km): far more depth precision,
        // so road markings, boost pads and the start line never flicker against the road far away
        camera.setProjection(fov.toDouble(), aspect.toDouble(), NEAR, FAR, Camera.Fov.VERTICAL)
        // up vector tilted by the cinematic roll (dutch angle)
        var vfx = look[0] - eye[0]; var vfz = look[2] - eye[2]
        val vl = sqrt(vfx * vfx + vfz * vfz).coerceAtLeast(1e-4f); vfx /= vl; vfz /= vl
        val upX = -vfz * sin(camRoll); val upY = cos(camRoll); val upZ = vfx * sin(camRoll)
        camera.lookAt((eye[0] + sx).toDouble(), (eye[1] + sy).toDouble(), eye[2].toDouble(), look[0].toDouble(), look[1].toDouble(), look[2].toDouble(), upX.toDouble(), upY.toDouble(), upZ.toDouble())
        env.follow(eye[0], eye[1], eye[2])
        scenery.updateLod(eye[0], eye[2])
        val camDist = sqrt((eye[0] - px).pow(2) + (eye[1] - py).pow(2) + (eye[2] - pz).pow(2))
        settings.setDof(view, camMode == CamMode.CINEMA || camMode == CamMode.ORBIT, camDist, camera)
        hud.letterbox = lerp(hud.letterbox, if (camMode == CamMode.CINEMA) 1f else 0f, 1f - exp(-realDt * 6f))
        updatePost(realDt, v, px, py, pz, dx, dz)
    }

    // ------------------------------------------------------------------ effects
    private fun worldOf(s: Float, x: Float, y: Float, out: FloatArray) { track.frame(s, fr); out[0] = fr.x + fr.rx * x; out[1] = y; out[2] = fr.z + fr.rz * x }
    private val ev = FloatArray(3)

    private fun handleEvents() {
        val pv = race.playerVehicle
        for (ei in race.events.indices) {
            val e = race.events[ei]
            val mine = e.car === pv
            worldOf(e.s, e.x, e.y, ev)
            when (e.type) {
                EventType.WALL_HIT, EventType.CAR_HIT -> {
                    add.burst((10 + e.strength * 2).toInt().coerceAtMost(40), ev[0], ev[1] + 0.6f, ev[2], 10f + e.strength, 4f, 0.5f, 0.12f, 0.05f, 4f, 2.2f, 0.6f, 1f, 18f, 0.04f)
                    if (mine) { shake = max(shake, min(1f, e.strength * 0.08f)); sound.play(if (e.type == EventType.WALL_HIT) "scrape" else "bump", e.strength) }
                }
                EventType.PROP_HIT -> {
                    alpha.burst(6, ev[0], ev[1] + 0.5f, ev[2], 3f, 2f, 0.9f, 1.2f, 3f, 0.75f, 0.7f, 0.65f, 0.6f, -0.5f)
                    if (mine) { shake = max(shake, 0.35f); sound.play("thud", e.strength) }
                }
                EventType.LAUNCH -> if (mine) {
                    sound.play("whoosh")
                    val air = 2f * e.strength / race.gravity
                    if (air > 0.7f && race.phase == RacePhase.RACING) {
                        camMode = CamMode.CINEMA; cineTimer = air
                        track.frame(pv.progress, fr)
                        val land = pv.progress + pv.speed * air
                        jumpCam.start(air, ev[0], ev[1], ev[2], fr.tx, fr.tz, fr.rx, fr.rz, pv.speed, track.groundHeight(land))
                    }
                    // ramp sparks off the lip
                    add.burst(14, ev[0], ev[1] + 0.2f, ev[2], 7f, 3f, 0.45f, 0.1f, 0.04f, 4f, 2.6f, 0.9f, 1f, 16f, 0.04f)
                }
                EventType.LAND -> {
                    alpha.burst(18, ev[0], ev[1] + 0.2f, ev[2], 6f, 1f, 1.0f, 1.0f, 4f, 0.8f, 0.75f, 0.7f, 0.55f, -0.3f)
                    // dust ring rolling out from the landing
                    alpha.ring(if (mine) 28 else 12, ev[0], ev[1] + 0.3f, ev[2], 9f + e.strength * 0.3f, 0.9f, 1.2f, 3.5f, 0.85f, 0.8f, 0.72f, 0.5f, up = 0.6f, gravity = -0.2f)
                    if (mine) {
                        shake = max(shake, min(0.9f, e.strength * 0.05f)); sound.play("land", e.strength)
                        if (camMode == CamMode.CINEMA) camMode = CamMode.CHASE
                    }
                }
                EventType.PAD -> {
                    add.burst(16, ev[0], ev[1] + 0.3f, ev[2], 6f, 4f, 0.5f, 0.6f, 0.1f, 0.3f, 1.6f, 3f, 1f, 0f)
                    add.ring(18, ev[0], ev[1] + 0.25f, ev[2], 14f, 0.35f, 0.5f, 0.2f, 0.4f, 1.8f, 3.5f, 1f, up = 0f, stretch = 0.05f)
                    if (mine) {
                        boostKick = max(boostKick, 0.5f)
                        track.frame(e.s, fr)
                        for (side in intArrayOf(-1, 1)) {
                            val offset = side * (pv.params.halfWidth + 1.3f)
                            add.burst(12, ev[0] + fr.rx * offset, ev[1] + 0.4f, ev[2] + fr.rz * offset,
                                4f, 8f, 0.58f, 0.18f, 0.04f, 0.2f, 2.8f, 3.6f, 1f, 7f, 0.04f)
                        }
                    }
                    if (mine) { sound.play("pad"); hud.flash = max(hud.flash, 0.35f) }
                }
                EventType.BOOST -> {
                    // shockwave: a ring of hot sparks plus a bright flash of the car's boost light
                    add.ring(if (mine) 36 else 16, ev[0], ev[1] + 0.5f, ev[2], 16f, 0.4f, 0.7f, 0.15f, 3.5f, 2f, 0.6f, 1f, up = 1f, stretch = 0.04f)
                    if (mine && e.strength >= 2f) {
                        add.ring(32, ev[0], ev[1] + 0.85f, ev[2], 11f, 0.55f, 0.7f, 0.14f, 0.45f, 2.3f, 2.6f, 1f, up = 3f, stretch = 0.04f)
                    }
                    if (mine) { sound.play("boost"); hud.flash = 0.6f; shake = max(shake, 0.3f); boostKick = 1f }
                }
                EventType.RESET -> {
                    trails.cut(race.racers.indexOfFirst { it.vehicle === e.car }.coerceAtLeast(0))
                    if (mine) { hud.whoops = 1.6f; hud.flash = 0.8f; sound.play("whoops") }
                }
                EventType.FINISH -> if (mine) {
                    hud.finishPlace = e.strength.toInt()
                    camMode = CamMode.ORBIT
                    sound.play(if (hud.finishPlace == 1) "win" else "finish")
                    celebrate(hud.finishPlace == 1)
                }
                EventType.LAP -> {}
            }
        }
    }

    private var confettiTimer = 0f
    private var fireworkTimer = 0f
    private fun celebrate(win: Boolean) { confettiTimer = if (win) 7f else 3f; fireworkTimer = if (win) 7f else 0f }

    private var exhaustCredit = 0f

    private fun ambientFx(dt: Float) {
        val v = race.playerVehicle
        carPose(v, tmpP, tmpF)
        SpaceFx.update(theme, add, eye, tmpF, dt, rnd)
        val boostOn = v.boosting
        HotFx.update(theme, dt, eye, look, add, alpha, rnd)
        // boost: hot sparks from the exhausts + glowing trail
        if (boostOn) {
            exhaustCredit += dt * 42f
            while (exhaustCredit >= 1f) {
                exhaustCredit -= 1f
                for (side in -1..1 step 2) {
                    val width = v.params.halfWidth * 0.51f * side
                    val x = tmpP[0] - tmpF[0] * v.params.halfLength + tmpF[2] * width
                    val z = tmpP[2] - tmpF[2] * v.params.halfLength - tmpF[0] * width
                    add.spawn(x, tmpP[1] + 0.47f, z, -tmpF[0] * 18f, 0.3f, -tmpF[2] * 18f,
                        0.24f, 0.22f, 0.045f, 0.55f, 2.6f, 3.6f, 0.95f, 0f, 1f, 0.08f)
                }
            }
        } else exhaustCredit = 0f
        // tyre smoke when sliding
        for (ri in race.racers.indices) {
            val c = race.racers[ri].vehicle
            if (c.onGround && abs(c.vl) > 4.5f && rnd.nextFloat() < 0.6f) {
                carPose(c, tmpP, tmpF)
                alpha.spawn(tmpP[0] - tmpF[0] * 1.5f, tmpP[1] + 0.3f, tmpP[2] - tmpF[2] * 1.5f, 0f, 1f, 0f, 1.2f, 1f, 3.5f, 0.85f, 0.85f, 0.85f, 0.45f, -0.4f, 1f)
            }
        }
        // speed streaks ("motion blur" for speed): long thin lines rushing past the camera
        if (settings.motionBlur && v.speed > 30f) {
            val n = if (boostOn) 6 else 2
            carPose(v, tmpP, tmpF)
            repeat(n) {
                val ang = rnd.nextFloat() * 6.283f; val rad = 4f + rnd.nextFloat() * 6f
                track.frame(v.progress, fr)
                val ox = fr.rx * cos(ang) * rad; val oz = fr.rz * cos(ang) * rad; val oy = sin(ang) * rad * 0.6f + 2f
                add.spawn(eye[0] + tmpF[0] * 30f + ox, eye[1] + oy, eye[2] + tmpF[2] * 30f + oz, -tmpF[0] * v.speed * 1.4f, 0f, -tmpF[2] * v.speed * 1.4f,
                    0.35f, 0.06f, 0.06f, 1.2f, 1.3f, 1.5f, if (boostOn) 0.5f else 0.2f, 0f, 0f, 0.07f)
            }
        }
        if (confettiTimer > 0f) {
            confettiTimer -= dt
            carPose(v, tmpP, tmpF)
            repeat(6) {
                val hue = rnd.nextFloat()
                val r = abs(hue * 6 - 3) - 1; val g = 2 - abs(hue * 6 - 2); val b = 2 - abs(hue * 6 - 4)
                alpha.spawn(tmpP[0] + (rnd.nextFloat() - 0.5f) * 14f, tmpP[1] + 9f + rnd.nextFloat() * 4f, tmpP[2] + (rnd.nextFloat() - 0.5f) * 14f,
                    (rnd.nextFloat() - 0.5f) * 4f, -1f, (rnd.nextFloat() - 0.5f) * 4f, 3.5f, 0.35f, 0.3f, r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f, 1.2f, 0.8f, force = true)
            }
        }
        if (fireworkTimer > 0f) {
            fireworkTimer -= dt
            if (rnd.nextFloat() < dt * 2.2f) {
                carPose(v, tmpP, tmpF)
                val c = FIREWORK_COLOURS[rnd.nextInt(FIREWORK_COLOURS.size)]
                add.burst(70, tmpP[0] + (rnd.nextFloat() - 0.5f) * 30f, tmpP[1] + 18f + rnd.nextFloat() * 10f, tmpP[2] + (rnd.nextFloat() - 0.5f) * 30f,
                    14f, 0f, 1.4f, 0.5f, 0.1f, c[0], c[1], c[2], 1f, 4f, 0.03f)
                sound.play("firework")
            }
        }
    }

    // ------------------------------------------------------------------ frame
    fun update(realDt: Float, steer: Float, boost: Boolean) {
        time += realDt
        race.steerInput = steer
        if (boost) race.boostPressed = true
        // jump slow motion: slow while flying, ease back before landing
        val pv = race.playerVehicle
        val wantScale = if (camMode == CamMode.CINEMA && !pv.onGround) {
            cineTimer -= realDt * race.timeScale
            if (cineTimer > 0.35f) 0.4f else 1f
        } else 1f
        race.timeScale = lerp(race.timeScale, wantScale, 1f - exp(-realDt * 8f))
        if (camMode == CamMode.CINEMA && pv.onGround) camMode = CamMode.CHASE
        if (camMode == CamMode.INTRO && race.phase != RacePhase.COUNTDOWN) camMode = CamMode.CHASE

        race.update(realDt)
        handleEvents()

        // cars
        for (i in race.racers.indices) {
            val r = race.racers[i]
            val v = r.vehicle
            carPose(v, tmpP, tmpF)
            var fx = tmpF[0]; var fy = tmpF[1]; var fz = tmpF[2]
            var rx = fy * 0f - fz * 1f; var ry = fz * 0f - fx * 0f; var rz = fx * 1f - fy * 0f
            val rl = sqrt(rx * rx + ry * ry + rz * rz); rx /= rl; ry /= rl; rz /= rl
            val ux = ry * fz - rz * fy; val uy = rz * fx - rx * fz; val uz = rx * fy - ry * fx
            Mat.basis(mtx, rx, ry, rz, ux, uy, uz, fx, fy, fz, tmpP[0], tmpP[1], tmpP[2])
            val blink = v.resetFlash > 0f && ((time * 12).toInt() % 2 == 0)
            val kick = if (r.isPlayer) boostKick else 0f
            cars[i].update(mtx, v.bodyPitch, v.bodyRoll, v.bodyBounce, v.steerAngle, v.wheelSpin, if (v.boosting || warming) 1f + kick * 0.8f else 0f, time, blink)
            // light trails from both rear corners (model +X maps to -right)
            val heat = if (settings.effects != Level.OFF && v.boosting && !blink) 1f else 0f
            for (k in 0..1) {
                val lx = if (k == 0) 0.6f else -0.6f; val ly = 0.55f; val lz = -1.9f
                trails.push(i, k, tmpP[0] - rx * lx + ux * ly + fx * lz, tmpP[1] - ry * lx + uy * ly + fy * lz, tmpP[2] - rz * lx + uz * ly + fz * lz, heat)
            }
        }
        boostKick = max(0f, boostKick - realDt * 3f)
        knock.update()
        ambientFx(realDt)
        updateCamera(realDt)
        val cr = camera.getLeftVector(camLeft); val cu = camera.getUpVector(camUp)
        if (warming) {
            // one of each particle kind right in front of the camera (hidden by the loading screen)
            add.spawn(look[0], look[1], look[2], 0f, 0f, 0f, 0.1f, 0.3f, 0.3f, 1f, 1f, 1f, 1f, force = true)
            alpha.spawn(look[0], look[1], look[2], 0f, 0f, 0f, 0.1f, 0.3f, 0.3f, 1f, 1f, 1f, 1f, force = true)
        }
        trails.update(eye[0], eye[1], eye[2])
        add.update(realDt * race.timeScale, eye[0], eye[1], eye[2], -cr[0], -cr[1], -cr[2], cu[0], cu[1], cu[2])
        alpha.update(realDt * race.timeScale, eye[0], eye[1], eye[2], -cr[0], -cr[1], -cr[2], cu[0], cu[1], cu[2])
        tropics?.update(realDt * race.timeScale, eye[0], eye[1], eye[2], -cr[0], -cr[1], -cr[2], cu[0], cu[1], cu[2])
        weather?.update(realDt * race.timeScale, eye, camera)
        sound.engine(pv.speed, pv.boosting); sound.tires(abs(pv.vl), pv.onGround)

        // HUD
        hud.place = race.player.place; hud.lap = race.lap; hud.energy = pv.energy
        hud.speedKmh = pv.speed * 3.6f; hud.boosting = pv.boosting
        hud.countdown = race.countdown; hud.phase = race.phase
        hud.flash = max(0f, hud.flash - realDt * 2f); hud.whoops = max(0f, hud.whoops - realDt)
        for (i in race.racers.indices) hud.progress[i] = (race.racers[i].vehicle.progress / (race.laps * track.length)).coerceIn(0f, 1f)
    }
    private val camLeft = FloatArray(3); private val camUp = FloatArray(3)

    companion object {
        const val NEAR = 0.5; const val FAR = 3000.0
        private val FIREWORK_COLOURS = arrayOf(floatArrayOf(4f, 1f, 1.5f), floatArrayOf(1f, 3f, 4f), floatArrayOf(4f, 3.5f, 0.8f), floatArrayOf(1.2f, 4f, 1.5f))
    }

    fun restart() {
        knock.resetAll()
    }

    fun destroy() {
        cars.forEach { it.destroy() }
        knock.destroy(); scenery.destroy(); trackR.destroy(); env.destroy()
        water.destroy(); trails.destroy()
        add.destroy(scene); alpha.destroy(scene)
        tropics?.destroy()
        weather?.destroy(scene)
        grading?.let { view.colorGrading = null; engine.destroyColorGrading(it) }
        // leave the view as the menus expect it: no race fog, boost vignette or jump-cam blur carried over
        view.fogOptions = View.FogOptions()
        view.vignetteOptions = View.VignetteOptions()
        settings.setDof(view, false, 12f, camera)
    }
}
