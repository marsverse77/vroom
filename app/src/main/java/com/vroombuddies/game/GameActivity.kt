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
import com.vroombuddies.game.render.fx.PostFx
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
    private var postFx: PostFx? = null
    private var surfaceW = 0; private var surfaceH = 0
    override lateinit var settings: GraphicsSettings
    private val sound by lazy { Sound(this) }

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
    private var vsyncs = 0
    private var loggedFrame = false
    private var frames = 0

    private fun followRefreshRate() {
        @Suppress("DEPRECATION")
        val hz = surface.display?.refreshRate ?: return
        if (kotlin.math.abs(hz - settings.displayHz) > 1f) { settings.displayHz = hz; settings.applyFrameRate(renderer) }
    }
    private var warmFrames = 0
    private val WARM_FRAMES = 4
    private var aspect = 1.8f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        surface = SurfaceView(this)
        overlay = FrameLayout(this)
        val root = FrameLayout(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        hideSystemUi()

        settings = GraphicsSettings(this)
        pickDisplayMode()
        choreographer = Choreographer.getInstance()
        displayHelper = DisplayHelper(this)
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(surface)

        android.util.Log.i("Vroom", "Startup: engine")
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(EntityManager.get().create())
        view.scene = scene; view.camera = camera
        view.blendMode = View.BlendMode.OPAQUE
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true; clearColor = doubleArrayOf(0.3, 0.6, 0.95, 1.0) }
        camera.setExposure(1f)
        android.util.Log.i("Vroom", "Startup: settings.apply")
        settings.apply(view, renderer)

        mats = MaterialLib(this)
        setScreen(Menus.loading(this, "Getting the cars ready..."))
        thread(name = "materials") {
            android.util.Log.i("Vroom", "Startup: val pkgs")
            val pkgs = try { mats.preparePackages { } } catch (t: Throwable) { Log.e("Vroom", "material build failed", t); null }
            runOnUiThread {
                if (pkgs == null) { setScreen(Menus.loading(this, "Oops! This phone can't run the game.")); return@runOnUiThread }
                android.util.Log.i("Vroom", "Startup: mats.create")
                mats.create(engine, pkgs)
                // start compiling every shader variant now (in the background where the GPU driver allows),
                // so the first spark, boost or jump never pauses the game to build a shader
                android.util.Log.i("Vroom", "Startup: mats.prewarm")
                mats.prewarm()
                android.util.Log.i("Vroom", "Startup: postFx")
                postFx = PostFx(engine, mats.postFx).also { it.resize(surfaceW, surfaceH) }
                ready = true
                android.util.Log.i("Vroom", "Startup: toTitle")
                toTitle()
            }
        }
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun setScreen(v: android.view.View, swoosh: Boolean = true) {
        overlay.removeAllViews(); overlay.addView(v, FrameLayout.LayoutParams(-1, -1)); hud = null; left = null; right = null; boostBtn = null
        if (swoosh) {
            // quick slide in from the right: every screen change feels zippy
            v.translationX = Ui.dp(90f); v.alpha = 0f
            v.animate().translationX(0f).alpha(1f).setDuration(220).setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
        }
    }

    // ------------------------------------------------------------- menus
    private fun ensureShowroom() {
        if (game != null) { game!!.destroy(); game = null; sound.engineOn = false }
        sound.menuMusic()
        if (showroom == null) showroom = Showroom(engine, scene, view, camera, mats, settings).also { it.setAspect(aspect) }
        showroom!!.setCar(CarDesigns.all[carIndex], Paints.all[paintIndex])
    }

    override fun toTitle() { ensureShowroom(); showroom!!.offsetX = 0.35f; showroom!!.offsetY = 0f; setScreen(Menus.title(this, this)) }
    override fun toCarPicker() { ensureShowroom(); showroom!!.offsetX = 0f; showroom!!.offsetY = 0.3f; setScreen(Menus.carPicker(this, this, carIndex)) }
    override fun toPaintPicker() { ensureShowroom(); showroom!!.offsetX = 0f; showroom!!.offsetY = 0.3f; setScreen(Menus.paintPicker(this, this, paintIndex)) }
    override fun toTrackPicker() { ensureShowroom(); showroom!!.offsetX = 0f; showroom!!.offsetY = 0f; setScreen(Menus.trackPicker(this, this)) }
    override fun toSettings() { ensureShowroom(); setScreen(Menus.settings(this, this)) }
    override fun pickCar(index: Int) { carIndex = index; showroom?.setCar(CarDesigns.all[index], Paints.all[paintIndex]) }
    override fun pickPaint(index: Int) { paintIndex = index; showroom?.setPaint(Paints.all[index]) }
    override fun applySettings() { settings.save(); pickDisplayMode(); settings.apply(view, renderer) }

    /**
     * Ask for a screen mode that matches the frame-rate setting. A 120 Hz tablet asked to run a 60 fps game
     * otherwise keeps refreshing at 120 Hz and every frame lands unevenly (judder). Switching the panel to
     * 60 Hz, or drawing on every 2nd refresh when it can't switch, keeps frames perfectly even.
     */
    private fun pickDisplayMode() {
        try {
            @Suppress("DEPRECATION")
            val d = (if (android.os.Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
            val cur = d.mode
            val modes = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            val want = settings.targetFps.toFloat()
            // the slowest mode that still reaches the target; a screen that can't reach it runs flat out
            val best = modes.filter { it.refreshRate >= want - 1f }.minByOrNull { it.refreshRate } ?: modes.maxByOrNull { it.refreshRate } ?: cur
            if (window.attributes.preferredDisplayModeId != best.modeId) window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
            settings.displayHz = best.refreshRate
        } catch (t: Throwable) { Log.w("Vroom", "display mode", t) }
    }
    override fun click() = sound.play("click")
    override fun stars(themeIndex: Int) = getSharedPreferences("progress", MODE_PRIVATE).getInt("stars$themeIndex", 0)

    override fun startRace(themeIndex: Int) {
        this.themeIndex = themeIndex
        setScreen(Menus.loading(this, "Building ${Themes.all[themeIndex].name}..."))
        // let the loading screen draw before the heavy build
        overlay.postDelayed({
            showroom?.destroy(); showroom = null
            game?.destroy()
            android.util.Log.i("Vroom", "Startup: settings.apply")
            settings.apply(view, renderer)
            game = GameScene(engine, scene, view, camera, mats, settings, Themes.all[themeIndex], CarDesigns.all[carIndex], Paints.all[paintIndex], sound).also { it.setAspect(aspect) }
            // building the world leaves a lot of garbage behind: collect it now, behind the loading screen,
            // instead of letting the collector wake up in the middle of the race
            Runtime.getRuntime().gc()
            sound.engineOn = true; sound.raceMusic(Themes.all[themeIndex].name)
            resultShown = false; paused = false
            // draw a few hidden frames with every effect on (still behind the loading screen) so shaders,
            // shadow maps and post-processing buffers are all ready before the countdown appears
            warmFrames = WARM_FRAMES
        }, 120)
    }

    private fun showHud() {
        val root = Ui.frame(this)
        val h = HudOverlay(this).also { it.track = game?.track }; root.addView(h, FrameLayout.LayoutParams(-1, -1)); hud = h
        val l = SteerButton(this, -1); val r = SteerButton(this, 1)
        root.addView(l, Ui.lp(Ui.dpi(Ui.STEER_SIZE), Ui.dpi(Ui.STEER_SIZE), Gravity.BOTTOM or Gravity.START, l = 16f, b = 14f))
        root.addView(r, Ui.lp(Ui.dpi(Ui.STEER_SIZE), Ui.dpi(Ui.STEER_SIZE), Gravity.BOTTOM or Gravity.START, l = 150f, b = 14f))
        val b = BoostButton(this)
        root.addView(b, Ui.lp(Ui.dpi(Ui.BOOST_SIZE), Ui.dpi(Ui.BOOST_SIZE), Gravity.BOTTOM or Gravity.END, r = 18f, b = 14f))
        root.addView(Ui.roundButton(this, "⏸", Ui.STEEL, 24f) { click(); pause() }, Ui.lp(-2, -2, Gravity.TOP or Gravity.END, t = 74f, r = 22f))
        setScreen(root, swoosh = false)
        hud = h; left = l; right = r; boostBtn = b
    }

    private fun pause() {
        if (paused || game == null) return
        paused = true; sound.setPaused(true)
        lateinit var shade: android.view.View
        shade = Menus.pause(this,
            onResume = { click(); paused = false; sound.setPaused(false); overlay.removeView(shade) },
            onRestart = { click(); sound.setPaused(false); startRace(themeIndex) },
            onHome = { click(); paused = false; sound.setPaused(false); toTitle() })
        overlay.addView(shade, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showResult(place: Int) {
        resultShown = true
        val prefs = getSharedPreferences("progress", MODE_PRIVATE)
        val stars = when (place) { 1 -> 3; 2 -> 2; 3 -> 1; else -> 0 }
        if (stars > prefs.getInt("stars$themeIndex", 0)) prefs.edit().putInt("stars$themeIndex", stars).apply()
        overlay.postDelayed({
            if (game == null) return@postDelayed
            overlay.addView(ResultView(this, place, stars, { sound.play(it) },
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
            // the screen may switch modes a moment after we ask (or refuse): follow its real refresh rate
            if ((frames++ and 63) == 0) followRefreshRate()
            // frame pacing: on a fast screen that couldn't switch to the game's frame rate, draw on every Nth refresh
            val interval = settings.frameInterval
            if (interval > 1 && (vsyncs++ % interval) != 0) return
            val dt = if (lastNanos == 0L) 1f / 60f else ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0.001f, 0.05f)
            lastNanos = frameTimeNanos
            if (!ready) return
            val g = game
            if (g != null && warmFrames > 0) {
                g.warming = true
                g.update(1f / 60f, 0f, false)
                g.post.force = true
                if (settings.depthOfField) settings.setDof(view, true, 8f, camera)
                if (--warmFrames == 0) {
                    g.warming = false; g.post.force = false
                    settings.setDof(view, false, 8f, camera)
                    showHud()
                }
            } else if (g != null) {
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
            } else {
                if (frames < 1200 && !loggedFrame) Log.i("Vroom", "first showroom update")
                showroom?.update(dt)
            }
            val sc = swapChain ?: return
            if (!loggedFrame) Log.i("Vroom", "first beginFrame")
            if (uiHelper.isReadyToRender && renderer.beginFrame(sc, frameTimeNanos)) {
                if (!loggedFrame) Log.i("Vroom", "first render")
                postFx?.render(renderer, view, game?.post) ?: renderer.render(view)
                if (!loggedFrame) Log.i("Vroom", "first endFrame")
                renderer.endFrame()
                if (!loggedFrame) Log.i("Vroom", "Showroom frame submitted")
                loggedFrame = true
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
        postFx?.destroy(view)
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
            settings.screenW = width; settings.screenH = height; settings.apply(view, renderer)
            surfaceW = width; surfaceH = height; postFx?.resize(width, height)
            game?.setAspect(aspect); showroom?.setAspect(aspect)
        }
    }
}
