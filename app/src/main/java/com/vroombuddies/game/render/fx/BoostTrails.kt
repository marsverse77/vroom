package com.vroombuddies.game.render.fx

import com.google.android.filament.*
import com.vroombuddies.game.render.Gpu
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Glowing light trails behind boosting cars: one ribbon per exhaust, coloured from the car's paint and bright
 * enough to bloom. The ribbon follows where the exhaust actually was, so it swooshes around corners and over jumps.
 * Uses the additive particle material, which already gives soft edges across the ribbon.
 */
class BoostTrails(private val engine: Engine, private val scene: Scene, material: Material, cars: Int, private val perCar: Int = 2) {
    private val n = 22                     // points per ribbon
    private val trails = cars * perCar
    private val px = FloatArray(trails * n); private val py = FloatArray(trails * n); private val pz = FloatArray(trails * n)
    private val heat = FloatArray(trails * n)
    private val col = FloatArray(cars * 3)
    private val segs = trails * (n - 1)
    private val mi = material.createInstance()
    private val vb: VertexBuffer
    private val ib: IndexBuffer
    val entity: Int
    private val posBufs = Array(3) { Gpu.floats(segs * 4 * 3) }
    private val colBufs = Array(3) { Gpu.floats(segs * 4 * 4) }
    private var ring = 0
    private var started = BooleanArray(trails)

    init {
        vb = VertexBuffer.Builder().vertexCount(segs * 4).bufferCount(3)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .build(engine)
        // u runs across the ribbon (soft edges), v stays in the middle so only u fades
        val uv = Gpu.floats(segs * 4 * 2)
        repeat(segs) { uv.put(0f).put(0.5f).put(1f).put(0.5f).put(1f).put(0.5f).put(0f).put(0.5f) }
        uv.flip(); vb.setBufferAt(engine, 2, uv)
        ib = IndexBuffer.Builder().indexCount(segs * 6).bufferType(IndexBuffer.Builder.IndexType.UINT).build(engine)
        val ibuf = ByteBuffer.allocateDirect(segs * 6 * 4).order(ByteOrder.nativeOrder())
        val ii = ibuf.asIntBuffer()
        repeat(segs) { val b = it * 4; ii.put(b).put(b + 1).put(b + 2).put(b).put(b + 2).put(b + 3) }
        ib.setBuffer(engine, ibuf)
        entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, 1e5f, 1e5f, 1e5f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, segs * 6)
            .material(0, mi).castShadows(false).receiveShadows(false).culling(false).priority(6)
            .build(engine, entity)
        scene.addEntity(entity)
    }

    /** Linear RGB of a car's trail (HDR values above 1 make it bloom). */
    fun setColor(car: Int, r: Float, g: Float, b: Float) { col[car * 3] = r; col[car * 3 + 1] = g; col[car * 3 + 2] = b }

    /** Feed the newest exhaust position of one ribbon. [h] = 0..1 how hot (boosting) it is right now. */
    fun push(car: Int, k: Int, x: Float, y: Float, z: Float, h: Float) {
        val t = car * perCar + k
        val o = t * n
        if (!started[t]) { for (i in 0 until n) { px[o + i] = x; py[o + i] = y; pz[o + i] = z; heat[o + i] = 0f }; started[t] = true }
        // shift back one slot, newest at index 0
        for (i in n - 1 downTo 1) { px[o + i] = px[o + i - 1]; py[o + i] = py[o + i - 1]; pz[o + i] = pz[o + i - 1]; heat[o + i] = heat[o + i - 1] }
        px[o] = x; py[o] = y; pz[o] = z; heat[o] = h
    }

    /** A car teleported (reset): don't draw a streak from its old place. */
    fun cut(car: Int) { for (k in 0 until perCar) started[car * perCar + k] = false }

    private var visibleSegments = -1

    fun update(camX: Float, camY: Float, camZ: Float) {
        val pos = posBufs[ring]; val c = colBufs[ring]; ring = (ring + 1) % 3
        pos.clear(); c.clear()
        var active = 0
        for (t in 0 until trails) {
            val o = t * n; val car = t / perCar
            val cr = col[car * 3]; val cg = col[car * 3 + 1]; val cb = col[car * 3 + 2]
            for (i in 0 until n - 1) {
                val a = o + i; val b = a + 1
                val ha = heat[a] * (1f - i.toFloat() / (n - 1)); val hb = heat[b] * (1f - (i + 1f) / (n - 1))
                if (ha <= 0.001f && hb <= 0.001f) {

                    continue
                }
                active++
                // width across the segment, facing the camera
                val dx = px[b] - px[a]; val dy = py[b] - py[a]; val dz = pz[b] - pz[a]
                val tx = camX - px[a]; val ty = camY - py[a]; val tz = camZ - pz[a]
                var sx = dy * tz - dz * ty; var sy = dz * tx - dx * tz; var sz = dx * ty - dy * tx
                val sl = sqrt(sx * sx + sy * sy + sz * sz)
                if (sl < 1e-5f) { sx = 0f; sy = 1f; sz = 0f } else { sx /= sl; sy /= sl; sz /= sl }
                val wa = 0.12f + 0.32f * ha; val wb = 0.12f + 0.32f * hb
                pos.put(px[a] - sx * wa).put(py[a] - sy * wa).put(pz[a] - sz * wa)
                pos.put(px[a] + sx * wa).put(py[a] + sy * wa).put(pz[a] + sz * wa)
                pos.put(px[b] + sx * wb).put(py[b] + sy * wb).put(pz[b] + sz * wb)
                pos.put(px[b] - sx * wb).put(py[b] - sy * wb).put(pz[b] - sz * wb)
                // hot white core near the car, paint colour further back
                val wA = ha * ha; val wB = hb * hb
                c.put(cr + wA * 2f).put(cg + wA * 2f).put(cb + wA * 2f).put(ha)
                c.put(cr + wA * 2f).put(cg + wA * 2f).put(cb + wA * 2f).put(ha)
                c.put(cr + wB * 2f).put(cg + wB * 2f).put(cb + wB * 2f).put(hb)
                c.put(cr + wB * 2f).put(cg + wB * 2f).put(cb + wB * 2f).put(hb)
            }
        }
        if (active != visibleSegments) {
            val rm = engine.renderableManager
            val instance = rm.getInstance(entity)
            rm.setLayerMask(instance, 0xff, if (active == 0) 0 else 0xff)
            if (active > 0) rm.setGeometryAt(instance, 0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, active * 6)
            visibleSegments = active
        }
        if (active == 0) return
        pos.flip(); c.flip()
        vb.setBufferAt(engine, 0, pos)
        vb.setBufferAt(engine, 1, c)
    }

    fun destroy() {
        scene.remove(entity); engine.destroyEntity(entity)
        engine.destroyVertexBuffer(vb); engine.destroyIndexBuffer(ib); engine.destroyMaterialInstance(mi)
    }
}
