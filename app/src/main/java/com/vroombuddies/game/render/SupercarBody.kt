package com.vroombuddies.game.render

import com.vroombuddies.game.render.cars.CarKit
import com.vroombuddies.game.sim.VehicleParams
import kotlin.math.*

internal object SupercarBody {
    private val graphite = Rgba.hex(0x17212b)
    private fun shell(mesh: MeshData, rings: Array<FloatArray>, color: Rgba, power: Float) {
        val base = mesh.vCount
        val segments = 32
        fun contour(value: Float) = sign(value) * abs(value).pow(power)
        fun point(row: Int, angle: Float): FloatArray {
            val (z, width, low, high) = rings[row]
            return floatArrayOf(width * contour(cos(angle)),
                (low + high + (high - low) * contour(sin(angle))) * 0.5f, z)
        }
        for (row in rings.indices) {
            for (side in 0 until segments) {
                val angle = (side * 2.0 * PI / segments).toFloat()
                val p = point(row, angle)
                val a = point(row, angle - 0.02f)
                val b = point(row, angle + 0.02f)
                val rear = point(max(0, row - 1), angle)
                val fore = point(min(rings.lastIndex, row + 1), angle)
                val dx = b[0] - a[0]
                val dy = b[1] - a[1]
                val dz = fore[2] - rear[2]
                val nx = dy * dz
                val ny = -dx * dz
                val nz = dx * (fore[1] - rear[1]) - dy * (fore[0] - rear[0])
                val length = sqrt(nx * nx + ny * ny + nz * nz)
                mesh.vertex(p[0], p[1], p[2], nx / length, ny / length, nz / length, color)
            }
        }
        for (row in 0 until rings.lastIndex) for (side in 0 until segments) {
            val a = base + row * segments + side
            val b = base + row * segments + (side + 1) % segments
            mesh.quad(a, b, b + segments, a + segments)
        }
        for (row in listOf(0, rings.lastIndex)) {
            val ring = rings[row]
            val cap = mesh.vertex(0f, (ring[2] + ring[3]) / 2f, ring[0], 0f, 0f,
                if (row == 0) -1f else 1f, color)
            for (side in 0 until segments) {
                val a = base + row * segments + side
                val b = base + row * segments + (side + 1) % segments
                if (row == 0) mesh.tri(cap, b, a) else mesh.tri(cap, a, b)
            }
        }

    }

    fun build(p: VehicleParams, livery: RaceLivery, wheelRadius: Float, noseWidth: Float,
              belt: Float, roofRise: Float, cockpitZ: Float, cockpitLength: Float, cockpitWidth: Float): CarParts {
        val c = CarParts()
        val w = p.halfWidth
        val l = p.halfLength
        val floor = wheelRadius * 0.58f
        fun station(z: Float, width: Float, low: Float, high: Float) = floatArrayOf(z*l, width*w, low, high)
        val body = arrayOf(station(-1f, 0.76f, floor + 0.10f, belt * 1.12f),
            station(-0.87f, 0.95f, floor, belt * 1.27f), station(-0.60f, 1f, floor, belt * 1.37f),
            station(-0.12f, 0.91f, floor, belt * 1.22f), station(0.32f, 0.93f, floor, belt * 1.14f),
            station(0.64f, 0.99f, floor, belt * 1.18f), station(0.88f, 0.91f, floor, belt * 0.95f),
            station(1f, noseWidth, floor + 0.06f, belt * 0.79f))
        shell(c.paint, body, Rgba.WHITE, 0.52f)
        fun canopy(z: Float, width: Float, rise: Float) =
            station(cockpitZ + z * cockpitLength, width * cockpitWidth, belt, belt * 1.17f + rise * roofRise)
        val windows = arrayOf(canopy(-1f, 0.87f, 0.05f), canopy(-0.48f, 1f, 0.78f),
            canopy(-0.17f, 0.94f, 1f), canopy(0.24f, 0.88f, 0.96f),
            canopy(0.78f, 0.97f, 0.38f), canopy(1f, 0.83f, 0.03f))
        shell(c.glass, windows, Rgba.hex(0x234253), 0.69f)
        // Sample the top of the same loft to conform printed stripes and eye graphics to the panels.
        fun top(rings: Array<FloatArray>, sharpness: Float, x: Float, z: Float): Float {
            val row = (0 until rings.lastIndex).firstOrNull { z <= rings[it + 1][0] } ?: rings.lastIndex - 1
            fun height(r: FloatArray): Float {
                val across = (abs(x) / r[1]).coerceIn(0f, 1f)
                val crown = (1f - across.pow(2f / sharpness)).coerceAtLeast(0f).pow(sharpness / 2f)
                return (r[2] + r[3] + (r[3] - r[2]) * crown) * 0.5f
            }
            val a = rings[row]
            val b = rings[row + 1]
            val t = ((z - a[0]) / (b[0] - a[0])).coerceIn(0f, 1f)
            return height(a) * (1f - t) + height(b) * t
        }
        for (s in listOf(-1f, 1f)) {
            fun stripe(t: Float, outer: Boolean): Int {
                val z = l * (0.62f + t * 0.34f)
                val spread = when (livery) {
                    RaceLivery.Comet, RaceLivery.Sprint -> 0.43f - t * 0.25f
                    RaceLivery.Summit, RaceLivery.Rally -> 0.28f + t * 0.10f
                    else -> 0.28f - t * 0.03f
                }
                val x = s * w * (spread + if (outer) 0.12f * (1f - t * 0.65f) else 0f)
                return c.trim.vertex(x, top(body, 0.52f, x, z) + 0.012f, z, 0f, 1f, 0f, livery.color)
            }
            for (i in 0 until 10) c.trim.quadFacing(stripe(i / 10f, false), stripe(i / 10f, true),
                stripe((i+1) / 10f, true), stripe((i+1) / 10f, false))

            // Flush oval graphics on the sloped windshield keep the character face inside the glass.
            fun lens(cx: Float, cz: Float, radiusX: Float, radiusZ: Float, lift: Float, ink: Rgba) {
                val steps = 24
                val first = c.trim.vCount
                for (j in 0..4) for (i in 0 until steps) {
                    val angle = (2 * PI * i / steps).toFloat()
                    val x = cx + radiusX * j / 4f * cos(angle)
                    val z = cz + radiusZ * j / 4f * sin(angle)
                    val y = top(windows, 0.69f, x, z) + lift
                    val slope = (top(windows, 0.69f, x, z + 0.006f) - top(windows, 0.69f, x, z - 0.006f)) / 0.012f
                    val norm = sqrt(1f + slope * slope)
                    c.trim.vertex(x, y, z, 0f, 1f / norm, -slope / norm, ink)
                }
                for (j in 0 until 4) for (i in 0 until steps) {
                    val a = first + j * steps + i
                    val b = first + j * steps + (i + 1) % steps
                    c.trim.quad(a, b, b + steps, a + steps)
                }
            }
            val ex = s * w * cockpitWidth * 0.39f
            val ez = (cockpitZ + cockpitLength * 0.62f) * l
            val eyeW = w * cockpitWidth * 0.40f
            val eyeD = l * cockpitLength * 0.27f
            lens(ex, ez, eyeW, eyeD, 0.014f, Rgba.hex(0xe6eff3))
            lens(ex - s * eyeW * 0.13f, ez + eyeD * 0.02f, eyeW * 0.39f, eyeD * 0.78f, 0.032f, Rgba.hex(0x227d9a))
            lens(ex - s * eyeW * 0.13f, ez + eyeD * 0.03f, eyeW * 0.23f, eyeD * 0.58f, 0.045f, graphite)
            lens(ex - eyeW * 0.17f, ez - eyeD * 0.27f, eyeW * 0.11f, eyeD * 0.16f, 0.057f, Rgba.WHITE)
        }
        val width = wheelRadius * 0.76f
        CarKit.wheels(c, wheelRadius, width, w - width * 0.43f, p.frontAxle, -p.rearAxle, livery)
        val crest = belt * 1.17f + roofRise
        val rear = -l * 0.86f
        for (side in listOf(-1f, 1f)) {
            Prims.roundBox(c.trim, w * 0.14f, 0.11f, l * 1.48f, 0.05f, graphite,
                Mat.trs(side * w * 0.90f, floor + 0.025f, 0f))
            Prims.roundBox(c.lights, w * 0.47f, 0.053f, 0.08f, 0.025f, Rgba.hex(0xc2fcff),
                Mat.trs(side * w * noseWidth * 0.75f, belt * 0.71f, l * 0.99f, ry = side * 0.24f))
            Prims.roundBox(c.lights, w * 0.63f, 0.058f, 0.08f, 0.024f, Rgba.hex(0xff293f),
                Mat.trs(side * w * 0.49f, belt, -l))
            val x = side * w * 0.51f
            c.exhausts.add(floatArrayOf(x, floor + 0.14f, -l - 0.07f))
            Prims.cylinder(c.trim, 0.11f, 0.14f, 0.19f, graphite, Mat.trs(x, floor + 0.14f, -l, rx = CarKit.HALF_PI), 12)
        }
        Prims.roundBox(c.trim, w * noseWidth * 2.15f, 0.10f, 0.25f, 0.04f, graphite, Mat.trs(0f, floor + 0.06f, l * 0.98f))
        when (livery) {
            RaceLivery.Sprint, RaceLivery.Summit -> {
                val height = belt * (if (livery == RaceLivery.Summit) 1.82f else 1.55f)
                for (side in listOf(-1f, 1f)) Prims.box(c.trim, 0.07f, height - belt, 0.22f,
                    graphite, Mat.trs(side * w * 0.52f, (height + belt) / 2, rear))
                Prims.roundBox(c.trim, 2.04f * w, 0.105f, 0.42f, 0.05f, graphite, Mat.trs(0f, height, rear, rx = -0.08f))
            }
            RaceLivery.Comet -> Prims.roundBox(c.paint, 0.08f, 0.62f, l * 0.78f, 0.035f,
                Rgba.WHITE, Mat.trs(0f, belt * 1.35f, rear * 0.82f, rx = 0.23f))
            RaceLivery.Rally -> for (s in listOf(-1f, 1f))
                Prims.torus(c.trim, w * 0.27f, 0.055f, graphite, Mat.trs(s * w * 0.35f, crest - 0.12f,
                    (cockpitZ - 0.15f) * l), 6, 16, PI.toFloat())
            RaceLivery.Honey -> for (i in 0..4)
                Prims.box(c.trim, w * (1.35f - 0.10f * i), 0.06f, 0.08f, graphite,
                    Mat.trs(0f, belt * 1.32f + i * 0.07f, rear + i * 0.12f))
            RaceLivery.Courier -> Prims.roundBox(c.paint, w * 1.5f, 0.10f, 0.34f, 0.04f,
                Rgba.WHITE, Mat.trs(0f, crest, (cockpitZ - 0.18f) * l))
            RaceLivery.Pearl -> for (s in listOf(-1f, 1f))
                CarKit.ellipsoid(c.paint, 0.19f, 0.18f, l * 0.31f, Rgba.WHITE,
                    Mat.trs(s * w * 0.48f, belt * 1.26f, rear * 0.76f))
        }
        // Recessed grille, chin and rear diffuser read as a continuous dark fascia from race distance.
        Prims.roundBox(c.trim, w * noseWidth * 1.32f, 0.19f, 0.13f, 0.085f,
            graphite, Mat.trs(0f, floor + 0.19f, l * 0.99f))
        Prims.roundBox(c.trim, w * 1.42f, 0.22f, 0.18f, 0.085f, graphite,
            Mat.trs(0f, floor + 0.14f, -l * 0.985f))
        for (i in -2..2) Prims.box(c.trim, 0.055f, 0.17f, 0.33f, graphite,
            Mat.trs(i * w * 0.23f, floor + 0.04f, -l * 0.97f))
        livery.apply(c, (belt * 1.22f + floor) / 2f)
        return c
    }
}
