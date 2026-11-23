package com.vroombuddies.game

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Startup diagnostics, so a crash on a phone we can't test on can still be explained:
 *  - every startup step leaves a breadcrumb (saved at once, so it survives even a native crash),
 *  - a Java crash writes its full stack trace to a file,
 *  - the next launch shows what happened on screen: the last step reached, the crash text,
 *    Android's own record of how the app died, and the device and GPU, for a screenshot.
 */
object Diagnostics {
    private const val PREFS = "diag"
    private lateinit var appCtx: Context
    private val steps = ArrayList<String>()

    private fun prefs() = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun crashFile() = File(appCtx.filesDir, "last-crash.txt")

    fun install(ctx: Context) {
        appCtx = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
                crashFile().writeText("Thread: ${t.name}\nStep: ${prefs().getString("step", "?")}\n\n$sw")
            } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
    }

    /** Record that startup reached [name]. Written synchronously: it must survive a crash right after. */
    fun step(name: String) {
        Log.i("Vroom", "step: $name")
        synchronized(steps) { steps.add(name); if (steps.size > 12) steps.removeAt(0) }
        try { prefs().edit().putString("step", name).putString("steps", synchronized(steps) { steps.joinToString(" > ") }).commit() } catch (_: Throwable) {}
    }

    /** Start of a launch: true if the previous launch never finished starting. */
    fun beginLaunch(): Boolean {
        val crashed = prefs().getBoolean("starting", false) || crashFile().exists()
        prefs().edit().putBoolean("starting", true).commit()
        return crashed
    }

    /** The game reached the title screen and kept drawing: clear the crash record. */
    fun launchOk() {
        prefs().edit().putBoolean("starting", false).remove("steps").remove("step").commit()
        crashFile().delete()
    }

    /** Everything we know about the last failed launch, as plain text. */
    fun report(extra: String? = null): String {
        val sb = StringBuilder()
        val p = prefs()
        sb.append("Vroom Buddies ${BuildConfigInfo.version}\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n")
        if (Build.VERSION.SDK_INT >= 31) sb.append("Chip: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}\n")
        sb.append("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}\n")
        try {
            val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            sb.append("Memory: ${mi.totalMem / 1048576} MB total, ${mi.availMem / 1048576} MB free, app limit ${am.largeMemoryClass} MB\n")
            sb.append("OpenGL ES (system): ${am.deviceConfigurationInfo.glEsVersion}\n")
        } catch (_: Throwable) {}
        sb.append("GPU: ${gpuInfo()}\n\n")
        sb.append("Last step reached: ${p.getString("step", "none")}\n")
        sb.append("Steps: ${p.getString("steps", "none")}\n")
        extra?.let { sb.append("\nError now:\n$it\n") }
        exitReasons(sb)
        try { if (crashFile().exists()) sb.append("\nCrash text:\n").append(crashFile().readText().take(6000)).append('\n') } catch (_: Throwable) {}
        return sb.toString()
    }

    private fun exitReasons(sb: StringBuilder) {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            val list = am.getHistoricalProcessExitReasons(appCtx.packageName, 0, 3)
            if (list.isEmpty()) return
            sb.append("\nHow Android saw the last runs end:\n")
            for (e in list) {
                val reason = when (e.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "Java crash"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
                    ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
                    ApplicationExitInfo.REASON_SIGNALED -> "killed by signal ${e.status}"
                    ApplicationExitInfo.REASON_EXIT_SELF -> "closed itself"
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "closed by user"
                    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "too much resource use"
                    else -> "reason ${e.reason}"
                }
                sb.append("- ${fmt.format(Date(e.timestamp))}: $reason, status ${e.status}, memory ${e.pss / 1024} MB")
                e.description?.let { sb.append(", \"").append(it.take(300)).append('"') }
                sb.append('\n')
            }
        } catch (_: Throwable) {}
    }

    /** GPU name and driver version from a throwaway OpenGL ES context (no Filament involved). */
    private fun gpuInfo(): String = try {
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2); EGL14.eglInitialize(dpy, ver, 0, ver, 1)
        val cfgs = arrayOfNulls<android.opengl.EGLConfig>(1); val n = IntArray(1)
        EGL14.eglChooseConfig(dpy, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40 /* ES3 */, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE), 0, cfgs, 0, 1, n, 0)
        val ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        val surf = EGL14.eglCreatePbufferSurface(dpy, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
        val s = "${GLES20.glGetString(GLES20.GL_RENDERER)} | ${GLES20.glGetString(GLES20.GL_VERSION)}"
        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(dpy, surf); EGL14.eglDestroyContext(dpy, ctx)
        s
    } catch (t: Throwable) { "unknown (${t.javaClass.simpleName})" }

    /** Full-screen panel with the report and buttons (plain Android views, nothing from the game). */
    fun panel(ctx: Context, title: String, text: String, buttons: List<Pair<String, () -> Unit>>): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xff121419.toInt()); setPadding(32, 24, 32, 24) }
        col.addView(TextView(ctx).apply { this.text = title; textSize = 20f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) })
        col.addView(TextView(ctx).apply {
            this.text = "Please take a screenshot of this screen and send it in the chat."
            textSize = 14f; setTextColor(0xffffcc66.toInt()); setPadding(0, 8, 0, 8)
        })
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START }
        for ((label, action) in buttons) row.addView(Button(ctx).apply { this.text = label; setOnClickListener { action() } })
        col.addView(row)
        val scroll = ScrollView(ctx)
        scroll.addView(TextView(ctx).apply { this.text = text; textSize = 12f; setTextColor(0xffdddddd.toInt()); typeface = Typeface.MONOSPACE; setTextIsSelectable(true) })
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return col
    }
}

/** Version label for reports. */
object BuildConfigInfo { const val version = "0.1.1 (diagnostic)" }
