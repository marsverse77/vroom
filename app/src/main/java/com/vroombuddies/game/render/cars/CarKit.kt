package com.vroombuddies.game.render.cars

import com.vroombuddies.game.render.*
import kotlin.math.*

/** Shared parts for the cartoon cars in this package: faces, wheels, fenders, lights and smooth egg shapes. */
internal object CarKit {
    const val PI_F = PI.toFloat()
    const val HALF_PI = (PI / 2).toFloat()

    val DARK = Rgba.hex(0x2a2c33)
    val WHITE = Rgba.hex(0xf7f7f7)
    val BLACK = Rgba.hex(0x101014)
    val CHROME = Rgba.hex(0xc9ced6)
    val GLASS = Rgba.hex(0x1b2a44)
    val GLASS_LIGHT = Rgba.hex(0x2b4a7a)
    val BLUSH = Rgba.hex(0xff8fa8)
    val TONGUE = Rgba.hex(0xff6f8a)
    val TAIL = Rgba(1f, 0.08f, 0.05f)
    val AMBER = Rgba(1f, 0.6f, 0.08f)
    val TIRE = Rgba.hex(0x1d1e24)

    private val tmp = MeshData()

    /**
     * Ellipsoid (or its top part, up to [thetaMax] from the pole) with correct normals.
     * Prims.sphere + a non-uniform scale would bend the normals and make the shading look lumpy.
     */
    fun ellipsoid(m: MeshData, rx: Float, ry: Float, rz: Float, c: Rgba, t: FloatArray? = null, ws: Int = 28, hs: Int = 16, thetaMax: Float = PI_F) {
        val g = tmp; g.vCount = 0; g.iCount = 0
        for (j in 0..hs) {
            val th = j.toFloat() / hs * thetaMax
            for (i in 0..ws) {
                val ph = i.toFloat() / ws * PI_F * 2
                val ux = sin(th) * sin(ph); val uy = cos(th); val uz = sin(th) * cos(ph)
                var nx = ux / rx; var ny = uy / ry; var nz = uz / rz
                val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
                nx /= l; ny /= l; nz /= l
                g.vertex(ux * rx, uy * ry, uz * rz, nx, ny, nz, c, i.toFloat() / ws, j.toFloat() / hs)
            }
        }
        for (j in 0 until hs) for (i in 0 until ws) {
            val a = j * (ws + 1) + i
            g.quad(a, a + ws + 1, a + ws + 2, a + 1)
        }
        m.append(g, t)
    }

    /** Four identical wheels: front pair at [zf] (they steer), rear pair at [zr], centred [x] from the middle. */
    fun wheels(c: CarParts, r: Float, w: Float, x: Float, zf: Float, zr: Float, rim: Rgba = Rgba.hex(0xd9dde3)) {
        c.wheelRadius = r
        CarParts3.wheel(c.wheel, r, w, rim)
        c.wheelPos.add(floatArrayOf(-x, r, zf)); c.wheelPos.add(floatArrayOf(x, r, zf))
        c.wheelPos.add(floatArrayOf(-x, r, zr)); c.wheelPos.add(floatArrayOf(x, r, zr))
    }

    /** Open half-tube mudguards hugging the top of every wheel. */
    fun fenders(c: CarParts, m: MeshData, col: Rgba = Rgba.WHITE, gap: Float = 0.1f, extraWidth: Float = 0.1f) {
        for (p in c.wheelPos) {
            // the tyre is wider than the rim; reuse the design's wheel mesh width via its bounds
            val b = c.wheel.bounds()
            val w = (b[3] - b[0]) + extraWidth
            val r = c.wheelRadius + gap
            Prims.cylinder(m, r, r, w, col, Mat.trs(p[0], p[1], p[2], rz = HALF_PI), 20, false, PI_F)
            // a thin lip so the open edge doesn't look paper thin
            for (s in floatArrayOf(-1f, 1f))
                Prims.torus(m, r, 0.05f, col, Mat.trs(p[0] + s * w / 2, p[1], p[2], ry = HALF_PI), 5, 20, PI_F)
        }
    }

    /**
     * A friendly face. Eyes are big headlight eyes with pupils and a sparkle; the smile is a curved line whose lowest point
     * is at [mouthY]. Optional extras give each car its own personality.
     */
    fun face(
        c: CarParts,
        eyeZ: Float, eyeY: Float, eyeX: Float, eyeR: Float,
        mouthZ: Float, mouthY: Float, mouthW: Float,
        lookX: Float = 0f,
        lid: Float = -1f,           // >= 0: paint-coloured eyelids tilted by this many radians (a cool, determined look)
        brows: Boolean = false,     // raised eyebrows (an excited look)
        cheeks: Boolean = false,    // rosy cheeks
        tongue: Boolean = false,    // open happy grin with a little tongue
        eyeSquash: Float = 0.55f,
    ) {
        for (s in floatArrayOf(-1f, 1f)) {
            val ex = s * eyeX
            Prims.sphere(c.trim, eyeR, WHITE, Mat.trs(ex, eyeY, eyeZ, sz = eyeSquash), 18, 12)
            val px = ex + lookX * eyeR * 0.25f - s * eyeR * 0.06f
            Prims.sphere(c.trim, eyeR * 0.5f, BLACK, Mat.trs(px, eyeY + eyeR * 0.05f, eyeZ + eyeR * (eyeSquash - 0.19f), sz = 0.5f), 14, 10)
            Prims.sphere(c.lights, eyeR * 0.16f, Rgba(1f, 1f, 1f), Mat.trs(px + eyeR * 0.1f, eyeY + eyeR * 0.28f, eyeZ + eyeR * (eyeSquash - 0.02f)), 8, 6)
            if (lid >= 0f)
                Prims.sphere(c.paint, eyeR * 1.08f, Rgba.WHITE, Mat.trs(ex, eyeY, eyeZ, rz = s * lid, sz = eyeSquash + 0.08f), 18, 6, 1.05f)
            if (brows)
                Prims.roundBox(c.trim, eyeR * 1.15f, eyeR * 0.24f, eyeR * 0.3f, eyeR * 0.11f, DARK,
                    Mat.trs(ex, eyeY + eyeR * 1.3f, eyeZ + eyeR * 0.1f, rz = -s * 0.22f), 3)
            if (cheeks)
                Prims.sphere(c.trim, eyeR * 0.32f, BLUSH, Mat.trs(s * (eyeX + eyeR * 0.55f), eyeY - eyeR * 1.05f, mouthZ - 0.02f, sz = 0.35f), 12, 8)
        }
        val tube = (mouthW * 0.2f).coerceIn(0.05f, 0.075f)
        if (tongue) {
            // dark open mouth (lower half disc) with a pink tongue, framed by the smile line
            Prims.cylinder(c.trim, mouthW * 0.92f, mouthW * 0.92f, 0.04f, DARK, Mat.mul(Mat.trs(0f, mouthY + mouthW, mouthZ - 0.01f, rx = HALF_PI), Mat.trs(ry = -HALF_PI)), 16, true, PI_F)
            Prims.sphere(c.trim, mouthW * 0.42f, TONGUE, Mat.trs(0f, mouthY + mouthW * 0.32f, mouthZ, sx = 1.1f, sz = 0.35f), 12, 8)
            Prims.box(c.trim, mouthW * 1.9f, tube * 1.6f, 0.05f, DARK, Mat.trs(0f, mouthY + mouthW, mouthZ + 0.01f))
            Prims.torus(c.trim, mouthW, tube, DARK, Mat.trs(0f, mouthY + mouthW, mouthZ, rz = PI_F), 6, 18, PI_F)
        } else {
            Prims.torus(c.trim, mouthW, tube, DARK, Mat.trs(0f, mouthY + mouthW, mouthZ, rz = PI_F + 0.6f), 6, 16, PI_F - 1.2f)
        }
    }

    /** A boost exhaust pipe pointing backwards; the game attaches the boost flame here. */
    fun exhaust(c: CarParts, x: Float, y: Float, z: Float, r: Float = 0.14f) {
        c.exhausts.add(floatArrayOf(x, y, z))
        Prims.cylinder(c.trim, r, r * 1.15f, 0.35f, CHROME, Mat.trs(x, y, z + 0.1f, rx = HALF_PI), 12)
        Prims.cylinder(c.trim, r * 0.7f, r * 0.7f, 0.02f, BLACK, Mat.trs(x, y, z - 0.08f, rx = HALF_PI), 10)
    }

    fun tailLights(c: CarParts, x: Float, y: Float, z: Float, w: Float = 0.4f, h: Float = 0.18f) {
        for (s in floatArrayOf(-1f, 1f)) Prims.roundBox(c.lights, w, h, 0.08f, min(w, h) * 0.35f, TAIL, Mat.trs(s * x, y, z), 3)
    }

    /** A white racing-number disc on both doors. */
    fun doorDiscs(c: CarParts, x: Float, y: Float, z: Float, r: Float) {
        for (s in floatArrayOf(-1f, 1f)) Prims.cylinder(c.trim, r, r, 0.04f, WHITE, Mat.trs(s * x, y, z, rz = HALF_PI), 18)
    }
}
