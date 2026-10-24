package dev.theiceji.debugtoggle

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tap to step through the display size presets (then back to the default size). */
class DisplayTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val state = DisplaySize.read(this)
        if (!DebugSettings.hasPermission(this) || state == null) {
            openApp()
            return
        }
        val next = Presets.next(Presets.load(this), state)
        val ok = if (next == null) DisplaySize.reset() else DisplaySize.apply(this, next.widthDp)
        if (!ok) openApp()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val state = DisplaySize.read(this)
        tile.state = if (state == null || state.isDefault) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.subtitle =
            when {
                !DebugSettings.hasPermission(this) -> "Needs permission"
                state == null -> "Unavailable"
                else -> {
                    val preset = Presets.load(this).firstOrNull { state.matches(it) }
                    when {
                        preset != null -> "${preset.name} · ${preset.widthDp} dp"
                        state.isDefault -> "Default · ${state.smallestWidthDp} dp"
                        else -> "${state.smallestWidthDp} dp"
                    }
                }
            }
        tile.updateTile()
    }

    private fun openApp() {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startActivityAndCollapse(pi)
    }
}
