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

/** The display size presets, in the order the tile cycles through them. */
object Presets {
    const val MAX = 5
    private const val PREFS = "presets"
    private const val KEY = "list"

    val defaults = listOf(Preset("Mobile", 411), Preset("Tablet", 600), Preset("Desktop", 840))

    fun load(context: Context): List<Preset> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return defaults
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Preset(o.getString("name"), o.getInt("dp"))
            }
        }.getOrDefault(defaults)
    }

    fun save(context: Context, presets: List<Preset>) {
        val array = JSONArray()
        presets.forEach { array.put(JSONObject().put("name", it.name).put("dp", it.widthDp)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    /**
     * What the tile switches to: the preset after the one in force, then the screen's default size
     * (null) after the last one, unless a preset already is the default. From a custom size, the first.
     */
    fun next(presets: List<Preset>, state: DisplaySize.State): Preset? {
        val cycle: List<Preset?> = if (presets.any { state.dpiFor(it.widthDp) == state.defaultDpi }) presets else presets + null
        if (cycle.isEmpty()) return null
        val current =
            if (state.isDefault && null in cycle) cycle.indexOf(null) else cycle.indexOfFirst { it != null && state.matches(it) }
        return cycle[(current + 1) % cycle.size]
    }
}
