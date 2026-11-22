package com.vroombuddies.game

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.vroombuddies.game.render.GraphicsSettings
import com.vroombuddies.game.render.Textures
import com.vroombuddies.game.content.Themes
import com.vroombuddies.game.sim.RacePhase
import com.vroombuddies.game.render.HudState
import com.vroombuddies.game.ui.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the real menus and HUD (transparent background, ready to lay over a 3D render) and the
 * procedural textures to $PREVIEW_DIR as PNGs. Skipped unless PREVIEW_DIR is set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h370dp-land-xxhdpi")
class PreviewUi {
    private val dirName: String? = System.getenv("PREVIEW_DIR")

    private lateinit var act: Activity

    private fun save(v: View, w: Int, h: Int, name: String) {
        // show the screen as it looks once the pop-in animations have finished
        fun settle(x: View) {
            x.animate().cancel(); x.alpha = 1f; x.scaleX = 1f; x.scaleY = 1f; x.translationY = 0f
            if (x is android.view.ViewGroup) for (i in 0 until x.childCount) settle(x.getChildAt(i))
        }
        settle(v)
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(dirName!!, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun png(b: Bitmap, name: String) = File(dirName!!, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test
    fun export() {
        Assume.assumeTrue(dirName != null)
        act = Robolectric.buildActivity(Activity::class.java).setup().get()
        Ui.density = act.resources.displayMetrics.density
        val w = act.resources.displayMetrics.widthPixels; val h = act.resources.displayMetrics.heightPixels
        val gs = GraphicsSettings(act)
        val host = object : MenuHost {
            override fun toCarPicker() {}; override fun toPaintPicker() {}; override fun toTrackPicker() {}; override fun toTitle() {}; override fun toSettings() {}
            override fun pickCar(index: Int) {}; override fun pickPaint(index: Int) {}; override fun startRace(themeIndex: Int) {}
            override fun applySettings() {}; override fun click() {}
            override val settings = gs
            override fun stars(themeIndex: Int) = listOf(3, 2, 1, 0)[themeIndex % 4].takeIf { themeIndex < 3 } ?: 0
        }
        File(dirName!!).mkdirs()
        File(dirName, "size.txt").writeText("$w $h ${Ui.density}")
        save(Menus.title(act, host), w, h, "ui_title")
        save(Menus.carPicker(act, host, 0), w, h, "ui_car")
        save(Menus.paintPicker(act, host, 3), w, h, "ui_paint")
        save(Menus.trackPicker(act, host), w, h, "ui_tracks")
        save(Menus.settings(act, host), w, h, "ui_settings")

        // HUD exactly as GameActivity lays it out, fed from the exported race state
        for (shot in listOf("race_start", "race_chase", "race_jump", "race_tires")) {
            val jf = File(dirName, "$shot.json"); if (!jf.exists()) continue
            val js = org.json.JSONObject(jf.readText()).getJSONObject("hud")
            val st = HudState().apply {
                place = js.getInt("place"); lap = js.getInt("lap"); laps = 2; racers = 6
                energy = js.getDouble("energy").toFloat(); speedKmh = js.getDouble("speed").toFloat(); boosting = js.getBoolean("boost")
                phase = RacePhase.valueOf(js.getString("phase")); countdown = js.getDouble("countdown").toFloat()
                val pr = js.getJSONArray("progress"); progress = FloatArray(pr.length()) { pr.getDouble(it).toFloat() }
                playerIndex = js.getInt("playerIndex")
                if (shot == "race_jump") letterbox = 1f
            }
            val root = FrameLayout(act)
            val hud = HudOverlay(act).apply { state = st }
            root.addView(hud, FrameLayout.LayoutParams(-1, -1))
            val l = SteerButton(act, -1); val r = SteerButton(act, 1)
            root.addView(l, Ui.lp(Ui.dpi(130f), Ui.dpi(130f), Gravity.BOTTOM or Gravity.START, l = 18f, b = 18f))
            root.addView(r, Ui.lp(Ui.dpi(130f), Ui.dpi(130f), Gravity.BOTTOM or Gravity.START, l = 160f, b = 18f))
            val b = BoostButton(act).apply { energy = if (shot == "race_chase") 70f else st.energy.coerceAtLeast(40f); boosting = st.boosting }
            root.addView(b, Ui.lp(Ui.dpi(170f), Ui.dpi(170f), Gravity.BOTTOM or Gravity.END, r = 18f, b = 14f))
            root.addView(Ui.button(act, "⏸", Ui.PURPLE, 22f) {}, Ui.lp(-2, -2, Gravity.TOP or Gravity.END, t = 74f, r = 22f))
            hud.tick(0.016f); if (shot != "race_start") hud.tick(2f); b.tick(0.3f)
            save(root, w, h, "hud_$shot")
        }
        val win = FrameLayout(act).apply { addView(ResultView(act, 1, {}, {}, {}), FrameLayout.LayoutParams(-1, -1)) }
        save(win, w, h, "ui_win")

        val t = Themes.all[0]
        png(Textures.roadBitmap(t.road, t.kerbs.first, t.kerbs.second), "tex_ROAD")
        png(Textures.wallBitmap(t.walls.first, t.walls.second), "tex_WALL")
        png(Textures.groundBitmap(t.skirt, t.skirt, 5), "tex_SKIRT")
        png(Textures.rampBitmap(), "tex_RAMP")
        png(Textures.chevronsBitmap(), "tex_PADS")
        png(Textures.checkerBitmap(), "tex_START_LINE")
        png(Textures.groundBitmap(t.ground!!.first, t.ground!!.second, t.shape.seed), "tex_GROUND")
    }
}
