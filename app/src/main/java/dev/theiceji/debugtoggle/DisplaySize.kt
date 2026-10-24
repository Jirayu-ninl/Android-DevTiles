package dev.theiceji.debugtoggle

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Process
import android.view.Display
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Display size the way Developer options' "Smallest width" sets it: a target smallest width in dp
 * becomes a forced density for the current screen, density = shortest side px × 160 / dp, both ways
 * truncated like Settings does (so 412 dp on the Find N6 cover and 813 dp inside are both 442 dpi).
 *
 * Android keeps a forced density per physical screen, so on a foldable the cover and inner screens
 * each keep their own; [Screen] tells them apart and each has its own presets.
 *
 * The density is forced with IWindowManager.setForcedDisplayDensityForUser, which checks
 * WRITE_SECURE_SETTINGS (the one grant the app already needs). Writing Settings.Secure
 * display_density_forced alone does nothing until a reboot, so the hidden binder call is the only way.
 * The setter is max-target-o (reflection denied for targetSdk 36), hence HiddenApiBypass; the
 * getters are on the allowed list.
 */
object DisplaySize {
    const val MIN_DP = 320
    const val MAX_DP = 1400

    /** The physical screen behind the default display, keyed by its panel resolution. */
    data class Screen(val key: String, val kind: Kind) {
        enum class Kind { Cover, Inner, Single }

        val label: String get() = when (kind) {
            Kind.Cover -> "Cover screen"
            Kind.Inner -> "Inner screen"
            Kind.Single -> "Screen"
        }
    }

    data class State(
        val screen: Screen,
        /** Density now in force. */
        val densityDpi: Int,
        val shortSidePx: Int,
    ) {
        val smallestWidthDp: Int get() = dpFor(densityDpi)

        fun dpFor(dpi: Int): Int = shortSidePx * 160 / dpi
        fun dpiFor(dp: Int): Int = shortSidePx * 160 / dp

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

    private fun screenOf(context: Context, display: Display): Screen {
        val mode = display.mode
        val w = minOf(mode.physicalWidth, mode.physicalHeight)
        val h = maxOf(mode.physicalWidth, mode.physicalHeight)
        val foldable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
        // A foldable's inner screen is near square (Find N6 2248×2480), the cover tall (1140×2616).
        val kind = when {
            !foldable -> Screen.Kind.Single
            h.toFloat() / w < 1.5f -> Screen.Kind.Inner
            else -> Screen.Kind.Cover
        }
        return Screen("${w}x$h", kind)
    }

    fun read(context: Context): State? {
        val display =
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val size = Point()
        @Suppress("DEPRECATION") // the logical size with any `wm size` override, which is what density is applied to
        display.getRealSize(size)
        return runCatching {
            val base = call("getBaseDisplayDensity", Display.DEFAULT_DISPLAY) as Int
            if (base <= 0) null else State(screenOf(context, display), base, minOf(size.x, size.y))
        }.getOrNull()
    }

    /** Forces the density for [widthDp]; false if the call isn't possible (permission, hidden API). */
    fun apply(context: Context, widthDp: Int): Boolean {
        val state = read(context) ?: return false
        val dpi = state.dpiFor(widthDp.coerceIn(MIN_DP, MAX_DP))
        return runCatching { call("setForcedDisplayDensityForUser", Display.DEFAULT_DISPLAY, dpi, userId) }.isSuccess
    }
}
