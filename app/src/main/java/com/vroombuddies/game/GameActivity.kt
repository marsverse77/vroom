package com.vroombuddies.game

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import com.google.android.filament.*
import com.google.android.filament.View
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.Utils
import com.vroombuddies.game.audio.Sound
import com.vroombuddies.game.content.Themes
import com.vroombuddies.game.render.*
import com.vroombuddies.game.sim.RacePhase
import com.vroombuddies.game.ui.*
import kotlin.concurrent.thread

/**
 * The whole app is one activity: a Filament SurfaceView with Android views on top for menus and HUD.
 * Flow: loading (compile shaders once) -> title -> car -> paint -> track -> race -> result.
 */
class GameActivity : Activity(), MenuHost {
    companion object { init { Utils.init() } }

    private lateinit var surface: SurfaceView
    private lateinit var overlay: FrameLayout
    private lateinit var uiHelper: UiHelper
    private lateinit var displayHelper: DisplayHelper
    private lateinit var choreographer: Choreographer
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    private var swapChain: SwapChain? = null
    private lateinit var mats: MaterialLib
    override lateinit var settings: GraphicsSettings
    private val sound by lazy { Sound() }

    private var showroom: Showroom? = null
    private var game: GameScene? = null
    private var ready = false
    private var carIndex = 0
    private var paintIndex = 0
    private var themeIndex = 0
    private var paused = false
    private var resultShown = false

    // HUD
    private var hud: HudOverlay? = null
    private var left: SteerButton? = null
    private var right: SteerButton? = null
    private var boostBtn: BoostButton? = null
    private var keyLeft = false; private var keyRight = false; private var keyBoost = false
    private var lastNanos = 0L
    private var aspect = 1.8f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.density = resources.displayMetrics.density
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        surface = SurfaceView(this)
        overlay = FrameLayout(this)
        val root = FrameLayout(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        hideSystemUi()

        settings = GraphicsSettings(this)
        choreographer = Choreographer.getInstance()
        displayHelper = DisplayHelper(this)
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(surface)

        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(EntityManager.get().create())
        view.scene = scene; view.camera = camera
        view.blendMode = View.BlendMode.OPAQUE
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true; clearColor = doubleArrayOf(0.3, 0.6, 0.95, 1.0) }
        camera.setExposure(1f)
        settings.apply(view, renderer)

        mats = MaterialLib(this)
        setScreen(Menus.loading(this, "Getting the cars ready..."))
        thread(name = "materials") {
            val pkgs = try { mats.preparePackages { } } catch (t: Throwable) { Log.e("Vroom", "material build failed", t); null }
            runOnUiThread {
                if (pkgs == null) { setScreen(Menus.loading(this, "Oops! This phone can't run the game.")); return@runOnUiThread }
                mats.create(engine, pkgs)
                ready = true
                toTitle()
            }
        }
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun setScreen(v: android.view.View) { overlay.removeAllViews(); overlay.addView(v, FrameLayout.LayoutParams(-1, -1)); hud = null; left = null; right = null; boostBtn = null }

    // ------------------------------------------------------------- menus
    private fun ensureShowroom() {
        if (game != null) { game!!.destroy(); game = null; sound.engineOn = false }
        if (showroom == null) showroom = Showroom(engine, scene, view, camera, mats, settings).also { it.setAspect(aspect) }
        showroom!!.setCar(CarDesigns.all[carIndex], Paints.all[paintIndex])
    }

    override fun toTitle() { ensureShowroom(); showroom!!.offsetX = 0.35f; setScreen(Menus.title(this, this)) }
    override fun toCarPicker() { ensureShowroom(); showroom!!.offsetX = 0f; setScreen(Menus.carPicker(this, this, carIndex)) }
    override fun toPaintPicker() { ensureShowroom(); showroom!!.offsetX = 0f; setScreen(Menus.paintPicker(this, this, paintIndex)) }
    override fun toTrackPicker() { ensureShowroom(); showroom!!.offsetX = 0f; setScreen(Menus.trackPicker(this, this)) }
    override fun toSettings() { ensureShowroom(); setScreen(Menus.settings(this, this)) }
    override fun pickCar(index: Int) { carIndex = index; showroom?.setCar(CarDesigns.all[index], Paints.all[paintIndex]) }
    override fun pickPaint(index: Int) { paintIndex = index; showroom?.setPaint(Paints.all[index]) }
    override fun applySettings() { settings.save(); settings.apply(view, renderer) }
    override fun click() = sound.play("click")
    override fun stars(themeIndex: Int) = getSharedPreferences("progress", MODE_PRIVATE).getInt("stars$themeIndex", 0)

    override fun startRace(themeIndex: Int) {
        this.themeIndex = themeIndex
        setScreen(Menus.loading(this, "Building ${Themes.all[themeIndex].name}..."))
        // let the loading screen draw before the heavy build
        overlay.postDelayed({
            showroom?.destroy(); showroom = null
            game?.destroy()
            settings.apply(view, renderer)
            game = GameScene(engine, scene, view, camera, mats, settings, Themes.all[themeIndex], CarDesigns.all[carIndex], Paints.all[paintIndex], sound).also { it.setAspect(aspect) }
            sound.engineOn = true
            resultShown = false; paused = false
            showHud()
        }, 120)
    }

    private fun showHud() {
        val root = Ui.frame(this)
        val h = HudOverlay(this); root.addView(h, FrameLayout.LayoutParams(-1, -1)); hud = h
        val l = SteerButton(this, -1); val r = SteerButton(this, 1)
        root.addView(l, Ui.lp(Ui.dpi(130f), Ui.dpi(130f), Gravity.BOTTOM or Gravity.START, l = 18f, b = 18f))
        root.addView(r, Ui.lp(Ui.dpi(130f), Ui.dpi(130f), Gravity.BOTTOM or Gravity.START, l = 160f, b = 18f))
        val b = BoostButton(this)
        root.addView(b, Ui.lp(Ui.dpi(170f), Ui.dpi(170f), Gravity.BOTTOM or Gravity.END, r = 18f, b = 14f))
        root.addView(Ui.button(this, "⏸", Ui.PURPLE, 22f) { click(); pause() }, Ui.lp(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL, t = 70f))
        setScreen(root)
        hud = h; left = l; right = r; boostBtn = b
    }

    private fun pause() {
        if (paused || game == null) return
        paused = true
        val box = Ui.col(this)
        box.addView(Ui.text(this, "Paused", 56f))
        box.addView(Ui.button(this, "▶  Keep racing", Ui.GREEN, 30f) { click(); paused = false; overlay.removeView(box.parent as android.view.View) })
        box.addView(Ui.button(this, "🔁  Start again", Ui.BLUE, 26f) { click(); startRace(themeIndex) })
        box.addView(Ui.button(this, "🏠  Home", Ui.ORANGE, 26f) { click(); paused = false; toTitle() })
        val shade = Ui.frame(this).apply { setBackgroundColor(0x88000000.toInt()); addView(box, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)) }
        overlay.addView(shade, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showResult(place: Int) {
        resultShown = true
        val prefs = getSharedPreferences("progress", MODE_PRIVATE)
        val stars = when (place) { 1 -> 3; 2 -> 2; 3 -> 1; else -> 0 }
        if (stars > prefs.getInt("stars$themeIndex", 0)) prefs.edit().putInt("stars$themeIndex", stars).apply()
        overlay.postDelayed({
            if (game == null) return@postDelayed
            overlay.addView(ResultView(this, place,
                onAgain = { click(); startRace(themeIndex) },
                onNext = { click(); startRace((themeIndex + 1) % Themes.all.size) },
                onHome = { click(); toTitle() }), FrameLayout.LayoutParams(-1, -1))
        }, 1800)
    }

    // ------------------------------------------------------------- input
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> keyLeft = true
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> keyRight = true
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_UP -> keyBoost = true
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> keyLeft = false
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> keyRight = false
            else -> return super.onKeyUp(keyCode, event)
        }
        return true
    }

    // ------------------------------------------------------------- loop
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            val dt = if (lastNanos == 0L) 1f / 60f else ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0.001f, 0.05f)
            lastNanos = frameTimeNanos
            if (!ready) return
            val g = game
            if (g != null) {
                if (!paused) {
                    val steer = ((if (right?.held == true || keyRight) 1f else 0f) - (if (left?.held == true || keyLeft) 1f else 0f))
                    val boost = (boostBtn?.tapped == true) || keyBoost
                    boostBtn?.tapped = false; keyBoost = false
                    g.update(dt, steer, boost)
                    hud?.state = g.hud
                    boostBtn?.energy = g.hud.energy; boostBtn?.boosting = g.hud.boosting
                    if (g.hud.phase == RacePhase.FINISHED && !resultShown) showResult(g.hud.finishPlace)
                    if (g.hud.phase == RacePhase.COUNTDOWN) countdownBeeps(g.hud.countdown)
                }
                hud?.tick(dt); boostBtn?.tick(dt)
            } else showroom?.update(dt)
            val sc = swapChain ?: return
            if (uiHelper.isReadyToRender && renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }
    }

    private var lastBeep = 99
    private fun countdownBeeps(cd: Float) {
        val n = kotlin.math.ceil(cd - 0.6f).toInt()
        if (n != lastBeep) { lastBeep = n; if (n in 1..3) sound.play("beep") else if (n <= 0) sound.play("go") }
        if (cd > 3.4f) lastBeep = 99
    }

    override fun onResume() { super.onResume(); hideSystemUi(); lastNanos = 0L; choreographer.postFrameCallback(frameCallback); sound.muted = false }
    override fun onPause() { super.onPause(); choreographer.removeFrameCallback(frameCallback); sound.muted = true; if (game != null && !resultShown) pause() }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameCallback)
        game?.destroy(); showroom?.destroy()
        uiHelper.detach()
        engine.destroyRenderer(renderer); engine.destroyView(view); engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        engine.destroy()
        sound.release()
    }

    inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
            displayHelper.attach(renderer, this@GameActivity.surface.display)
        }
        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait(); swapChain = null }
        }
        override fun onResized(width: Int, height: Int) {
            aspect = width.toFloat() / height
            view.viewport = Viewport(0, 0, width, height)
            game?.setAspect(aspect); showroom?.setAspect(aspect)
        }
    }
}
