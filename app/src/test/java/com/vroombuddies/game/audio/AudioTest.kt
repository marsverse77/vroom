package com.vroombuddies.game.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Renders the synth offline. Checks every song and effect is finite, not silent and never clips, and that
 * mixing is far faster than real time. Set -Dvroom.audioPreview=<dir> (env VROOM_AUDIO_PREVIEW) to also
 * write WAV previews.
 */
class AudioTest {
    private val previewDir = (System.getProperty("vroom.audioPreview") ?: System.getenv("VROOM_AUDIO_PREVIEW"))?.let { File(it).apply { mkdirs() } }

    private fun stats(x: FloatArray): Pair<Float, Float> {
        var peak = 0f; var sum = 0.0
        for (v in x) { assertTrue("NaN/inf in audio", v.isFinite()); peak = maxOf(peak, abs(v)); sum += v * v }
        return peak to sqrt(sum / x.size).toFloat()
    }

    private fun wav(name: String, x: FloatArray) {
        val dir = previewDir ?: return
        DataOutputStream(FileOutputStream(File(dir, "$name.wav"))).use { o ->
            fun i32(v: Int) { o.write(v and 255); o.write((v shr 8) and 255); o.write((v shr 16) and 255); o.write((v shr 24) and 255) }
            fun i16(v: Int) { o.write(v and 255); o.write((v shr 8) and 255) }
            o.writeBytes("RIFF"); i32(36 + x.size * 2); o.writeBytes("WAVEfmt "); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
            o.writeBytes("data"); i32(x.size * 2)
            for (v in x) i16((v.coerceIn(-1f, 1f) * 32000f).toInt())
        }
    }

    private fun renderSong(st: Style, seconds: Float): FloatArray {
        val p = MusicPlayer(); p.change(Composer.compose(st), 0.01f)
        return FloatArray((seconds * RATE).toInt()) { p.next() * 0.5f }
    }

    @Test fun everySongPlaysCleanly() {
        val names = listOf("Sunny Meadow", "Desert Canyon", "Space Station", "Neon City", "Snowy Peaks", "Sunny Beach", "Jungle Jam",
            "Volcano Run", "Sweet Treats", "Moon Base", "Autumn Woods", "Rainbow Sky", "Frozen Lake", "Pirate Cove", "Dino Valley",
            "Toy Town", "Sunset Highway", "Crystal Canyon", "Bubble Reef", "Galaxy Grand Prix")
        val all = names.map { it to Songs.forTheme(it) } + listOf("menu" to Songs.menu, "victory" to Songs.victory)
        assertTrue("each track has its own song", names.map { Songs.forTheme(it) }.toSet().size == names.size)
        for ((name, st) in all) {
            val song = Composer.compose(st)
            assertTrue("$name has a melody", song.events.count { it.inst == st.lead } > 40)
            val x = renderSong(st, if (previewDir != null) 26f else 6f)
            val (peak, rms) = stats(x)
            assertTrue("$name too quiet ($rms)", rms > 0.02f)
            assertTrue("$name too loud ($peak)", peak < 1.6f) // pre-limiter level; the mixer soft-clips
            wav("music-" + name.lowercase().replace(' ', '-'), x)
        }
    }

    @Test fun effectsAndEngine() {
        for (n in listOf("click", "pop", "beep", "go", "boost", "pad", "whoosh", "land", "bump", "scrape", "thud", "whoops", "firework", "win", "finish")) {
            val m = Mixer(); m.play(n, 12f)
            val x = FloatArray(RATE * 3); m.render(x)
            val (peak, rms) = stats(x)
            assertTrue("$n silent", rms > 0.002f)
            assertTrue("$n clips", peak <= 0.93f)
            wav("sfx-$n", x)
        }
        // a lap in miniature: idle, accelerate through the gears, slide, jump, boost
        val m = Mixer(); m.engine.on = true
        val out = FloatArray(RATE * 12); val blk = FloatArray(441)
        for (b in 0 until out.size / blk.size) {
            val t = b * blk.size.toFloat() / RATE
            m.engine.speed = when { t < 1f -> 0f; t < 7f -> (t - 1f) * 8f; else -> 48f + (if (t > 9f) 25f else 0f) }
            m.engine.slide = if (t in 5f..6f) 8f else 0f
            m.engine.grounded = t !in 7.5f..8.5f
            m.engine.boost = t > 9f
            if (b == 900) m.play("boost", 1f)
            m.render(blk); System.arraycopy(blk, 0, out, b * blk.size, blk.size)
        }
        val (_, rms) = stats(out)
        assertTrue("engine silent", rms > 0.02f)
        wav("engine-drive", out)
    }

    @Test fun raceFlowAndSpeed() {
        val m = Mixer(); m.engine.on = true; m.engine.speed = 40f
        m.race(Songs.forTheme("Neon City")); m.play("go", 1f)
        val buf = FloatArray(512)
        val seconds = 30
        val t0 = System.nanoTime()
        val all = FloatArray(seconds * RATE / 512 * 512)
        for (b in 0 until seconds * RATE / 512) {
            if (b == 400) m.play("boost", 1f)
            if (b == 2000) m.play("win", 1f)
            m.render(buf); System.arraycopy(buf, 0, all, b * 512, 512)
        }
        val secs = (System.nanoTime() - t0) / 1e9
        stats(all)
        wav("race-flow", all)
        assertTrue("mixing too slow: ${seconds / secs}x real time", seconds / secs > 20)
    }
}
