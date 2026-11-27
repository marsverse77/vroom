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
        // Each silhouette has its own deck, waist and nose section.
        val widths = when (livery) {
            RaceLivery.Sprint -> floatArrayOf(.91f,1.04f,1.06f,.92f,.94f,1.04f,.96f,.82f)
            RaceLivery.Pearl -> floatArrayOf(.76f,.95f,1.08f,1.04f,1f,1.04f,.90f,.69f)
            RaceLivery.Comet -> floatArrayOf(1.06f,1.08f,.95f,.64f,.68f,.81f,.70f,.52f)
            RaceLivery.Summit -> floatArrayOf(1.12f,1.06f,1f,1.04f,1.04f,1.03f,1.04f,1.1f)
            RaceLivery.Courier -> floatArrayOf(1.08f,1.04f,1f,.98f,.97f,.96f,.87f,.91f)
            RaceLivery.Rally -> floatArrayOf(.65f,.84f,.91f,.79f,.76f,.88f,.79f,.64f)
            RaceLivery.Honey -> floatArrayOf(1.14f,1.04f,1.05f,.94f,1f,1.03f,1.08f,1.12f)
        }
        for (i in body.indices) body[i][1] *= widths[i]
        if (livery == RaceLivery.Courier || livery == RaceLivery.Summit) {
            for (i in 0..3) body[i][3] += belt * .20f
        }
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
            // A broad, continuous eye panel follows the rake of the windscreen. The upper
            // paint-coloured brow slopes inward, giving each pilot a focused expression.
            val ex = s * w * cockpitWidth * 0.37f
            val ez = (cockpitZ + cockpitLength * 0.58f) * l
            val eyeW = w * cockpitWidth * 0.47f
            val eyeD = l * cockpitLength * 0.29f
            fun panel(u: Float, v: Float): Int {
                val halfWidth = w * cockpitWidth * (.77f + .06f*v)
                val x = u * halfWidth
                val upper = .32f + .10f * (1f - abs(u))
                val lower = .86f - .08f * abs(u).pow(3f)
                val z = (cockpitZ + cockpitLength * (upper + (lower-upper)*v)) * l
                return c.trim.vertex(x,top(windows,.69f,x,z)+.015f,z,0f,.8f,.6f,Rgba.hex(0xd9f4ff))
            }
            for (i in 0 until 8) for (j in 0 until 3) {
                val a = s*i/8f; val b = s*(i+1)/8f
                c.trim.quadFacing(panel(a,j/3f),panel(b,j/3f),panel(b,(j+1)/3f),panel(a,(j+1)/3f))
            }
            val iris = when (livery) {
                RaceLivery.Sprint, RaceLivery.Honey -> Rgba.hex(0x39835c)
                RaceLivery.Comet, RaceLivery.Pearl -> Rgba.hex(0x3199c3)
                else -> Rgba.hex(0xa46b35)
            }
            lens(ex, ez, eyeW*.48f, eyeD*.69f, .039f, iris)
            lens(ex, ez, eyeW*.29f, eyeD*.55f, .050f, graphite)
            lens(ex-eyeW*.10f, ez-eyeD*.16f, eyeW*.10f, eyeD*.14f, .063f, Rgba.WHITE)
        }
        val roofZ = (cockpitZ - cockpitLength*.15f)*l
        CarKit.ellipsoid(c.paint,w*cockpitWidth*.88f,.07f,l*cockpitLength*.35f,Rgba.WHITE,
            Mat.trs(0f,top(windows,.69f,0f,roofZ)-.035f,roofZ),16,6)
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
        for (side in listOf(-1f, 1f)) {
            val x = side * w
            when (livery) {
                RaceLivery.Sprint, RaceLivery.Pearl -> {
                    // Sculpted shoulders frame the hood; inset LED blades sweep toward the cockpit.
                    CarKit.ellipsoid(c.paint, w*.21f, belt*.25f, l*.40f, Rgba.WHITE,
                        Mat.trs(x*.79f, belt*.98f, l*.52f, ry=side*.09f), 16, 8)
                    Prims.roundBox(c.lights,.065f,.048f,l*.34f,.021f,Rgba.hex(0xadefff),
                        Mat.trs(x*.80f,belt*1.19f,l*.66f,rx=.11f,ry=side*.24f))
                }
                RaceLivery.Comet -> CarKit.ellipsoid(c.paint,w*.24f,belt*.34f,l*.56f,Rgba.WHITE,
                    Mat.trs(x*.84f,belt*.78f,-l*.17f),16,8)
                RaceLivery.Rally -> {
                    Prims.cylinder(c.trim,.15f,.16f,.16f,graphite,Mat.trs(x*.44f,belt*1.18f,l*.75f,rx=CarKit.HALF_PI),12)
                    Prims.sphere(c.lights,.13f,livery.color,Mat.trs(x*.44f,belt*1.18f,l*.75f+.08f,sz=.4f),10,6)
                }
                RaceLivery.Summit -> Prims.roundBox(c.trim,.16f,.18f,l*1.52f,.06f,graphite,
                    Mat.trs(x*1.03f,floor+.02f,0f))
                RaceLivery.Honey -> Prims.roundBox(c.paint,.24f,.22f,l*.50f,.09f,Rgba.WHITE,
                    Mat.trs(x*.28f,belt*1.19f,l*.52f,rx=.14f))
                RaceLivery.Courier -> Prims.roundBox(c.trim,.08f,.10f,l*.60f,.035f,graphite,
                    Mat.trs(x*.43f,crest+.045f,(cockpitZ-.13f)*l))
            }
        }
        if (livery == RaceLivery.Rally || livery == RaceLivery.Summit)
            CarKit.fenders(c, c.paint, gap=.035f, extraWidth=.015f)
        // Recessed grille, chin and rear diffuser read as a continuous dark fascia from race distance.
        Prims.roundBox(c.trim, w * noseWidth * 1.32f, 0.19f, 0.13f, 0.085f,
            graphite, Mat.trs(0f, floor + 0.19f, l * 0.99f))
        Prims.roundBox(c.trim, w * 1.42f, 0.22f, 0.18f, 0.085f, graphite,
            Mat.trs(0f, floor + 0.14f, -l * 0.985f))
        for (side in listOf(-1f,1f)) {
            val radius = wheelRadius
            if (livery != RaceLivery.Rally && livery != RaceLivery.Summit) {
                CarKit.ellipsoid(c.paint,w*.25f,radius*.40f,radius*1.14f,Rgba.WHITE,
                    Mat.trs(side*w*.82f,radius*1.75f,-p.rearAxle),18,8)
            }
            // The lower lip rises toward the corners to give the nose a readable smile.
            fun lip(t: Float, edge: Float): Int {
                val x = side*w*noseWidth*.65f*t
                val y = floor+.145f+.07f*t*t+edge*(.045f-.022f*t)
                return c.trim.vertex(x,y,l*0.99f+.071f,0f,0f,1f,Rgba.hex(0xc5dddf))
            }
            for (k in 0 until 8) c.trim.quadFacing(lip(k/8f,0f),lip((k+1)/8f,0f),
                lip((k+1)/8f,1f),lip(k/8f,1f))
        }
        for (i in -2..2) Prims.box(c.trim, 0.055f, 0.17f, 0.33f, graphite,
            Mat.trs(i * w * 0.23f, floor + 0.04f, -l * 0.97f))
        livery.apply(c, (belt * 1.22f + floor) / 2f)
        return c
    }
}
