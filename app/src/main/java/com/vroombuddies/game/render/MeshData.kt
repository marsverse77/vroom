package com.vroombuddies.game.render

import kotlin.math.*

/**
 * A growable CPU-side triangle mesh with position, normal, colour (rgba; alpha = wind sway weight) and uv.
 * Everything in the game (cars, scenery, track) is generated from code into these, then uploaded once.
 */
class MeshData {
    var pos = FloatArray(3 * 1024); var nrm = FloatArray(3 * 1024); var col = FloatArray(4 * 1024); var uv = FloatArray(2 * 1024)
    var idx = IntArray(3 * 1024)
    var vCount = 0; var iCount = 0

    private fun growV(n: Int) {
        if (vCount + n <= pos.size / 3) return
        val cap = max(pos.size / 3 * 2, vCount + n)
        pos = pos.copyOf(cap * 3); nrm = nrm.copyOf(cap * 3); col = col.copyOf(cap * 4); uv = uv.copyOf(cap * 2)
    }
    private fun growI(n: Int) { if (iCount + n > idx.size) idx = idx.copyOf(max(idx.size * 2, iCount + n)) }

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, c: Rgba, u: Float = 0f, v: Float = 0f): Int {
        growV(1)
        val i = vCount++
        pos[i * 3] = x; pos[i * 3 + 1] = y; pos[i * 3 + 2] = z
        nrm[i * 3] = nx; nrm[i * 3 + 1] = ny; nrm[i * 3 + 2] = nz
        col[i * 4] = c.r; col[i * 4 + 1] = c.g; col[i * 4 + 2] = c.b; col[i * 4 + 3] = c.a
        uv[i * 2] = u; uv[i * 2 + 1] = v
        return i
    }
    fun tri(a: Int, b: Int, c: Int) { growI(3); idx[iCount++] = a; idx[iCount++] = b; idx[iCount++] = c }
    fun quad(a: Int, b: Int, c: Int, d: Int) { tri(a, b, c); tri(a, c, d) }

    /** Quad that always faces the way vertex [a]'s normal points (fixes winding automatically). */
    fun quadFacing(a: Int, b: Int, c: Int, d: Int) {
        val ux = pos[b * 3] - pos[a * 3]; val uy = pos[b * 3 + 1] - pos[a * 3 + 1]; val uz = pos[b * 3 + 2] - pos[a * 3 + 2]
        val vx = pos[c * 3] - pos[a * 3]; val vy = pos[c * 3 + 1] - pos[a * 3 + 1]; val vz = pos[c * 3 + 2] - pos[a * 3 + 2]
        val fx = uy * vz - uz * vy; val fy = uz * vx - ux * vz; val fz = ux * vy - uy * vx
        if (fx * nrm[a * 3] + fy * nrm[a * 3 + 1] + fz * nrm[a * 3 + 2] >= 0) quad(a, b, c, d) else quad(a, d, c, b)
    }

    /** Append another mesh transformed by [m] (column-major 4x4), recoloured if [tint] is given. */
    fun append(o: MeshData, m: FloatArray? = null, tint: Rgba? = null) {
        val base = vCount
        growV(o.vCount)
        for (i in 0 until o.vCount) {
            var x = o.pos[i * 3]; var y = o.pos[i * 3 + 1]; var z = o.pos[i * 3 + 2]
            var nx = o.nrm[i * 3]; var ny = o.nrm[i * 3 + 1]; var nz = o.nrm[i * 3 + 2]
            if (m != null) {
                val tx = m[0] * x + m[4] * y + m[8] * z + m[12]
                val ty = m[1] * x + m[5] * y + m[9] * z + m[13]
                val tz = m[2] * x + m[6] * y + m[10] * z + m[14]
                x = tx; y = ty; z = tz
                // normals: assumes uniform-ish scale; renormalise
                val qx = m[0] * nx + m[4] * ny + m[8] * nz
                val qy = m[1] * nx + m[5] * ny + m[9] * nz
                val qz = m[2] * nx + m[6] * ny + m[10] * nz
                val l = sqrt(qx * qx + qy * qy + qz * qz).coerceAtLeast(1e-6f)
                nx = qx / l; ny = qy / l; nz = qz / l
            }
            val j = vCount++
            pos[j * 3] = x; pos[j * 3 + 1] = y; pos[j * 3 + 2] = z
            nrm[j * 3] = nx; nrm[j * 3 + 1] = ny; nrm[j * 3 + 2] = nz
            if (tint != null) {
                col[j * 4] = o.col[i * 4] * tint.r; col[j * 4 + 1] = o.col[i * 4 + 1] * tint.g; col[j * 4 + 2] = o.col[i * 4 + 2] * tint.b
            } else { col[j * 4] = o.col[i * 4]; col[j * 4 + 1] = o.col[i * 4 + 1]; col[j * 4 + 2] = o.col[i * 4 + 2] }
            col[j * 4 + 3] = o.col[i * 4 + 3]
            uv[j * 2] = o.uv[i * 2]; uv[j * 2 + 1] = o.uv[i * 2 + 1]
        }
        growI(o.iCount)
        for (i in 0 until o.iCount) idx[iCount++] = o.idx[i] + base
    }

    fun bounds(): FloatArray {
        if (vCount == 0) return floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
        var a = Float.MAX_VALUE; var b = a; var c = a; var d = -a; var e = -a; var f = -a
        for (i in 0 until vCount) {
            a = min(a, pos[i * 3]); b = min(b, pos[i * 3 + 1]); c = min(c, pos[i * 3 + 2])
            d = max(d, pos[i * 3]); e = max(e, pos[i * 3 + 1]); f = max(f, pos[i * 3 + 2])
        }
        return floatArrayOf(a, b, c, d, e, f)
    }

    /** Set every vertex alpha (wind sway weight) from height: 0 at the base, up to [max] at the top. */
    fun windByHeight(maxW: Float) {
        val b = bounds(); val h = max(0.01f, b[4] - b[1])
        for (i in 0 until vCount) col[i * 4 + 3] = ((pos[i * 3 + 1] - b[1]) / h).let { it * it } * maxW
    }
}

data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float = 0f) {
    companion object {
        /** From 0xRRGGBB in sRGB; converted to linear so lighting is physically right. */
        fun hex(c: Int, a: Float = 0f) = Rgba(lin((c shr 16 and 255) / 255f), lin((c shr 8 and 255) / 255f), lin((c and 255) / 255f), a)
        fun lin(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        val WHITE = Rgba(1f, 1f, 1f)
    }
    fun mul(k: Float) = Rgba(r * k, g * k, b * k, a)
}

/** Column-major 4x4 matrix helpers (matches Filament's float[16] layout). */
object Mat {
    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    /** translate * rotY(ry) * rotX(rx) * rotZ(rz) * scale */
    fun trs(tx: Float = 0f, ty: Float = 0f, tz: Float = 0f, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f): FloatArray =
        trsInto(FloatArray(16), tx, ty, tz, rx, ry, rz, sx, sy, sz)

    /** [trs] written into [out]: no allocation, for per-frame use. */
    fun trsInto(out: FloatArray, tx: Float = 0f, ty: Float = 0f, tz: Float = 0f, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f): FloatArray {
        val cx = cos(rx); val sxr = sin(rx); val cy = cos(ry); val syr = sin(ry); val cz = cos(rz); val szr = sin(rz)
        // R = Ry * Rx * Rz
        val r00 = cy * cz + syr * sxr * szr; val r01 = -cy * szr + syr * sxr * cz; val r02 = syr * cx
        val r10 = cx * szr; val r11 = cx * cz; val r12 = -sxr
        val r20 = -syr * cz + cy * sxr * szr; val r21 = syr * szr + cy * sxr * cz; val r22 = cy * cx
        out[0] = r00 * sx; out[1] = r10 * sx; out[2] = r20 * sx; out[3] = 0f
        out[4] = r01 * sy; out[5] = r11 * sy; out[6] = r21 * sy; out[7] = 0f
        out[8] = r02 * sz; out[9] = r12 * sz; out[10] = r22 * sz; out[11] = 0f
        out[12] = tx; out[13] = ty; out[14] = tz; out[15] = 1f
        return out
    }

    fun mul(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        for (c in 0 until 4) for (r in 0 until 4) {
            out[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3]
        }
        return out
    }

    /** Rigid transform from an orthonormal basis (right, up, forward) and a position. Model +Z faces [f]. */
    fun basis(out: FloatArray, rx: Float, ry: Float, rz: Float, ux: Float, uy: Float, uz: Float, fx: Float, fy: Float, fz: Float, px: Float, py: Float, pz: Float): FloatArray {
        // model +X maps to -right so that +Z forward and +Y up stay right-handed
        out[0] = -rx; out[1] = -ry; out[2] = -rz; out[3] = 0f
        out[4] = ux; out[5] = uy; out[6] = uz; out[7] = 0f
        out[8] = fx; out[9] = fy; out[10] = fz; out[11] = 0f
        out[12] = px; out[13] = py; out[14] = pz; out[15] = 1f
        return out
    }

    fun fromQuat(out: FloatArray, qx: Float, qy: Float, qz: Float, qw: Float, px: Float, py: Float, pz: Float, s: Float = 1f): FloatArray {
        val xx = qx * qx; val yy = qy * qy; val zz = qz * qz; val xy = qx * qy; val xz = qx * qz; val yz = qy * qz; val wx = qw * qx; val wy = qw * qy; val wz = qw * qz
        out[0] = (1 - 2 * (yy + zz)) * s; out[1] = 2 * (xy + wz) * s; out[2] = 2 * (xz - wy) * s; out[3] = 0f
        out[4] = 2 * (xy - wz) * s; out[5] = (1 - 2 * (xx + zz)) * s; out[6] = 2 * (yz + wx) * s; out[7] = 0f
        out[8] = 2 * (xz + wy) * s; out[9] = 2 * (yz - wx) * s; out[10] = (1 - 2 * (xx + yy)) * s; out[11] = 0f
        out[12] = px; out[13] = py; out[14] = pz; out[15] = 1f
        return out
    }
}
