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
    private val carPaints = intArrayOf(0, 5, 1, 7, 4, 3, 2)
    private var paintIndex = carPaints[0]
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
    private var frames = 0
    private var garageVisible = false
    private var staticMenu = false
    private var showroomDrawn = false
    private var raceDrawn = false

    private var renderScale = 1f
    private var overspent = 0
    private var comfortable = 0
    private var requestedW = 0
    private var requestedH = 0

    private fun sizeRenderSurface() {
        val host = surface.parent as? FrameLayout ?: return
        val w = host.width; val h = host.height
        if (w <= 0 || h <= 0) return
        val pixels = when (settings.effects) {
            Level.OFF, Level.LOW -> 800_000f
            Level.MEDIUM -> 1_400_000f
            Level.HIGH -> 2_000_000f
        }
        val scale = minOf(1f, kotlin.math.sqrt(pixels / (w.toFloat()*h))) * renderScale
        val rw = (w * scale).toInt().coerceAtLeast(1)
        val rh = (h * scale).toInt().coerceAtLeast(1)
        surface.scaleX = w.toFloat() / rw
        surface.scaleY = h.toFloat() / rh
        if (rw == requestedW && rh == requestedH) return
        requestedW = rw; requestedH = rh
        surface.layoutParams = FrameLayout.LayoutParams(rw, rh, Gravity.TOP or Gravity.LEFT)
    }

    /** A physical-buffer fallback for drivers without reliable GPU timers. Only the 3D surface
     * changes resolution; the controls keep their window coordinates and display resolution. */
    private fun adaptSurface(interval: Long) {
        if (!settings.dynamicResolution || interval <= 0) return
        val budget = 1_000_000_000L / settings.targetFps
        overspent = if (interval > budget * 2) overspent + 1 else 0
        comfortable = if (interval < budget * 1.12) comfortable + 1 else 0
        val next = when {
            overspent >= 8 -> (renderScale * .80f).coerceAtLeast(.45f)
            comfortable >= 240 -> (renderScale + .06f).coerceAtMost(1f)
            else -> return
        }
        overspent = 0; comfortable = 0
        if (next != renderScale) { renderScale = next; sizeRenderSurface() }
    }

    private fun followRefreshRate() {
        @Suppress("DEPRECATION")
        val hz = surface.display?.refreshRate ?: return
        if (kotlin.math.abs(hz - settings.displayHz) > 1f) { settings.displayHz = hz; settings.applyFrameRate(renderer) }
    }
    private var loadGeneration = 0
    private val submitIntervals = LongArray(60)
    private var lastSubmission = 0L
    private var samples = 0
    private var declined = 0
    private var pacingSkips = 0
    private val beginTimes = LongArray(60)
    private val updateTimes = LongArray(60)
    private val drawTimes = LongArray(60)
    private fun measureSubmission(nanos: Long, begin: Long, update: Long, draw: Long, phase: RacePhase) {
        if (lastSubmission != 0L) {
            val elapsed = nanos - lastSubmission
            beginTimes[samples] = begin / 1000
            updateTimes[samples] = update / 1000
            drawTimes[samples] = draw / 1000
            submitIntervals[samples++] = elapsed / 1000
            adaptSurface(elapsed)
        }
        lastSubmission = nanos
        if (samples == submitIntervals.size) {
            beginTimes.sort()
            updateTimes.sort()
            drawTimes.sort()
            Log.i("Vroom", "Frame profile us phase=$phase begin50=${beginTimes[30]} update50=${updateTimes[30]} draw50=${drawTimes[30]} update95=${updateTimes[56]} draw95=${drawTimes[56]} declined=$declined pacingSkips=$pacingSkips buffer=${surfaceW}x$surfaceH")
            declined = 0
            pacingSkips = 0
            submitIntervals.sort()
            Log.i("Vroom", "Frame intervals us median=${submitIntervals[30]} p95=${submitIntervals[56]} worst=${submitIntervals.last()}")
            samples = 0
        }
    }
    private var warmFrames = 0
    private val WARM_FRAMES = 4
    private var aspect = 1.8f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        surface = SurfaceView(this).apply { pivotX = 0f; pivotY = 0f }
        val root = FrameLayout(this)
        root.addOnLayoutChangeListener { _,l,t,r,b,ol,ot,orr,ob ->
            if (::settings.isInitialized && (r-l != orr-ol || b-t != ob-ot)) sizeRenderSurface()
        }
        overlay = FrameLayout(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        val viewport = FrameLayout(this)
        viewport.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        overlay.addOnLayoutChangeListener { _, l,t,r,b,ol,ot,orr,ob ->
            if (r-l != orr-ol || b-t != ob-ot) reflow(r-l, b-t)
        }
        root.addView(viewport, FrameLayout.LayoutParams(-1, -1))
        viewport.setOnApplyWindowInsetsListener { v, insets ->
            val cut = if (android.os.Build.VERSION.SDK_INT >= 28) insets.displayCutout else null
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            } else v.setPadding(maxOf(insets.systemWindowInsetLeft,cut?.safeInsetLeft ?: 0),
                maxOf(insets.systemWindowInsetTop,cut?.safeInsetTop ?: 0),
                maxOf(insets.systemWindowInsetRight,cut?.safeInsetRight ?: 0),
                maxOf(insets.systemWindowInsetBottom,cut?.safeInsetBottom ?: 0))
            insets
        }
        setContentView(root)
        hideSystemUi()

        settings = GraphicsSettings(this)
        pickDisplayMode()
        choreographer = Choreographer.getInstance()
        displayHelper = DisplayHelper(this)
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = SurfaceCallback()

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

        uiHelper.attachTo(surface)
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

    private var layoutMenu: (() -> android.view.View)? = null

    private fun reflow(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val unit = minOf(resources.displayMetrics.density * 1.55f, w / 620f, h / 345f)
        if (kotlin.math.abs(unit - Ui.density) < .001f) return
        Ui.density = unit
        overlay.post {
            val menu = layoutMenu
            if (menu != null) {
                setScreen(menu(), swoosh = false)
                layoutMenu = menu
            } else if (hud != null && !resultShown) {
                val wasPaused = paused
                showHud()
                if (wasPaused) { paused = false; pause() }
            }
        }
    }

    private fun menu(make: () -> android.view.View) {
        setScreen(make())
        layoutMenu = make
    }

    private fun setScreen(v: android.view.View, swoosh: Boolean = true) {
        layoutMenu = null
        overlay.removeAllViews(); overlay.addView(v, FrameLayout.LayoutParams(-1, -1)); hud = null; left = null; right = null; boostBtn = null
        if (swoosh) {
            // quick slide in from the right: every screen change feels zippy
            v.translationX = Ui.dp(90f); v.alpha = 0f
            v.animate().translationX(0f).alpha(1f).setDuration(220).setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
        }
    }

    // ------------------------------------------------------------- menus
    private fun ensureShowroom() {
        loadGeneration++
        staticMenu = false
        if (game != null) { game!!.destroy(); game = null; sound.engineOn = false }
        sound.menuMusic()
        if (showroom == null) garageVisible = false
        if (showroom == null) showroom = Showroom(engine, scene, view, camera, mats, settings, resources).also { it.setAspect(aspect) }
        showroom!!.setCar(CarDesigns.all[carIndex], Paints.all[paintIndex])
    }

    override fun toTitle() { ensureShowroom(); staticMenu = true; showroom!!.offsetX = 0f; showroom!!.offsetY = 0f; menu { Menus.title(this, this) } }
    override fun toCarPicker() {
        ensureShowroom()
        showroom!!.offsetX = 0f
        showroom!!.offsetY = 0.10f
        val picker = Menus.carPicker(this, this, carIndex) as FrameLayout
        setScreen(picker)
        layoutMenu = { Menus.carPicker(this, this, carIndex) }
        if (!garageVisible) revealGarage(picker)
    }

    /** Keep the illustrated cover up until the separate SurfaceView actually presents a garage frame. */
    private fun revealGarage(picker: FrameLayout) {
        val cover = Menus.loading(this, "Opening the garage…")
        cover.isClickable = true
        picker.addView(cover, FrameLayout.LayoutParams(-1,-1))
        val sample = android.graphics.Bitmap.createBitmap(16,12,android.graphics.Bitmap.Config.ARGB_8888)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val deadline = android.os.SystemClock.elapsedRealtime() + 12000
        fun poll() {
            if (!cover.isAttachedToWindow) return
            if (!surface.holder.surface.isValid || !uiHelper.isReadyToRender) {
                cover.postDelayed({ poll() },160)
                return
            }
            PixelCopy.request(surface,sample,{ result ->
                if (!cover.isAttachedToWindow) return@request
                val visible = result == PixelCopy.SUCCESS && (2 until 10).any { y ->
                    val rgb = sample.getPixel(8,y)
                    android.graphics.Color.red(rgb) + android.graphics.Color.green(rgb) + android.graphics.Color.blue(rgb) > 120
                }
                if (visible || android.os.SystemClock.elapsedRealtime() >= deadline) {
                    garageVisible = visible
                    cover.animate().alpha(0f).setDuration(180).withEndAction { picker.removeView(cover) }.start()
                } else cover.postDelayed({ poll() },160)
            },handler)
        }
        cover.post { poll() }
    }
    override fun toPaintPicker() { ensureShowroom(); showroom!!.offsetX = 0f; showroom!!.offsetY = 0.10f; menu { Menus.paintPicker(this, this, paintIndex) } }
    override fun toTrackPicker() { ensureShowroom(); showroom!!.offsetX = 0f; showroom!!.offsetY = 0f; menu { Menus.trackPicker(this, this) } }
    override fun toSettings() { ensureShowroom(); staticMenu = true; menu { Menus.settings(this, this) } }
    override fun pickCar(index: Int) { carIndex = index; paintIndex = carPaints[index]; showroom?.setCar(CarDesigns.all[index], Paints.all[paintIndex]) }
    override fun pickPaint(index: Int) { paintIndex = index; carPaints[carIndex] = index; showroom?.setPaint(Paints.all[index]) }
    override fun applySettings() {
        renderScale = 1f; overspent = 0; comfortable = 0; sizeRenderSurface()
        settings.save(); pickDisplayMode(); settings.apply(view, renderer) }

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
        staticMenu = false
        this.themeIndex = themeIndex
        setScreen(Menus.loading(this, "Building ${Themes.all[themeIndex].name}..."))
        // let the loading screen draw before the heavy build
        val theme = Themes.all[themeIndex]
        val token = ++loadGeneration
        val start = android.os.SystemClock.elapsedRealtime()
        thread(name = "course-mesh") {
            val path = com.vroombuddies.game.sim.TrackPath(theme.shape)
            val layout = SceneryLayout(path, theme).place()
            runOnUiThread {
            if (isDestroyed || isFinishing || token != loadGeneration) return@runOnUiThread
            showroom?.destroy(); showroom = null
            game?.destroy()
            settings.apply(view, renderer)
            if (!settings.usesScreenEffects) {
                postFx?.destroy(view)
                postFx = null
            } else if (postFx == null && mats.postFx != null) {
                postFx = PostFx(engine, mats.postFx).also { it.resize(surfaceW, surfaceH) }
            }
            game = GameScene(engine, scene, view, camera, mats, settings, Themes.all[themeIndex], CarDesigns.all[carIndex], Paints.all[paintIndex], sound, path, layout).also { it.setAspect(aspect) }
            Log.i("Vroom", "Course ready in ${android.os.SystemClock.elapsedRealtime() - start} ms")
            sound.engineOn = true; sound.raceMusic(Themes.all[themeIndex].name)
            resultShown = false; paused = false
            // draw a few hidden frames with every effect on (still behind the loading screen) so shaders,
            // shadow maps and post-processing buffers are all ready before the countdown appears
            samples = 0; lastSubmission = 0L; declined = 0; pacingSkips = 0
            warmFrames = WARM_FRAMES
            }
        }
    }

    private fun showHud() {
        val root = Ui.frame(this)
        val h = HudOverlay(this).also { it.track = game?.track }; root.addView(h, FrameLayout.LayoutParams(-1, -1)); hud = h
        val l = SteerButton(this, -1); val r = SteerButton(this, 1)
        root.addView(l, Ui.lp(Ui.dpi(Ui.STEER_SIZE), Ui.dpi(Ui.STEER_SIZE), Gravity.BOTTOM or Gravity.START, l = 16f, b = 14f))
        root.addView(r, Ui.lp(Ui.dpi(Ui.STEER_SIZE), Ui.dpi(Ui.STEER_SIZE), Gravity.BOTTOM or Gravity.START, l = Ui.STEER_SIZE + 24f, b = 14f))
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
            if (interval > 1 && (vsyncs++ % interval) != 0) { pacingSkips++; return }
            if (!ready || (staticMenu && showroomDrawn && game == null)) return
            val sc = swapChain ?: return
            if (!uiHelper.isReadyToRender) return
            val startBegin = System.nanoTime()
            if (!renderer.beginFrame(sc, frameTimeNanos)) { declined++; return }
            val startUpdate = System.nanoTime()
            val dt = if (lastNanos == 0L) 1f / 60f else ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0.001f, 0.05f)
            lastNanos = frameTimeNanos
            val g = game
            if (g != null && warmFrames > 0) {
                g.warming = true
                g.update(1f / 60f, 0f, false)
                g.post.force = postFx != null
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
            } else showroom?.update(dt)
            run {
                val startDraw = System.nanoTime()
                postFx?.render(renderer, view, game?.post) ?: renderer.render(view)
                renderer.endFrame()
                if (g != null && warmFrames == 0 && !paused) measureSubmission(frameTimeNanos, startUpdate - startBegin, startDraw - startUpdate, System.nanoTime() - startDraw, g.hud.phase) else lastSubmission = 0L
                if (g == null && !showroomDrawn) { showroomDrawn = true; Log.i("Vroom", "Showroom frame submitted") }
                if (g != null && warmFrames == 0 && !raceDrawn) { raceDrawn = true; Log.i("Vroom", "Race frame submitted") }
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
            surfaceW = width; surfaceH = height
            if (view.renderTarget != null) view.renderTarget = null
            postFx?.resize(width, height)
            game?.setAspect(aspect); showroom?.setAspect(aspect)
        }
    }
}
