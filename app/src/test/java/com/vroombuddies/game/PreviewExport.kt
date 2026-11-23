package com.vroombuddies.game

import com.vroombuddies.game.content.Knock
import com.vroombuddies.game.content.Theme
import com.vroombuddies.game.content.Themes
import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.Race
import com.vroombuddies.game.sim.RacePhase
import com.vroombuddies.game.sim.TrackPath
import com.vroombuddies.game.sim.Vehicle
import com.vroombuddies.game.sim.Frame
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Writes the game's real geometry (track, scenery, cars, tyre stacks) plus camera poses to
 * $PREVIEW_DIR, so tools/preview can render screenshots without a phone. Skipped unless PREVIEW_DIR is set.
 */
class PreviewExport {
    private class Scene(val dir: File, val name: String) {
        val items = StringBuilder()
        var n = 0
        fun mesh(m: MeshData, mat: String, matrix: FloatArray? = null, extra: String = "") {
            if (m.vCount == 0) return
            val file = "${name}_${n++}.bin"
            val bb = ByteBuffer.allocate((m.vCount * 12) * 4 + m.iCount * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until m.vCount * 3) bb.putFloat(m.pos[i])
            for (i in 0 until m.vCount * 3) bb.putFloat(m.nrm[i])
            for (i in 0 until m.vCount * 4) bb.putFloat(m.col[i])
            for (i in 0 until m.vCount * 2) bb.putFloat(m.uv[i])
            for (i in 0 until m.iCount) bb.putInt(m.idx[i])
            File(dir, file).writeBytes(bb.array())
            if (items.isNotEmpty()) items.append(",\n")
            items.append("{\"file\":\"$file\",\"v\":${m.vCount},\"i\":${m.iCount},\"mat\":\"$mat\"")
            matrix?.let { items.append(",\"m\":[${it.joinToString(",")}]") }
            if (extra.isNotEmpty()) items.append(",$extra")
            items.append("}")
        }
        fun write(json: String) = File(dir, "$name.json").writeText("{$json,\"meshes\":[\n$items\n]}")
    }

    private fun lin(c: Int) = Rgba.hex(c).let { "[${it.r},${it.g},${it.b}]" }

    private fun envJson(t: Theme) = "\"sky\":[${lin(t.skyTop)},${lin(t.skyHorizon)},${lin(t.skyBottom)}],\"sun\":{\"color\":${lin(t.sunColor)},\"i\":${t.sunIntensity},\"el\":${t.sunElevation},\"az\":${t.sunAzimuth}}," +
        "\"ambient\":${t.ambient},\"fog\":{\"color\":${lin(t.fogColor)},\"density\":${t.fogDensity}},\"sat\":${t.saturation},\"contrast\":${t.contrast}"

    private fun addCar(sc: Scene, design: CarDesign, paint: Paint, world: FloatArray, steer: Float, spin: Float, boost: Boolean) {
        val parts = design.build()
        val c = Rgba.hex(paint.color)
        sc.mesh(parts.paint, "paint", world, "\"color\":[${c.r},${c.g},${c.b}],\"metallic\":${paint.metallic},\"rough\":${paint.roughness},\"rainbow\":${paint.rainbow}")
        sc.mesh(parts.trim, "lit", world, "\"rough\":0.45")
        sc.mesh(parts.glass, "lit", world, "\"rough\":0.04,\"metallic\":0.1")
        sc.mesh(parts.lights, "glow", world, "\"k\":6")
        for (i in 0 until 4) {
            val p = parts.wheelPos[i]
            val local = Mat.mul(Mat.trs(p[0], p[1], p[2], 0f, if (i < 2) -steer else 0f, 0f), Mat.trs(rx = spin))
            sc.mesh(parts.wheel, "lit", Mat.mul(world, local), "\"rough\":0.55")
        }
        if (boost) {
            val fm = MeshData()
            Prims.cone(fm, 0.28f, 1.6f, Rgba(1f, 0.45f, 0.08f), Mat.trs(0f, 0f, -0.8f, rx = (-PI / 2).toFloat()))
            Prims.cone(fm, 0.15f, 1.0f, Rgba(1f, 0.95f, 0.6f), Mat.trs(0f, 0f, -0.5f, rx = (-PI / 2).toFloat()))
            for (ex in parts.exhausts) sc.mesh(fm, "glow", Mat.mul(world, Mat.trs(ex[0], ex[1], ex[2], sx = 1.1f, sy = 1.1f, sz = 1.3f)), "\"k\":8")
        }
    }

    private fun carWorld(track: TrackPath, v: Vehicle, out: FloatArray, fwd: FloatArray): FloatArray {
        val fr = Frame()
        track.frame(v.progress, fr)
        val ca = cos(v.heading); val sa = sin(v.heading)
        var fx = fr.tx * ca + fr.rx * sa; var fz = fr.tz * ca + fr.rz * sa
        var fy = if (v.onGround) fr.slope * ca else 0f
        val l = sqrt(fx * fx + fy * fy + fz * fz); fx /= l; fy /= l; fz /= l
        out[0] = fr.x + fr.rx * v.x; out[1] = v.y; out[2] = fr.z + fr.rz * v.x
        fwd[0] = fx; fwd[1] = fy; fwd[2] = fz
        var rx = -fz; var rz = fx; val rl = sqrt(rx * rx + rz * rz); rx /= rl; rz /= rl
        val ux = -rz * fy; val uy = rz * fx - rx * fz; val uz = rx * fy
        val root = Mat.basis(FloatArray(16), rx, 0f, rz, ux, uy, uz, fx, fy, fz, out[0], out[1], out[2])
        return Mat.mul(root, Mat.trs(0f, v.bodyBounce, 0f, v.bodyPitch, 0f, -v.bodyRoll))
    }

    @Test
    fun export() {
        val dirName = System.getenv("PREVIEW_DIR")
        Assume.assumeTrue(dirName != null)
        val dir = File(dirName!!).apply { mkdirs() }
        // PREVIEW_THEME / PREVIEW_CAR pick another track or car (defaults: the first of each)
        val theme = Themes.all[System.getenv("PREVIEW_THEME")?.toIntOrNull() ?: 0]
        val track = TrackPath(theme.shape)

        // ---- static world (track + scenery), shared by every race shot
        val world = Scene(dir, "world")
        TrackGeometry(track, theme).build { m, layer -> world.mesh(m, "track", null, "\"layer\":\"$layer\"") }
        val layout = SceneryLayout(track, theme).place()
        for (c in layout.cells.values) { world.mesh(c.near, "lit", null, "\"rough\":0.8,\"wind\":true"); world.mesh(c.glow, "glow", null, "\"k\":3") }
        world.write(envJson(theme) + ",\"missing\":\"${layout.missing.joinToString()}\"")

        // ---- race moments from the real simulation
        val paints = Paints.all
        val design = CarDesigns.all[System.getenv("PREVIEW_CAR")?.toIntOrNull() ?: 0]
        val playerPaint = paints[0]
        val aiPaints = paints.filter { it.color != playerPaint.color && !it.rainbow }.shuffled(kotlin.random.Random(theme.shape.seed))
        val designs = CarDesigns.all
        val aiCars = (0 until 5).map { i -> val d = designs[(i + 1) % designs.size]; i to d.params.copy(grip = d.params.grip * theme.grip / 2f) }
        val race = Race(track, theme.gravity, 2, theme.ground != null, Knock.kinds(theme.knock),
            design.params.copy(grip = design.params.grip * theme.grip / 2f), 0, 0, aiCars, aiPaints.indices.toList(), theme.shape.seed)

        fun shot(name: String, cam: String, boostPlayer: Boolean) {
            val sc = Scene(dir, name)
            var ai = 0
            val p = FloatArray(3); val f = FloatArray(3)
            for (r in race.racers) {
                val v = r.vehicle
                val w = carWorld(track, v, p, f)
                val d = if (r.isPlayer) design else designs[(ai + 1) % designs.size]
                val pt = if (r.isPlayer) playerPaint else aiPaints[ai++ % aiPaints.size]
                addCar(sc, d, pt, w, v.steerAngle, v.wheelSpin, v.boosting || (r.isPlayer && boostPlayer))
            }
            val meshes = HashMap<String, List<MeshData>>()
            for (b in race.props.bodies) {
                if (b.gone) continue
                val list = meshes.getOrPut(b.kind.id) { KnockRenderer.build(b.kind.id) }
                sc.mesh(list[b.tint % list.size], "lit", Mat.fromQuat(FloatArray(16), b.qx, b.qy, b.qz, b.qw, b.x, b.y, b.z), "\"rough\":0.6")
            }
            val pv = race.playerVehicle
            sc.write("$cam,\"hud\":{\"place\":${race.player.place},\"lap\":${race.lap},\"energy\":${pv.energy},\"speed\":${pv.speed * 3.6f},\"boost\":${pv.boosting},\"phase\":\"${race.phase}\",\"countdown\":${race.countdown}," +
                "\"progress\":[${race.racers.joinToString(",") { (it.vehicle.progress / (race.laps * track.length)).coerceIn(0f, 1f).toString() }}],\"playerIndex\":${race.racers.indexOf(race.player)}}")
        }

        fun chaseCam(): String {
            val v = race.playerVehicle; val p = FloatArray(3); val f = FloatArray(3); carWorld(track, v, p, f)
            val fr = Frame(); track.frame(v.progress, fr)
            var dx = f[0] * 0.45f + fr.tx * 0.55f; var dz = f[2] * 0.45f + fr.tz * 0.55f
            val dl = sqrt(dx * dx + dz * dz); dx /= dl; dz /= dl
            val boost = if (v.boosting) 1f else 0f
            val speedK = (v.speed / 60f).coerceIn(0f, 1.4f)
            val dist = 6.8f + boost * 1.2f - speedK * 0.4f
            val ey = max(p[1] + 2.5f + boost * 0.25f, track.groundHeight(v.progress) + 1f)
            return "\"eye\":[${p[0] - dx * dist},$ey,${p[2] - dz * dist}],\"look\":[${p[0] + dx * 5f},${p[1] + 1.2f},${p[2] + dz * 5f}],\"fov\":${60f + speedK * 8f + boost * 12f}"
        }

        // 1. countdown on the grid (intro camera, swinging round the car)
        for (i in 0 until 60) race.update(1f / 60f)
        run {
            val v = race.playerVehicle; val p = FloatArray(3); val f = FloatArray(3); carWorld(track, v, p, f)
            val fr = Frame(); track.frame(v.progress, fr)
            val t = (1f - race.countdown / 3.6f).coerceIn(0f, 1f); val a = (1f - t) * 2.6f; val r = 16f + (10f - 16f) * t
            val ex = p[0] + (fr.tx * cos(a) - fr.rx * sin(a)) * -r; val ez = p[2] + (fr.tz * cos(a) - fr.rz * sin(a)) * -r
            shot("race_start", "\"eye\":[$ex,${p[1] + 7f + (4f - 7f) * t},$ez],\"look\":[${p[0]},${p[1] + 1.2f},${p[2]}],\"fov\":60", false)
        }
        // 2. racing in the pack, boosting
        while (race.phase == RacePhase.COUNTDOWN) race.update(1f / 60f)
        for (i in 0 until 60 * 7) { race.steerInput = 0.15f * sin(i / 50f); race.update(1f / 60f) }
        race.boostPressed = true; race.update(1f / 60f)
        for (i in 0 until 30) race.update(1f / 60f)
        shot("race_chase", chaseCam(), true)
        // 3. first jump: cinematic side camera mid-air
        var guard = 0
        while (!(race.playerVehicle.airTime > 0.35f && !race.playerVehicle.onGround) && guard++ < 60 * 120) { race.steerInput = 0f; race.update(1f / 60f) }
        run {
            val v = race.playerVehicle; val p = FloatArray(3); val f = FloatArray(3); carWorld(track, v, p, f)
            val fr = Frame(); track.frame(v.progress, fr)
            val ex = p[0] + fr.rx * 12f + fr.tx * 8f; val ez = p[2] + fr.rz * 12f + fr.tz * 8f
            shot("race_jump", "\"eye\":[$ex,${p[1] + 1.5f},$ez],\"look\":[${p[0]},${p[1] + 1f},${p[2]}],\"fov\":50,\"letterbox\":true", false)
        }
        // 4. tyre stack hit: drive on until some stack has been knocked flying
        guard = 0
        while (race.props.bodies.none { !it.asleep && it.y > 1.5f } && guard++ < 60 * 200) { race.update(1f / 60f) }
        val flying = race.props.bodies.firstOrNull { !it.asleep && it.y > 1.5f }
        if (flying != null) {
            for (i in 0 until 2) race.update(1f / 60f)
            // stand on the road a little behind the stack, looking at it
            val fr = Frame(); track.frame(flying.sHint - 11f, fr)
            val lat = ((flying.x - fr.x) * fr.rx + (flying.z - fr.z) * fr.rz).coerceIn(-track.halfWidth + 2f, track.halfWidth - 2f) * 0.4f
            val ex = fr.x + fr.rx * lat; val ez = fr.z + fr.rz * lat
            shot("race_tires", "\"eye\":[$ex,${track.groundHeight(flying.sHint - 11f) + 2.6f},$ez],\"look\":[${flying.x},${flying.y},${flying.z}],\"fov\":55", false)
        }

        // ---- showroom (menus backdrop), same as render/Showroom.kt
        val show = Scene(dir, "showroom")
        val m = MeshData()
        Prims.cylinder(m, 5.2f, 5.4f, 0.5f, Rgba.hex(0xffffff), Mat.trs(0f, -0.25f, 0f), 48)
        Prims.torus(m, 5.3f, 0.18f, Rgba.hex(0xffd23f), Mat.trs(0f, 0.02f, 0f, rx = (PI / 2).toFloat()), 8, 48)
        show.mesh(m, "lit", null, "\"rough\":0.25")
        val fld = MeshData()
        Prims.plane(fld, 400f, 400f, Rgba.hex(0x6cc24a), Mat.trs(0f, -0.5f, 0f))
        val gb = SceneryLibrary.builders.getValue("roundTree")(0)
        val bush = SceneryLibrary.builders.getValue("flower")(0)
        val rng = com.vroombuddies.game.sim.Rng(4)
        for (i in 0 until 26) { val a = rng.f() * 6.28f; val r = 22f + rng.f() * 40f; fld.append(gb.lit, Mat.trs(cos(a) * r, -0.5f, sin(a) * r - 10f, ry = rng.f() * 6f, sx = 1.2f, sy = 1.2f, sz = 1.2f), Rgba.hex(0x5cc160)) }
        for (i in 0 until 160) { val a = rng.f() * 6.28f; val r = 7f + rng.f() * 25f; fld.append(bush.lit, Mat.trs(cos(a) * r, -0.5f, sin(a) * r, ry = rng.f() * 6f), Rgba.hex(listOf(0xff5fa2, 0xffffff, 0xffe14d, 0x8a7dff)[rng.int(4)])) }
        show.mesh(fld, "lit", null, "\"rough\":0.85")
        for ((k, pt) in listOf(paints[0], paints[3]).withIndex()) {
            // two variants (different paints) so menus can show the paint change
            val sc = if (k == 0) show else Scene(dir, "showroom_car$k")
            addCar(sc, design, pt, Mat.trs(0f, 0.03f, 0f, 0f, 0.9f, 0f), 0.2f, 0f, false)
            if (k != 0) sc.write("\"carOnly\":true")
        }
        show.write(envJson(theme) + ",\"eye\":[6.6,2.6,6.6],\"look\":[0,0.9,0],\"fov\":38")
        File(dir, "paints.txt").writeText(listOf(paints[0], paints[3]).joinToString("\n") { it.name })
    }
}
