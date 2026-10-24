package dev.theiceji.debugtoggle

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/** Reads and writes the three Settings.Global flags that bank apps look at. */
object DebugSettings {
    const val ADB = Settings.Global.ADB_ENABLED
    const val ADB_WIFI = "adb_wifi_enabled"
    const val DEV = Settings.Global.DEVELOPMENT_SETTINGS_ENABLED

    const val GRANT_COMMAND =
        "adb shell pm grant dev.theiceji.debugtoggle android.permission.WRITE_SECURE_SETTINGS"

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Current value, or null if the key has never been written. */
    fun read(context: Context, key: String): Int? {
        val cr = context.contentResolver
        return try {
            Settings.Global.getInt(cr, key)
        } catch (_: Settings.SettingNotFoundException) {
            null
        }
    }

    fun isDebugging(context: Context): Boolean = read(context, ADB) == 1

    /**
     * Off: wireless debugging, USB debugging, Developer options. On: Developer options, USB debugging
     * (wireless debugging is left alone). Returns false if the permission is missing.
     */
    fun setDebugging(context: Context, on: Boolean): Boolean {
        val cr = context.contentResolver
        return try {
            if (on) {
                Settings.Global.putInt(cr, DEV, 1)
                Settings.Global.putInt(cr, ADB, 1)
            } else {
                Settings.Global.putInt(cr, ADB_WIFI, 0)
                Settings.Global.putInt(cr, ADB, 0)
                Settings.Global.putInt(cr, DEV, 0)
            }
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
