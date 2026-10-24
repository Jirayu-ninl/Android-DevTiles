package dev.theiceji.debugtoggle

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Process
import android.view.Display
import kotlin.math.roundToInt
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Display size the way Developer options' "Smallest width" sets it: a target smallest width in dp
 * becomes a forced density for the current screen (density = shortest side px × 160 / dp), so one
 * preset gives the same layout class on the Find N6's cover and inner screens.
 *
 * The density is forced with IWindowManager.setForcedDisplayDensityForUser, which checks
 * WRITE_SECURE_SETTINGS (the one grant the app already needs). Writing Settings.Secure
 * display_density_forced alone does nothing until a reboot, so the hidden binder call is the only way.
 * The setter is max-target-o (reflection denied for targetSdk 36), hence HiddenApiBypass; the
 * getters are on the allowed list.
 */
object DisplaySize {
    const val MIN_DP = 320
    const val MAX_DP = 1200

    data class State(
        /** Density now in force. */
        val densityDpi: Int,
        /** The screen's own density, what Reset goes back to. */
        val defaultDpi: Int,
        val shortSidePx: Int,
    ) {
        val smallestWidthDp: Int get() = dpFor(densityDpi)
        val defaultWidthDp: Int get() = dpFor(defaultDpi)
        val isDefault: Boolean get() = densityDpi == defaultDpi

        fun dpFor(dpi: Int): Int = (shortSidePx * 160f / dpi).roundToInt()
        fun dpiFor(dp: Int): Int = (shortSidePx * 160f / dp).roundToInt()

        /** A preset counts as applied when its density is the one in force. */
        fun matches(preset: Preset): Boolean = dpiFor(preset.widthDp) == densityDpi
    }

    private val windowManager: Any? by lazy {
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/view/IWindowManager")
            Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null)
        }.getOrNull()
    }

    private fun call(name: String, vararg args: Int): Any? {
        val wm = windowManager ?: return null
        val types = Array(args.size) { Int::class.javaPrimitiveType!! }
        return wm.javaClass.getMethod(name, *types).invoke(wm, *args.toTypedArray())
    }

    private val userId: Int get() = Process.myUserHandle().hashCode()

    fun read(context: Context): State? {
        val display =
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val size = Point()
        @Suppress("DEPRECATION") // the logical size with any `wm size` override, which is what density is applied to
        display.getRealSize(size)
        return runCatching {
            val base = call("getBaseDisplayDensity", Display.DEFAULT_DISPLAY) as Int
            val initial = call("getInitialDisplayDensity", Display.DEFAULT_DISPLAY) as Int
            if (base <= 0 || initial <= 0) null else State(base, initial, minOf(size.x, size.y))
        }.getOrNull()
    }

    /** Forces the density for [widthDp]; false if the call isn't possible (permission, hidden API). */
    fun apply(context: Context, widthDp: Int): Boolean {
        val state = read(context) ?: return false
        val dpi = state.dpiFor(widthDp.coerceIn(MIN_DP, MAX_DP))
        return runCatching { call("setForcedDisplayDensityForUser", Display.DEFAULT_DISPLAY, dpi, userId) }.isSuccess
    }

    fun reset(): Boolean =
        runCatching { call("clearForcedDisplayDensityForUser", Display.DEFAULT_DISPLAY, userId) }.isSuccess
}
