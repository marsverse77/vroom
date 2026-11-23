package com.vroombuddies.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.vroombuddies.game.render.SoundSink
import kotlin.math.max

/**
 * The game's sound system: original synthesised music (a menu song, a song for every track and a victory
 * song), a geared cartoon engine, tyre squeal, wind, and all the effects. No audio files and nothing licensed.
 * The mixing runs on its own thread and streams to an [AudioTrack].
 */
class Sound(ctx: Context) : SoundSink {
    val settings = AudioSettings.of(ctx)
    private val mixer = Mixer()
    private val track: AudioTrack? = makeTrack()
    @Volatile private var running = true
    @Volatile var muted = false
    var engineOn: Boolean
        get() = mixer.engine.on
        set(v) { mixer.engine.on = v; if (!v) { mixer.engine.speed = 0f; mixer.engine.slide = 0f } }

    /** Low-latency stream if the device offers it, a plain one if not, and silence rather than a crash. */
    private fun makeTrack(): AudioTrack? {
        for (lowLatency in listOf(true, false)) {
            try {
                val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val b = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(max(min * 2, 2048 * 2 * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                if (lowLatency) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                val t = b.build()
                t.play()
                return t
            } catch (t: Throwable) { android.util.Log.w("Vroom", "audio track (lowLatency=$lowLatency) failed", t) }
        }
        return null
    }

    init {
        if (track != null) Thread({ loop(track) }, "synth").apply { priority = Thread.MAX_PRIORITY; isDaemon = true; start() }
    }

    private fun loop(track: AudioTrack) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        val n = 512
        val buf = FloatArray(n)
        val out = ShortArray(n)
        while (running) {
            mixer.musicVol = settings.music.gain
            mixer.sfxVol = settings.effects.gain
            if (muted) { java.util.Arrays.fill(out, 0) }
            else { mixer.render(buf, n); for (i in 0 until n) out[i] = (buf[i] * 32000f).toInt().toShort() }
            track.write(out, 0, n)
        }
        try { track.stop() } catch (_: IllegalStateException) {}
        track.release()
    }

    // ---- SoundSink (called by the race scene)
    override fun engine(speed: Float, boost: Boolean) { mixer.engine.speed = speed; mixer.engine.boost = boost }
    override fun tires(slide: Float, onGround: Boolean) { mixer.engine.slide = slide; mixer.engine.grounded = onGround }
    override fun play(name: String, strength: Float) = mixer.play(name, strength)

    // ---- music and flow (called by the activity)
    /** Menu song; safe to call on every menu screen, it keeps playing without restarting. */
    fun menuMusic() { setPaused(false); mixer.menu() }
    /** Queue the track's own song; it starts on "GO!". */
    fun raceMusic(themeName: String) { setPaused(false); mixer.race(Songs.forTheme(themeName)) }
    /** In-race pause: engine fades, music goes soft and muffled. */
    fun setPaused(p: Boolean) { mixer.paused = p; mixer.engine.hush = p }

    fun release() { running = false }
}
