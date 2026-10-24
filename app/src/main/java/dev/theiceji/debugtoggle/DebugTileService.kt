package dev.theiceji.debugtoggle

import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class DebugTileService : TileService() {
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }

    override fun onStartListening() {
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(DebugSettings.ADB),
            false,
            observer,
        )
        refresh()
    }

    override fun onStopListening() {
        contentResolver.unregisterContentObserver(observer)
    }

    override fun onClick() {
        if (!DebugSettings.hasPermission(this)) {
            openApp()
            return
        }
        val turnOn = !DebugSettings.isDebugging(this)
        // Turning debugging on from a locked screen would defeat the lock; turning it off is always fine.
        if (turnOn && isLocked) unlockAndRun { apply(true) } else apply(turnOn)
    }

    private fun apply(on: Boolean) {
        if (!DebugSettings.setDebugging(this, on)) openApp()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val granted = DebugSettings.hasPermission(this)
        val on = DebugSettings.isDebugging(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle =
            when {
                !granted -> "Needs permission"
                on -> "On"
                else -> "Off"
            }
        tile.updateTile()
    }

    private fun openApp() {
        val pi =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        startActivityAndCollapse(pi)
    }
}
