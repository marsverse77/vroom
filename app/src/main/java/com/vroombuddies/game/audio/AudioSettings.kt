package com.vroombuddies.game.audio

import android.content.Context

/** Volume choices for the grown-up settings screen, saved on the phone. */
enum class Volume(val label: String, val gain: Float) { LOUD("Loud", 1.25f), NORMAL("Normal", 1f), QUIET("Quiet", 0.5f), OFF("Off", 0f) }

class AudioSettings private constructor(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("audio", Context.MODE_PRIVATE)
    var music: Volume = Volume.valueOf(prefs.getString("music", Volume.NORMAL.name)!!)
        set(v) { field = v; save() }
    var effects: Volume = Volume.valueOf(prefs.getString("sfx", Volume.NORMAL.name)!!)
        set(v) { field = v; save() }
    private fun save() = prefs.edit().putString("music", music.name).putString("sfx", effects.name).apply()

    companion object {
        @Volatile private var inst: AudioSettings? = null
        /** One shared instance, so the settings screen and the synth see the same values. */
        fun of(ctx: Context): AudioSettings = inst ?: synchronized(this) { inst ?: AudioSettings(ctx.applicationContext).also { inst = it } }
    }
}
