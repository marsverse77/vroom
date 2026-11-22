package com.vroombuddies.game.render

import com.google.android.filament.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.sqrt

/** GPU copy of a [MeshData]. */
class GpuMesh(val vb: VertexBuffer, val ib: IndexBuffer, val indexCount: Int, val box: Box) {
    fun destroy(engine: Engine) { engine.destroyVertexBuffer(vb); engine.destroyIndexBuffer(ib) }
}

object Gpu {
    fun floats(n: Int): FloatBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    /** Tangent-frame quaternion from a normal (Filament's TANGENTS attribute). */
    fun tangentQuat(nx: Float, ny: Float, nz: Float, out: FloatArray, o: Int) {
        // pick a tangent perpendicular to n
        var tx: Float; var ty: Float; var tz: Float
        if (abs(ny) < 0.9f) { tx = nz; ty = 0f; tz = -nx } else { tx = 0f; ty = -nz; tz = ny }
        var l = sqrt(tx * tx + ty * ty + tz * tz); tx /= l; ty /= l; tz /= l
        // bitangent = n x t
        val bx = ny * tz - nz * ty; val by = nz * tx - nx * tz; val bz = nx * ty - ny * tx
        // rotation matrix columns (t, b, n) -> quaternion
        val m00 = tx; val m10 = ty; val m20 = tz
        val m01 = bx; val m11 = by; val m21 = bz
        val m02 = nx; val m12 = ny; val m22 = nz
        val tr = m00 + m11 + m22
        var qx: Float; var qy: Float; var qz: Float; var qw: Float
        if (tr > 0) {
            val s = sqrt(tr + 1f) * 2f
            qw = 0.25f * s; qx = (m21 - m12) / s; qy = (m02 - m20) / s; qz = (m10 - m01) / s
        } else if (m00 > m11 && m00 > m22) {
            val s = sqrt(1f + m00 - m11 - m22) * 2f
            qw = (m21 - m12) / s; qx = 0.25f * s; qy = (m01 + m10) / s; qz = (m02 + m20) / s
        } else if (m11 > m22) {
            val s = sqrt(1f + m11 - m00 - m22) * 2f
            qw = (m02 - m20) / s; qx = (m01 + m10) / s; qy = 0.25f * s; qz = (m12 + m21) / s
        } else {
            val s = sqrt(1f + m22 - m00 - m11) * 2f
            qw = (m10 - m01) / s; qx = (m02 + m20) / s; qy = (m12 + m21) / s; qz = 0.25f * s
        }
        // Filament stores bitangent handedness in the sign of w: keep w positive (right-handed)
        if (qw < 0) { qx = -qx; qy = -qy; qz = -qz; qw = -qw }
        if (qw < 1e-5f) { qw = 1e-5f; l = sqrt(qx * qx + qy * qy + qz * qz + qw * qw); qx /= l; qy /= l; qz /= l; qw /= l }
        out[o] = qx; out[o + 1] = qy; out[o + 2] = qz; out[o + 3] = qw
    }

    fun upload(engine: Engine, m: MeshData): GpuMesh {
        val n = m.vCount
        val q = FloatArray(n * 4)
        for (i in 0 until n) tangentQuat(m.nrm[i * 3], m.nrm[i * 3 + 1], m.nrm[i * 3 + 2], q, i * 4)
        val vb = VertexBuffer.Builder()
            .vertexCount(n).bufferCount(4)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 2, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .attribute(VertexBuffer.VertexAttribute.UV0, 3, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .build(engine)
        vb.setBufferAt(engine, 0, floats(n * 3).put(m.pos, 0, n * 3).flip())
        vb.setBufferAt(engine, 1, floats(n * 4).put(q, 0, n * 4).flip())
        vb.setBufferAt(engine, 2, floats(n * 4).put(m.col, 0, n * 4).flip())
        vb.setBufferAt(engine, 3, floats(n * 2).put(m.uv, 0, n * 2).flip())
        val ib = IndexBuffer.Builder().indexCount(m.iCount).bufferType(IndexBuffer.Builder.IndexType.UINT).build(engine)
        val ibuf = ByteBuffer.allocateDirect(m.iCount * 4).order(ByteOrder.nativeOrder())
        ibuf.asIntBuffer().put(m.idx, 0, m.iCount)
        ib.setBuffer(engine, ibuf)
        val b = m.bounds()
        val box = Box((b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2, (b[3] - b[0]) / 2 + 0.5f, (b[4] - b[1]) / 2 + 0.5f, (b[5] - b[2]) / 2 + 0.5f)
        return GpuMesh(vb, ib, m.iCount, box)
    }

    /** Make an entity that draws [mesh] with [mi]. */
    fun renderable(engine: Engine, mesh: GpuMesh, mi: MaterialInstance, castShadows: Boolean = true, receiveShadows: Boolean = true, priority: Int = 4, culling: Boolean = true): Int {
        val e = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(mesh.box)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, mesh.vb, mesh.ib, 0, mesh.indexCount)
            .material(0, mi)
            .castShadows(castShadows)
            .receiveShadows(receiveShadows)
            .priority(priority)
            .culling(culling)
            .build(engine, e)
        return e
    }
}
