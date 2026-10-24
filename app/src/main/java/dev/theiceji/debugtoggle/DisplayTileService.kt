package dev.theiceji.debugtoggle

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tap to step through the current screen's display size presets. */
class DisplayTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val state = DisplaySize.read(this)
        if (!DebugSettings.hasPermission(this) || state == null) {
            openApp()
            return
        }
        val next = Presets.load(this, state.screen).next(state)
        if (!DisplaySize.apply(this, next.widthDp)) openApp()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val state = DisplaySize.read(this)
        val screen = state?.let { Presets.load(this, it.screen) }
        tile.state = if (state == null || screen == null || state.matches(screen.default)) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.subtitle =
            when {
                !DebugSettings.hasPermission(this) -> "Needs permission"
                state == null || screen == null -> "Unavailable"
                else -> {
                    val preset = screen.presets.firstOrNull { state.matches(it) }
                    if (preset != null) "${preset.name} · ${preset.widthDp} dp" else "${state.smallestWidthDp} dp"
                }
            }
        tile.updateTile()
    }

    private fun openApp() {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startActivityAndCollapse(pi)
    }
}
