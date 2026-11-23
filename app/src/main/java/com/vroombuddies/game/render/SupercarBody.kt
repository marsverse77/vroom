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
        shell(c.paint, arrayOf(station(-1f, 0.76f, floor + 0.10f, belt * 1.12f),
            station(-0.87f, 0.95f, floor, belt * 1.27f), station(-0.60f, 1f, floor, belt * 1.37f),
            station(-0.12f, 0.91f, floor, belt * 1.22f), station(0.32f, 0.93f, floor, belt * 1.14f),
            station(0.64f, 0.99f, floor, belt * 1.18f), station(0.88f, 0.91f, floor, belt * 0.95f),
            station(1f, noseWidth, floor + 0.06f, belt * 0.79f)), Rgba.WHITE, 0.52f)
        fun canopy(z: Float, width: Float, rise: Float) =
            station(cockpitZ + z * cockpitLength, width * cockpitWidth, belt, belt * 1.17f + rise * roofRise)
        shell(c.glass, arrayOf(canopy(-1f, 0.87f, 0.05f), canopy(-0.48f, 1f, 0.78f),
            canopy(-0.17f, 0.94f, 1f), canopy(0.24f, 0.88f, 0.96f),
            canopy(0.78f, 0.97f, 0.38f), canopy(1f, 0.83f, 0.03f)), Rgba.hex(0x234253), 0.69f)
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
        CarKit.face(c, (cockpitZ + cockpitLength * 0.86f) * l, belt * 1.24f + roofRise * 0.24f,
            w * 0.25f, w * 0.19f, l + 0.012f, floor + 0.09f, w * 0.24f, eyeSquash = 0.62f)
        livery.apply(c, (belt * 1.22f + floor) / 2f)
        return c
    }
}
