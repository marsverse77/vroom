package com.vroombuddies.game.render

import com.google.android.filament.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*
import kotlin.random.Random

/**
 * CPU particle system drawn as camera-facing (or velocity-stretched) quads in one dynamic mesh.
 * Two instances are used: additive (sparks, boost glow, speed streaks, fireworks) and alpha (dust, smoke, confetti).
 */
class Particles(private val engine: Engine, scene: Scene, material: Material, private val max: Int) {
    private val px = FloatArray(max); private val py = FloatArray(max); private val pz = FloatArray(max)
    private val vx = FloatArray(max); private val vy = FloatArray(max); private val vz = FloatArray(max)
    private val life = FloatArray(max); private val maxLife = FloatArray(max)
    private val size0 = FloatArray(max); private val size1 = FloatArray(max)
    private val cr = FloatArray(max); private val cg = FloatArray(max); private val cb = FloatArray(max); private val ca = FloatArray(max)
    private val grav = FloatArray(max); private val drag = FloatArray(max); private val stretch = FloatArray(max)
    private var next = 0
    var scale = 1f   // effects quality: fraction of particles actually spawned
    private val rnd = Random(3)

    private val vb: VertexBuffer
    private val ib: IndexBuffer
    val entity: Int
    private val mi: MaterialInstance = material.createInstance()
    private val posBufs = Array(3) { floats(max * 4 * 3) }
    private val colBufs = Array(3) { floats(max * 4 * 4) }
    private var ring = 0

    private fun floats(n: Int): FloatBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    init {
        vb = VertexBuffer.Builder().vertexCount(max * 4).bufferCount(3)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .build(engine)
        val uv = floats(max * 4 * 2)
        repeat(max) { uv.put(0f).put(0f).put(1f).put(0f).put(1f).put(1f).put(0f).put(1f) }
        uv.flip(); vb.setBufferAt(engine, 2, uv)
        ib = IndexBuffer.Builder().indexCount(max * 6).bufferType(IndexBuffer.Builder.IndexType.UINT).build(engine)
        val ibuf = ByteBuffer.allocateDirect(max * 6 * 4).order(ByteOrder.nativeOrder())
        val ii = ibuf.asIntBuffer()
        repeat(max) { val b = it * 4; ii.put(b).put(b + 1).put(b + 2).put(b).put(b + 2).put(b + 3) }
        ib.setBuffer(engine, ibuf)
        entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, 1e5f, 1e5f, 1e5f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, max * 6)
            .material(0, mi).castShadows(false).receiveShadows(false).culling(false).priority(6)
            .build(engine, entity)
        scene.addEntity(entity)
    }

    fun spawn(x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, life: Float, s0: Float, s1: Float,
              r: Float, g: Float, b: Float, a: Float, gravity: Float = 0f, drag: Float = 0f, stretch: Float = 0f, force: Boolean = false) {
        if (!force && rnd.nextFloat() > scale) return
        val i = next; next = (next + 1) % max
        px[i] = x; py[i] = y; pz[i] = z; this.vx[i] = vx; this.vy[i] = vy; this.vz[i] = vz
        this.life[i] = life; maxLife[i] = life; size0[i] = s0; size1[i] = s1
        cr[i] = r; cg[i] = g; cb[i] = b; ca[i] = a; grav[i] = gravity; this.drag[i] = drag; this.stretch[i] = stretch
    }

    fun burst(n: Int, x: Float, y: Float, z: Float, speed: Float, up: Float, life: Float, s0: Float, s1: Float, r: Float, g: Float, b: Float, a: Float, gravity: Float, stretch: Float = 0f, baseVx: Float = 0f, baseVy: Float = 0f, baseVz: Float = 0f) {
        repeat(n) {
            val th = rnd.nextFloat() * 6.283f; val ph = acos(rnd.nextFloat() * 2 - 1)
            val sp = speed * (0.4f + rnd.nextFloat() * 0.6f)
            spawn(x, y, z, baseVx + sin(ph) * cos(th) * sp, baseVy + abs(cos(ph)) * sp * 0.5f + up * rnd.nextFloat(), baseVz + sin(ph) * sin(th) * sp,
                life * (0.6f + rnd.nextFloat() * 0.6f), s0, s1, r, g, b, a, gravity, 1.2f, stretch)
        }
    }

    /** Flat ring of particles flying outward along the ground (boost shockwave, landing dust). */
    fun ring(n: Int, x: Float, y: Float, z: Float, speed: Float, life: Float, s0: Float, s1: Float, r: Float, g: Float, b: Float, a: Float, up: Float = 0.5f, stretch: Float = 0f, gravity: Float = 0f) {
        val off = rnd.nextFloat() * 6.283f
        for (i in 0 until n) {
            val th = off + i * 6.283f / n
            val sp = speed * (0.85f + rnd.nextFloat() * 0.3f)
            spawn(x + cos(th) * 0.6f, y, z + sin(th) * 0.6f, cos(th) * sp, up * (0.5f + rnd.nextFloat()), sin(th) * sp,
                life * (0.8f + rnd.nextFloat() * 0.4f), s0, s1, r, g, b, a, gravity, 2.2f, stretch)
        }
    }

    fun clear() { for (i in 0 until max) life[i] = 0f }

    /** Simulate and rebuild the quads. cam* = camera position, right and up vectors. */
    fun update(dt: Float, camX: Float, camY: Float, camZ: Float, rX: Float, rY: Float, rZ: Float, uX: Float, uY: Float, uZ: Float) {
        val pos = posBufs[ring]; val col = colBufs[ring]; ring = (ring + 1) % 3
        pos.clear(); col.clear()
        for (i in 0 until max) {
            if (life[i] <= 0f) {
                repeat(4) { pos.put(0f).put(-1e4f).put(0f); col.put(0f).put(0f).put(0f).put(0f) }
                continue
            }
            life[i] -= dt
            val k = exp(-drag[i] * dt)
            vx[i] *= k; vy[i] = vy[i] * k - grav[i] * dt; vz[i] *= k
            px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * dt
            val t = 1f - max(0f, life[i]) / maxLife[i]
            val s = size0[i] + (size1[i] - size0[i]) * t
            val fade = (1f - t) * min(1f, t * 8f + 0.2f)
            val a = ca[i] * fade
            var ax: Float; var ay: Float; var az: Float; var bx: Float; var by: Float; var bz: Float
            if (stretch[i] > 0f) {
                // stretch along the velocity, width across it (facing the camera)
                val sp = sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]).coerceAtLeast(0.001f)
                val dx = vx[i] / sp; val dy = vy[i] / sp; val dz = vz[i] / sp
                val tx = camX - px[i]; val ty = camY - py[i]; val tz = camZ - pz[i]
                var sx = dy * tz - dz * ty; var sy = dz * tx - dx * tz; var sz = dx * ty - dy * tx
                val sl = sqrt(sx * sx + sy * sy + sz * sz).coerceAtLeast(0.001f); sx /= sl; sy /= sl; sz /= sl
                val len = s + sp * stretch[i]
                ax = sx * s * 0.5f; ay = sy * s * 0.5f; az = sz * s * 0.5f
                bx = dx * len * 0.5f; by = dy * len * 0.5f; bz = dz * len * 0.5f
            } else {
                ax = rX * s * 0.5f; ay = rY * s * 0.5f; az = rZ * s * 0.5f
                bx = uX * s * 0.5f; by = uY * s * 0.5f; bz = uZ * s * 0.5f
            }
            val x = px[i]; val y = py[i]; val z = pz[i]
            pos.put(x - ax - bx).put(y - ay - by).put(z - az - bz)
            pos.put(x + ax - bx).put(y + ay - by).put(z + az - bz)
            pos.put(x + ax + bx).put(y + ay + by).put(z + az + bz)
            pos.put(x - ax + bx).put(y - ay + by).put(z - az + bz)
            repeat(4) { col.put(cr[i]).put(cg[i]).put(cb[i]).put(a) }
        }
        pos.flip(); col.flip()
        vb.setBufferAt(engine, 0, pos)
        vb.setBufferAt(engine, 1, col)
    }

    fun destroy(scene: Scene) {
        scene.remove(entity); engine.destroyEntity(entity)
        engine.destroyVertexBuffer(vb); engine.destroyIndexBuffer(ib); engine.destroyMaterialInstance(mi)
    }
}
