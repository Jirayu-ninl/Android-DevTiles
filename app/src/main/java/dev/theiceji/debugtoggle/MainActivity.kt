package dev.theiceji.debugtoggle

import android.app.Activity
import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.database.ContentObserver
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var grantHint: TextView
    private lateinit var copyButton: Button

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        status = TextView(this).apply { textSize = 15f; typeface = Typeface.MONOSPACE }
        grantHint = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, 0)
        }
        copyButton = button("Copy grant command") {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("grant", DebugSettings.GRANT_COMMAND))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            addView(TextView(this@MainActivity).apply { text = "Debug Toggle"; textSize = 24f })
            addView(status.also { it.setPadding(0, dp(16), 0, 0) })
            addView(grantHint)
            addView(copyButton)
            addView(button("Turn OFF  (USB, wireless, developer options)") { set(false) })
            addView(button("Turn ON  (developer options, USB debugging)") { set(true) })
            addView(button("Add Quick Settings tile") { addTile() })
        }
        val scroll = ScrollView(this).apply {
            addView(column)
            setOnApplyWindowInsetsListener { v, insets ->
                val b = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                insets
            }
        }
        setContentView(scroll)
    }

    override fun onStart() {
        super.onStart()
        contentResolver.registerContentObserver(Settings.Global.CONTENT_URI, true, observer)
        refresh()
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(observer)
        super.onStop()
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun set(on: Boolean) {
        if (!DebugSettings.setDebugging(this, on)) {
            Toast.makeText(this, "WRITE_SECURE_SETTINGS is not granted", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun addTile() {
        val bar = getSystemService(StatusBarManager::class.java)
        bar.requestAddTileService(
            ComponentName(this, DebugTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_usb),
            mainExecutor,
        ) { }
    }

    private fun refresh() {
        val granted = DebugSettings.hasPermission(this)
        fun v(key: String) = DebugSettings.read(this, key)?.toString() ?: "unset"
        status.text =
            "WRITE_SECURE_SETTINGS: ${if (granted) "granted" else "NOT granted"}\n\n" +
                "adb_enabled (USB debugging): ${v(DebugSettings.ADB)}\n" +
                "adb_wifi_enabled (Wireless debugging): ${v(DebugSettings.ADB_WIFI)}\n" +
                "development_settings_enabled (Developer options): ${v(DebugSettings.DEV)}"
        val visibility = if (granted) View.GONE else View.VISIBLE
        grantHint.visibility = visibility
        copyButton.visibility = visibility
        grantHint.text = "Run once from the PC:\n${DebugSettings.GRANT_COMMAND}"
    }
}
