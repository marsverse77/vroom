package com.vroombuddies.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.vroombuddies.game.render.SoundSink
import kotlin.math.*
import kotlin.random.Random

/**
 * Tiny real-time synthesizer: a cartoon engine hum that follows speed, plus generated effects
 * (boost whoosh, bumps, jump, countdown beeps, win fanfare). No audio files needed.
 */
class Sound : SoundSink {
    private val rate = 44100
    private val track: AudioTrack
    @Volatile private var running = true
    @Volatile private var speed = 0f
    @Volatile private var boosting = false
    @Volatile var muted = false
    @Volatile var engineOn = false
    private val voices = ArrayList<Voice>()
    private val lock = Any()
    private val rnd = Random(1)

    private abstract class Voice { var t = 0f; var done = false; abstract fun sample(dt: Float): Float }

    private inner class Tone(val f0: Float, val f1: Float, val dur: Float, val vol: Float, val shape: Int = 0) : Voice() {
        var ph = 0f
        override fun sample(dt: Float): Float {
            t += dt; if (t >= dur) { done = true; return 0f }
            val f = f0 + (f1 - f0) * (t / dur)
            ph += f * dt
            val env = min(1f, t * 80f) * (1f - t / dur)
            val w = when (shape) { 1 -> if (ph % 1f < 0.5f) 1f else -1f; 2 -> (ph % 1f) * 2 - 1; else -> sin(ph * 6.2832f) }
            return w * env * vol
        }
    }

    private inner class Noise(val dur: Float, val vol: Float, val cutoff: Float) : Voice() {
        var lp = 0f
        override fun sample(dt: Float): Float {
            t += dt; if (t >= dur) { done = true; return 0f }
            val n = rnd.nextFloat() * 2 - 1
            val k = (cutoff * (1f - t / dur * 0.7f) / rate * 6.28f).coerceIn(0.001f, 1f)
            lp += (n - lp) * k
            return lp * vol * (1f - t / dur).pow(2)
        }
    }

    private inner class Melody(val notes: FloatArray, val step: Float, val vol: Float) : Voice() {
        var ph = 0f
        override fun sample(dt: Float): Float {
            t += dt
            val i = (t / step).toInt(); if (i >= notes.size) { done = true; return 0f }
            val nt = t - i * step
            ph += notes[i] * dt
            val env = min(1f, nt * 60f) * exp(-nt * 3f)
            return (sin(ph * 6.2832f) * 0.7f + (if (ph % 1f < 0.5f) 0.3f else -0.3f)) * env * vol
        }
    }

    init {
        val buf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(max(buf, 4096 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        Thread({ loop() }, "synth").apply { priority = Thread.MAX_PRIORITY; isDaemon = true; start() }
    }

    private fun loop() {
        val n = 1024
        val out = ShortArray(n)
        val dt = 1f / rate
        var ph1 = 0f; var ph2 = 0f; var lp = 0f; var curF = 60f
        while (running) {
            val targetF = 55f + speed * 2.2f + (if (boosting) 40f else 0f)
            for (i in 0 until n) {
                curF += (targetF - curF) * 0.0005f
                var s = 0f
                if (engineOn) {
                    ph1 += curF * dt; ph2 += curF * 0.5f * dt
                    val saw = (ph1 % 1f) * 2 - 1
                    val sq = if (ph2 % 1f < 0.5f) 0.6f else -0.6f
                    lp += ((saw * 0.6f + sq * 0.4f) - lp) * 0.08f
                    s += lp * 0.16f
                }
                synchronized(lock) {
                    val it = voices.iterator()
                    while (it.hasNext()) { val v = it.next(); s += v.sample(dt); if (v.done) it.remove() }
                }
                if (muted) s = 0f
                out[i] = (tanh(s) * 30000).toInt().toShort()
            }
            track.write(out, 0, n)
        }
    }

    override fun engine(speed: Float, boost: Boolean) { this.speed = speed; this.boosting = boost }

    private fun add(v: Voice) = synchronized(lock) { if (voices.size < 24) voices.add(v) }

    override fun play(name: String, strength: Float) {
        when (name) {
            "boost" -> { add(Noise(1.2f, 0.7f, 3000f)); add(Tone(200f, 900f, 0.6f, 0.25f, 2)) }
            "pad" -> add(Tone(500f, 1400f, 0.25f, 0.25f, 1))
            "whoosh" -> add(Noise(0.6f, 0.5f, 1800f))
            "land" -> add(Noise(0.25f, min(0.9f, 0.2f + strength * 0.04f), 400f))
            "bump" -> { add(Noise(0.18f, 0.6f, 700f)); add(Tone(140f, 60f, 0.2f, 0.4f)) }
            "scrape" -> add(Noise(0.25f, 0.35f, 5000f))
            "thud" -> { add(Tone(220f, 90f, 0.15f, 0.45f)); add(Noise(0.12f, 0.3f, 1200f)) }
            "whoops" -> add(Tone(700f, 250f, 0.5f, 0.3f, 1))
            "beep" -> add(Tone(660f, 660f, 0.22f, 0.35f, 1))
            "go" -> add(Tone(1320f, 1320f, 0.5f, 0.35f, 1))
            "win" -> add(Melody(floatArrayOf(523f, 659f, 784f, 1047f, 784f, 1047f, 1319f), 0.16f, 0.4f))
            "finish" -> add(Melody(floatArrayOf(523f, 659f, 784f, 1047f), 0.18f, 0.35f))
            "firework" -> { add(Tone(400f, 1200f, 0.3f, 0.1f, 2)); add(Noise(0.5f, 0.35f, 2500f)) }
            "click" -> add(Tone(900f, 1200f, 0.06f, 0.25f))
            "pop" -> add(Tone(300f, 900f, 0.12f, 0.3f))
        }
    }

    fun release() { running = false; track.stop(); track.release() }
}
