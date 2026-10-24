package dev.theiceji.debugtoggle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A named smallest width, e.g. Tablet = 600 dp. */
data class Preset(val name: String, val widthDp: Int) {
    /** Which glyph to draw, from Android's own window size classes (600 and 840 dp). */
    val kind: Kind get() = when {
        widthDp < 600 -> Kind.Phone
        widthDp < 840 -> Kind.Tablet
        else -> Kind.Desktop
    }

    enum class Kind { Phone, Tablet, Desktop }
}

/** One screen's presets, in the order the tile cycles through them; [defaultIndex] is what Reset goes back to. */
data class ScreenPresets(val presets: List<Preset>, val defaultIndex: Int) {
    val default: Preset get() = presets[defaultIndex]

    fun replace(index: Int, preset: Preset) = copy(presets = presets.toMutableList().also { it[index] = preset })

    fun add(preset: Preset) = copy(presets = presets + preset)

    fun remove(index: Int): ScreenPresets {
        val left = presets.filterIndexed { i, _ -> i != index }
        val default = when {
            index == defaultIndex -> 0
            index < defaultIndex -> defaultIndex - 1
            else -> defaultIndex
        }
        return ScreenPresets(left, default)
    }

    /** What the tile switches to: the preset after the one in force; from a size no preset has, the default. */
    fun next(state: DisplaySize.State): Preset {
        val current = presets.indexOfFirst { state.matches(it) }
        return if (current < 0) default else presets[(current + 1) % presets.size]
    }
}

/** Display size presets, kept per screen (a foldable's cover and inner screens each have their own). */
object Presets {
    const val MAX = 5
    private const val PREFS = "presets"

    /**
     * First-run presets. The Find N6 runs both screens at 442 dpi: 412 dp on the cover, 813 dp inside.
     * The inner presets take the cover's steps at the same scale, i.e. the same density: Tablet 600 dp
     * is 304 dpi (×1.45 denser), Desktop 840 dp is 217 dpi (×2.04), which inside is 1183 and 1657 dp.
     */
    private fun defaults(screen: DisplaySize.Screen) =
        if (screen.kind == DisplaySize.Screen.Kind.Inner) {
            ScreenPresets(listOf(Preset("Default", 813), Preset("Dense", 1183), Preset("Densest", 1657)), 0)
        } else {
            ScreenPresets(listOf(Preset("Mobile", 412), Preset("Tablet", 600), Preset("Desktop", 840)), 0)
        }

    fun load(context: Context, screen: DisplaySize.Screen): ScreenPresets {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(screen.key, null)
            ?: return defaults(screen)
        return runCatching {
            val o = JSONObject(json)
            val array = o.getJSONArray("list")
            val presets = List(array.length()) { i ->
                val p = array.getJSONObject(i)
                Preset(p.getString("name"), p.getInt("dp"))
            }
            ScreenPresets(presets, o.getInt("default").coerceIn(presets.indices))
        }.getOrDefault(defaults(screen))
    }

    fun save(context: Context, screen: DisplaySize.Screen, value: ScreenPresets) {
        val array = JSONArray()
        value.presets.forEach { array.put(JSONObject().put("name", it.name).put("dp", it.widthDp)) }
        val json = JSONObject().put("default", value.defaultIndex).put("list", array).toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(screen.key, json).apply()
    }
}
