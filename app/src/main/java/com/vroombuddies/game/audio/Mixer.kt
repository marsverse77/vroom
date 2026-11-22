package com.vroombuddies.game.audio

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.tanh

/**
 * Everything that makes sound, mixed into one stream: music, the player's engine and effects.
 * Pure Kotlin (no Android), so it can be rendered offline in tests. Game-thread calls are queued and
 * applied on the audio thread at the start of each block.
 */
internal class Mixer {
    val engine = EngineSound()
    private val music = MusicPlayer()
    private val sfx = ArrayList<Voice>(32)
    private val cmds = ConcurrentLinkedQueue<() -> Unit>()
    private val songs = HashMap<Style, Song>()
    private var current: Style? = null
    private var armed: Style? = null      // race song waiting for the "GO!"

    @Volatile var musicVol = 1f
    @Volatile var sfxVol = 1f
    @Volatile var paused = false
    private var pauseAmt = 0f
    private var duck = 0f
    private var musicLp = 0f

    private fun song(st: Style) = songs.getOrPut(st) { Composer.compose(st) }
    private fun switchTo(st: Style?, fade: Float = 0.6f, delay: Float = 0f) {
        current = st
        music.change(st?.let { song(it) }, fade, delay)
    }

    /** Title and pickers: the menu song (keeps playing across menu screens). */
    fun menu() { cmds.add { armed = null; paused = false; if (current !== Songs.menu) switchTo(Songs.menu, 0.8f, 0.2f) } }

    /** Entering a race: fade out, then start the theme's song on "GO!". */
    fun race(st: Style) { cmds.add { paused = false; armed = st; switchTo(null, 0.8f) } }

    fun play(name: String, strength: Float) { cmds.add {
        for (v in Sfx.make(name, strength)) if (sfx.size < 48) sfx.add(v)
        when (name) {
            "go" -> armed?.let { switchTo(it, 0.01f, 0.25f); armed = null }
            "win", "finish" -> { armed = null; switchTo(Songs.victory, 0.3f, Sfx.fanfareLength(name == "win")) }
            "boost" -> duck = 1f
        }
    } }

    fun stopMusic() { cmds.add { armed = null; switchTo(null, 0.3f) } }

    /** Fills [out] with samples in -1..1. */
    fun render(out: FloatArray, n: Int = out.size) {
        while (true) { val c = cmds.poll() ?: break; c() }
        val mv = musicVol * 0.4f; val sv = sfxVol
        val wantPause = if (paused) 1f else 0f
        for (i in 0 until n) {
            if (i and 63 == 0) {
                pauseAmt += (wantPause - pauseAmt) * 0.02f
                duck *= 0.985f
            }
            val m = music.next() * mv * (1f - 0.35f * duck)
            // paused: music goes quiet and muffled, like hearing it from another room
            musicLp += (m - musicLp) * (1f - 0.93f * pauseAmt)
            var e = engine.next()
            var i2 = 0
            while (i2 < sfx.size) {
                val v = sfx[i2]
                e += v.next()
                if (v.done) { sfx[i2] = sfx[sfx.size - 1]; sfx.removeAt(sfx.size - 1) } else i2++
            }
            val s = musicLp * (1f - 0.55f * pauseAmt) + e * sv
            out[i] = tanh(s * 1.1f) * 0.92f
        }
    }
}
