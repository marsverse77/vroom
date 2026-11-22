package com.vroombuddies.game.render

import android.graphics.*
import android.graphics.Paint
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

/** Procedural textures painted with Android's Canvas, uploaded with full mipmaps. */
object Textures {
    /** Anisotropy applied to every sampler (graphics setting). */
    var anisotropy = 8f
    /** Texture size multiplier from the Texture Quality setting (0.5 low, 1 high). */
    var quality = 1f

    fun sampler(repeat: Boolean = true): TextureSampler {
        val wrap = if (repeat) TextureSampler.WrapMode.REPEAT else TextureSampler.WrapMode.CLAMP_TO_EDGE
        return TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, wrap).also { it.anisotropy = anisotropy }
    }

    fun upload(engine: Engine, bmp: Bitmap, srgb: Boolean = true): Texture {
        val w = bmp.width; val h = bmp.height
        val levels = (ln(max(w, h).toDouble()) / ln(2.0)).toInt() + 1
        val tex = Texture.Builder().width(w).height(h).levels(levels)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(if (srgb) Texture.InternalFormat.SRGB8_A8 else Texture.InternalFormat.RGBA8)
            .build(engine)
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        bmp.copyPixelsToBuffer(buf); buf.flip()
        tex.setImage(engine, 0, Texture.PixelBufferDescriptor(buf, Texture.Format.RGBA, Texture.Type.UBYTE))
        tex.generateMipmaps(engine)
        bmp.recycle()
        return tex
    }

    private fun size(n: Int) = max(64, (n * quality).toInt())

    private fun noise(c: Canvas, w: Int, h: Int, base: Int, spots: Int, var1: Int, alpha: Int, r0: Float, r1: Float, rnd: Random) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until spots) {
            p.color = if (rnd.nextBoolean()) var1 else base
            p.alpha = rnd.nextInt(alpha)
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h; val r = r0 + rnd.nextFloat() * (r1 - r0)
            // draw wrapped so the texture tiles seamlessly
            for (ox in intArrayOf(-w, 0, w)) for (oy in intArrayOf(-h, 0, h)) c.drawCircle(x + ox, y + oy, r, p)
        }
    }

    /** Road: asphalt, coloured kerbs on both edges, dashed centre line. U across (0..1), V along. */
    fun road(engine: Engine, asphalt: Int, kerbA: Int, kerbB: Int): Texture {
        val w = size(512); val h = size(512)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val rnd = Random(7)
        c.drawColor(asphalt or 0xff000000.toInt())
        noise(c, w, h, 0xff000000.toInt(), 2500, 0xffffffff.toInt(), 22, 0.6f, 2.2f, rnd)
        val p = Paint()
        val kerbW = w * 0.06f
        val stripes = 8
        for (i in 0 until stripes) {
            p.color = if (i % 2 == 0) kerbA or 0xff000000.toInt() else kerbB or 0xff000000.toInt()
            val y0 = i * h / stripes.toFloat(); val y1 = (i + 1) * h / stripes.toFloat()
            c.drawRect(0f, y0, kerbW, y1, p); c.drawRect(w - kerbW, y0, w.toFloat(), y1, p)
        }
        p.color = 0xffffffff.toInt()
        c.drawRect(kerbW + w * 0.01f, 0f, kerbW + w * 0.025f, h.toFloat(), p)
        c.drawRect(w - kerbW - w * 0.025f, 0f, w - kerbW - w * 0.01f, h.toFloat(), p)
        // dashed centre line + lane lines
        for (lane in floatArrayOf(0.5f)) {
            val x = w * lane
            c.drawRect(x - w * 0.012f, h * 0.1f, x + w * 0.012f, h * 0.45f, p)
            c.drawRect(x - w * 0.012f, h * 0.6f, x + w * 0.012f, h * 0.95f, p)
        }
        return upload(engine, bmp)
    }

    /** Wall: bold alternating colour blocks with a soft top stripe. Alpha = glow mask for neon walls. */
    fun wall(engine: Engine, a: Int, b: Int): Texture {
        val w = size(256); val h = size(128)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = a or 0xff000000.toInt(); c.drawRect(0f, 0f, w / 2f, h.toFloat(), p)
        p.color = b or 0xff000000.toInt(); c.drawRect(w / 2f, 0f, w.toFloat(), h.toFloat(), p)
        p.color = 0x33000000; c.drawRect(0f, h * 0.85f, w.toFloat(), h.toFloat(), p)
        p.color = 0x55ffffff; c.drawRect(0f, 0f, w.toFloat(), h * 0.12f, p)
        return upload(engine, bmp)
    }

    /** Ground: soft blotchy two-tone grass/sand/snow. */
    fun ground(engine: Engine, a: Int, b: Int, seed: Int): Texture {
        val w = size(512); val h = size(512)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val rnd = Random(seed)
        c.drawColor(a or 0xff000000.toInt())
        noise(c, w, h, b or 0xff000000.toInt(), 400, a or 0xff000000.toInt(), 160, 8f, 40f, rnd)
        noise(c, w, h, 0xff000000.toInt(), 1500, 0xffffffff.toInt(), 25, 1f, 3f, rnd)
        return upload(engine, bmp)
    }

    /** Ramp: bright stripes and arrows. */
    fun ramp(engine: Engine): Texture {
        val w = size(256); val h = size(256)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(0xffff8a00.toInt())
        p.color = 0xffffe14d.toInt()
        val path = Path()
        for (k in 0 until 2) {
            val y = k * h / 2f
            path.reset(); path.moveTo(w * 0.2f, y + h * 0.4f); path.lineTo(w * 0.5f, y + h * 0.1f); path.lineTo(w * 0.8f, y + h * 0.4f)
            path.lineTo(w * 0.8f, y + h * 0.48f); path.lineTo(w * 0.5f, y + h * 0.2f); path.lineTo(w * 0.2f, y + h * 0.48f); path.close()
            c.drawPath(path, p)
        }
        return upload(engine, bmp)
    }

    /** Boost pad: glowing chevrons on transparent background (scrolls in the shader). */
    fun chevrons(engine: Engine): Texture {
        val w = 128; val h = 128
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x40ffffff
        c.drawRect(4f, 0f, w - 4f, h.toFloat(), p)
        p.color = 0xffffffff.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 14f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
        val path = Path(); path.moveTo(w * 0.18f, h * 0.75f); path.lineTo(w * 0.5f, h * 0.35f); path.lineTo(w * 0.82f, h * 0.75f)
        c.drawPath(path, p)
        return upload(engine, bmp)
    }

    /** Checkered start/finish line. */
    fun checker(engine: Engine): Texture {
        val n = 128
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint()
        for (i in 0 until 8) for (j in 0 until 8) { p.color = if ((i + j) % 2 == 0) 0xffffffff.toInt() else 0xff111111.toInt(); c.drawRect(i * 16f, j * 16f, i * 16f + 16, j * 16f + 16, p) }
        return upload(engine, bmp)
    }
}
