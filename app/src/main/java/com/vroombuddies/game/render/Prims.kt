package com.vroombuddies.game.render

import kotlin.math.*

/**
 * Primitive shape generators. Each call appends a shape to [m], placed by matrix [t] and painted [c].
 * Shapes are built around the origin: boxes/spheres centred, cylinders/cones along +Y centred on their middle.
 */
object Prims {
    private val scratch = object : ThreadLocal<MeshData>() { override fun initialValue() = MeshData() }
    private val tmp get() = scratch.get()!!

    private fun put(m: MeshData, t: FloatArray?, c: Rgba, build: (MeshData) -> Unit) {
        val tmp = this.tmp
        tmp.vCount = 0; tmp.iCount = 0
        build(tmp)
        for (i in 0 until tmp.vCount) { tmp.col[i * 4] = c.r; tmp.col[i * 4 + 1] = c.g; tmp.col[i * 4 + 2] = c.b; tmp.col[i * 4 + 3] = c.a }
        m.append(tmp, t)
    }

    fun box(m: MeshData, w: Float, h: Float, d: Float, c: Rgba, t: FloatArray? = null) = put(m, t, c) { g ->
        val x = w / 2; val y = h / 2; val z = d / 2
        face(g, -x, -y, z, x, -y, z, x, y, z, -x, y, z, 0f, 0f, 1f, w, h)
        face(g, x, -y, -z, -x, -y, -z, -x, y, -z, x, y, -z, 0f, 0f, -1f, w, h)
        face(g, x, -y, z, x, -y, -z, x, y, -z, x, y, z, 1f, 0f, 0f, d, h)
        face(g, -x, -y, -z, -x, -y, z, -x, y, z, -x, y, -z, -1f, 0f, 0f, d, h)
        face(g, -x, y, z, x, y, z, x, y, -z, -x, y, -z, 0f, 1f, 0f, w, d)
        face(g, -x, -y, -z, x, -y, -z, x, -y, z, -x, -y, z, 0f, -1f, 0f, w, d)
    }

    private fun face(g: MeshData, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, nx: Float, ny: Float, nz: Float, uw: Float, vh: Float) {
        val w = Rgba.WHITE
        val a = g.vertex(ax, ay, az, nx, ny, nz, w, 0f, 0f); val b = g.vertex(bx, by, bz, nx, ny, nz, w, uw, 0f)
        val c = g.vertex(cx, cy, cz, nx, ny, nz, w, uw, vh); val d = g.vertex(dx, dy, dz, nx, ny, nz, w, 0f, vh)
        g.quad(a, b, c, d)
    }

    /** Rounded box: a superellipsoid-ish shape made by pushing a subdivided cube toward a sphere at the corners. */
    fun roundBox(m: MeshData, w: Float, h: Float, d: Float, r: Float, c: Rgba, t: FloatArray? = null, seg: Int = 2) = put(m, t, c) { g ->
        val radius = minOf(r, w / 2, h / 2, d / 2)
        val hx = w / 2 - radius; val hy = h / 2 - radius; val hz = d / 2 - radius
        fun axis(unit: Float, inner: Float) = when {
            unit == 0f -> 0f
            else -> sign(unit) * (inner + abs(unit) * radius)
        }
        val n = seg * 2
        // 6 faces, each a grid; each grid point maps to cube point -> clamp to inner box + r * normalised offset
        val dirs = arrayOf(
            floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f), floatArrayOf(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f), floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f),
            floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, -1f), floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        for (f in dirs) {
            val base = g.vCount
            for (j in 0..n) for (i in 0..n) {
                val u = i.toFloat() / n * 2 - 1; val v = j.toFloat() / n * 2 - 1
                // cube point (unit), then map
                val px = f[0] + f[3] * u + f[6] * v; val py = f[1] + f[4] * u + f[7] * v; val pz = f[2] + f[5] * u + f[8] * v
                val x = axis(px,hx); val y = axis(py,hy); val z = axis(pz,hz)
                val ix = x.coerceIn(-hx,hx); val iy = y.coerceIn(-hy,hy); val iz = z.coerceIn(-hz,hz)
                // offset direction from inner box surface
                var ox = x - ix; var oy = y - iy; var oz = z - iz
                val l = sqrt(ox * ox + oy * oy + oz * oz).coerceAtLeast(1e-6f)
                ox /= l; oy /= l; oz /= l
                g.vertex(ix + ox * radius, iy + oy * radius, iz + oz * radius, ox, oy, oz, Rgba.WHITE, (u + 1) / 2, (v + 1) / 2)
            }
            for (j in 0 until n) for (i in 0 until n) {
                val a = base + j * (n + 1) + i
                g.quad(a, a + 1, a + n + 2, a + n + 1)
            }
        }
    }

    fun cylinder(m: MeshData, rTop: Float, rBot: Float, h: Float, c: Rgba, t: FloatArray? = null, seg: Int = 16, caps: Boolean = true, arc: Float = (PI * 2).toFloat()) = put(m, t, c) { g ->
        val y0 = -h / 2; val y1 = h / 2
        val slope = (rBot - rTop) / h
        for (i in 0..seg) {
            val a = i.toFloat() / seg * arc
            val cx = sin(a); val cz = cos(a)
            val nl = sqrt(1 + slope * slope)
            g.vertex(cx * rBot, y0, cz * rBot, cx / nl, slope / nl, cz / nl, Rgba.WHITE, i.toFloat() / seg, 0f)
            g.vertex(cx * rTop, y1, cz * rTop, cx / nl, slope / nl, cz / nl, Rgba.WHITE, i.toFloat() / seg, 1f)
        }
        for (i in 0 until seg) { val a = i * 2; g.quad(a, a + 2, a + 3, a + 1) }
        if (caps) {
            for ((y, r, ny) in listOf(Triple(y1, rTop, 1f), Triple(y0, rBot, -1f))) {
                if (r <= 0.0001f) continue
                val c0 = g.vertex(0f, y, 0f, 0f, ny, 0f, Rgba.WHITE, 0.5f, 0.5f)
                val start = g.vCount
                for (i in 0..seg) { val a = i.toFloat() / seg * arc; g.vertex(sin(a) * r, y, cos(a) * r, 0f, ny, 0f, Rgba.WHITE) }
                for (i in 0 until seg) if (ny > 0) g.tri(c0, start + i, start + i + 1) else g.tri(c0, start + i + 1, start + i)
            }
        }
    }

    fun cone(m: MeshData, r: Float, h: Float, c: Rgba, t: FloatArray? = null, seg: Int = 14) = cylinder(m, 0f, r, h, c, t, seg)

    fun sphere(m: MeshData, r: Float, c: Rgba, t: FloatArray? = null, ws: Int = 18, hs: Int = 12, thetaMax: Float = PI.toFloat()) = put(m, t, c) { g ->
        for (j in 0..hs) {
            val th = j.toFloat() / hs * thetaMax
            for (i in 0..ws) {
                val ph = i.toFloat() / ws * PI.toFloat() * 2
                val x = sin(th) * sin(ph); val y = cos(th); val z = sin(th) * cos(ph)
                g.vertex(x * r, y * r, z * r, x, y, z, Rgba.WHITE, i.toFloat() / ws, j.toFloat() / hs)
            }
        }
        for (j in 0 until hs) for (i in 0 until ws) {
            val a = j * (ws + 1) + i
            g.quad(a, a + ws + 1, a + ws + 2, a + 1)
        }
    }

    /** Torus in the XY plane (ring around Z). */
    fun torus(m: MeshData, R: Float, r: Float, c: Rgba, t: FloatArray? = null, rs: Int = 10, ts: Int = 24, arc: Float = (PI * 2).toFloat()) = put(m, t, c) { g ->
        for (j in 0..ts) {
            val u = j.toFloat() / ts * arc
            for (i in 0..rs) {
                val v = i.toFloat() / rs * PI.toFloat() * 2
                val nx = cos(v) * cos(u); val ny = cos(v) * sin(u); val nz = sin(v)
                g.vertex((R + r * cos(v)) * cos(u), (R + r * cos(v)) * sin(u), r * sin(v), nx, ny, nz, Rgba.WHITE, j.toFloat() / ts, i.toFloat() / rs)
            }
        }
        for (j in 0 until ts) for (i in 0 until rs) {
            val a = j * (rs + 1) + i
            g.quad(a, a + rs + 1, a + rs + 2, a + 1)
        }
    }

    /** Low-poly rock / icosahedron-ish blob with flat shading. */
    fun rock(m: MeshData, r: Float, c: Rgba, t: FloatArray? = null, seed: Int = 1) = put(m, t, c) { g ->
        val p = 0.5f * (1 + sqrt(5f))
        val v = arrayOf(floatArrayOf(-1f, p, 0f), floatArrayOf(1f, p, 0f), floatArrayOf(-1f, -p, 0f), floatArrayOf(1f, -p, 0f), floatArrayOf(0f, -1f, p), floatArrayOf(0f, 1f, p), floatArrayOf(0f, -1f, -p), floatArrayOf(0f, 1f, -p), floatArrayOf(p, 0f, -1f), floatArrayOf(p, 0f, 1f), floatArrayOf(-p, 0f, -1f), floatArrayOf(-p, 0f, 1f))
        var s = seed
        for (q in v) { s = s * 1103515245 + 12345; val k = 0.8f + ((s ushr 16) and 255) / 255f * 0.4f; val l = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]); q[0] *= k / l * r; q[1] *= k / l * r; q[2] *= k / l * r }
        val f = intArrayOf(0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8, 3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1)
        for (i in f.indices step 3) {
            val a = v[f[i]]; val b = v[f[i + 1]]; val cc = v[f[i + 2]]
            val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
            val wx = cc[0] - a[0]; val wy = cc[1] - a[1]; val wz = cc[2] - a[2]
            var nx = uy * wz - uz * wy; var ny = uz * wx - ux * wz; var nz = ux * wy - uy * wx
            val l = sqrt(nx * nx + ny * ny + nz * nz); nx /= l; ny /= l; nz /= l
            val i0 = g.vertex(a[0], a[1], a[2], nx, ny, nz, Rgba.WHITE)
            val i1 = g.vertex(b[0], b[1], b[2], nx, ny, nz, Rgba.WHITE)
            val i2 = g.vertex(cc[0], cc[1], cc[2], nx, ny, nz, Rgba.WHITE)
            g.tri(i0, i1, i2)
        }
    }

    /** Flat quad in XZ facing up, centred, size w x d. */
    fun plane(m: MeshData, w: Float, d: Float, c: Rgba, t: FloatArray? = null, uRep: Float = 1f, vRep: Float = 1f) = put(m, t, c) { g ->
        val a = g.vertex(-w / 2, 0f, -d / 2, 0f, 1f, 0f, Rgba.WHITE, 0f, 0f)
        val b = g.vertex(w / 2, 0f, -d / 2, 0f, 1f, 0f, Rgba.WHITE, uRep, 0f)
        val cc = g.vertex(w / 2, 0f, d / 2, 0f, 1f, 0f, Rgba.WHITE, uRep, vRep)
        val dd = g.vertex(-w / 2, 0f, d / 2, 0f, 1f, 0f, Rgba.WHITE, 0f, vRep)
        g.quad(a, dd, cc, b)
    }
}
