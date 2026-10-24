package dev.theiceji.debugtoggle

import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(28.dp)

/** Settings.Global as Compose state: changes from anywhere (tile, Settings, adb) show up live. */
@Composable
private fun rememberGlobalSettingsVersion(): Int {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    version++
                }
            }
        context.contentResolver.registerContentObserver(Settings.Global.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return version
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val settingsVersion = rememberGlobalSettingsVersion()
    // The activity handles density changes itself, so a new density arrives here as a new configuration.
    val densityDpi = LocalConfiguration.current.densityDpi
    val granted = remember(settingsVersion) { DebugSettings.hasPermission(context) }
    val display = remember(densityDpi, settingsVersion) { DisplaySize.read(context) }
    var presets by remember { mutableStateOf(Presets.load(context)) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header()
            AnimatedVisibility(!granted) { PermissionCard() }
            DebugCard(settingsVersion)
            DisplayCard(
                state = display,
                presets = presets,
                onPresetsChange = {
                    presets = it
                    Presets.save(context, it)
                },
            )
            TilesCard()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(start = 4.dp, top = 24.dp, bottom = 4.dp)) {
        Text(
            "Debug Toggle",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "USB debugging and display size, one tap each",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "One-time setup",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Grant WRITE_SECURE_SETTINGS once from the PC. Both tiles need it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
                Text(
                    // One word per line rather than a break inside the permission name.
                    DebugSettings.GRANT_COMMAND.replace(" dev.", "\ndev.").replace(" android.", "\nandroid."),
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            FilledTonalButton(onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("grant", DebugSettings.GRANT_COMMAND))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(painterResource(R.drawable.ic_copy), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy command")
            }
        }
    }
}

@Composable
private fun DebugCard(settingsVersion: Int) {
    val context = LocalContext.current
    val on = remember(settingsVersion) { DebugSettings.isDebugging(context) }
    fun flag(key: String) = DebugSettings.read(context, key)
    val usb = remember(settingsVersion) { flag(DebugSettings.ADB) }
    val wifi = remember(settingsVersion) { flag(DebugSettings.ADB_WIFI) }
    val dev = remember(settingsVersion) { flag(DebugSettings.DEV) }

    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (on) scheme.tertiaryContainer else scheme.surfaceContainer, tween(300))
    val content = if (on) scheme.onTertiaryContainer else scheme.onSurface

    Surface(shape = CardShape, color = container) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(if (on) R.drawable.ic_usb else R.drawable.ic_shield, emphasized = on)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (on) "Debugging is on" else "Debugging is off",
                        style = MaterialTheme.typography.titleLarge,
                        color = content,
                    )
                    Text(
                        if (on) "Bank apps will refuse to open" else "Safe for bank apps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.75f),
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = { want ->
                        if (!DebugSettings.setDebugging(context, want)) {
                            Toast.makeText(context, "WRITE_SECURE_SETTINGS is not granted", Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlagRow("USB debugging", usb, content)
                FlagRow("Wireless debugging", wifi, content)
                FlagRow("Developer options", dev, content)
            }
        }
    }
}

@Composable
private fun FlagRow(label: String, value: Int?, color: Color) {
    val on = value == 1
    val dot by animateColorAsState(if (on) MaterialTheme.colorScheme.tertiary else color.copy(alpha = 0.25f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = color)
        Text(
            when (value) {
                null -> "unset"
                1 -> "On"
                else -> "Off"
            },
            style = MaterialTheme.typography.labelLarge,
            color = color.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun Badge(@DrawableRes icon: Int, emphasized: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (emphasized) scheme.tertiary else scheme.secondaryContainer)
    val fg = if (emphasized) scheme.onTertiary else scheme.onSecondaryContainer
    Box(Modifier.size(48.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = fg)
    }
}

/** Editing state for the preset dialog: index -1 adds a new one. */
private data class Editing(val index: Int, val preset: Preset)

@Composable
private fun DisplayCard(state: DisplaySize.State?, presets: List<Preset>, onPresetsChange: (List<Preset>) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Editing?>(null) }

    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(R.drawable.ic_devices, emphasized = false)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Display size", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            state == null -> "Not available on this device"
                            state.isDefault -> "Default size"
                            else -> "Default is ${state.defaultWidthDp} dp"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state != null && !state.isDefault) {
                    IconButton(onClick = { if (!DisplaySize.reset()) noPermission(context) }) {
                        Icon(painterResource(R.drawable.ic_restart), "Reset to default size")
                    }
                }
            }

            if (state != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${state.smallestWidthDp}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        " dp wide",
                        Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${state.densityDpi} dpi",
                        Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEachIndexed { i, preset ->
                        PresetTile(
                            preset = preset,
                            selected = state.matches(preset),
                            modifier = Modifier.weight(1f),
                            onClick = { if (!DisplaySize.apply(context, preset.widthDp)) noPermission(context) },
                            onLongClick = { editing = Editing(i, preset) },
                        )
                    }
                    if (presets.size < Presets.MAX) {
                        AddTile(onClick = { editing = Editing(-1, Preset("Custom", state.smallestWidthDp)) })
                    }
                }
                Text(
                    "Tap to switch · long-press to edit · the tile steps through them",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val edit = editing
    if (edit != null && state != null) {
        PresetDialog(
            initial = edit.preset,
            state = state,
            canDelete = edit.index >= 0 && presets.size > 1,
            onDismiss = { editing = null },
            onSave = { saved ->
                onPresetsChange(
                    if (edit.index < 0) presets + saved else presets.toMutableList().also { it[edit.index] = saved },
                )
                editing = null
            },
            onDelete = {
                onPresetsChange(presets.filterIndexed { i, _ -> i != edit.index })
                editing = null
            },
        )
    }
}

private fun noPermission(context: Context) =
    Toast.makeText(context, "WRITE_SECURE_SETTINGS is not granted", Toast.LENGTH_LONG).show()

@DrawableRes
private fun Preset.Kind.icon() =
    when (this) {
        Preset.Kind.Phone -> R.drawable.ic_phone
        Preset.Kind.Tablet -> R.drawable.ic_tablet
        Preset.Kind.Desktop -> R.drawable.ic_desktop
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetTile(
    preset: Preset,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainerHighest, tween(250))
    val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, tween(250))
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(preset.kind.icon()), null, Modifier.size(26.dp), tint = fg)
        Text(
            preset.name,
            style = MaterialTheme.typography.titleSmall,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${preset.widthDp} dp", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.7f))
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Box(
        Modifier
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick)
            .padding(PaddingValues(vertical = 34.dp, horizontal = 12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_add), "Add preset", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PresetDialog(
    initial: Preset,
    state: DisplaySize.State,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (Preset) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var dp by remember { mutableIntStateOf(initial.widthDp.coerceIn(DisplaySize.MIN_DP, DisplaySize.MAX_DP)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (canDelete || initial.name != "Custom") "Edit preset" else "New preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$dp", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text(" dp wide", Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${state.dpiFor(dp)} dpi here",
                        Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = dp.toFloat(),
                    onValueChange = { dp = it.roundToInt() },
                    valueRange = DisplaySize.MIN_DP.toFloat()..DisplaySize.MAX_DP.toFloat(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(360, 411, 600, 840).forEach { v ->
                        TextButton(onClick = { dp = v }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("$v") }
                    }
                }
                Text(
                    "Under 600 dp is a phone layout, 600–839 tablet, 840 and up desktop-class.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Preset(name.trim().ifEmpty { "$dp dp" }, dp)) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (canDelete) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun TilesCard() {
    val context = LocalContext.current
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quick Settings", style = MaterialTheme.typography.titleLarge)
            Text(
                "Add the tiles to switch without opening the app. Long-press a tile to come back here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TileButton(R.drawable.ic_usb, "Debugging", Modifier.weight(1f)) {
                    addTile(context, DebugTileService::class.java, R.string.tile_label, R.drawable.ic_usb)
                }
                TileButton(R.drawable.ic_devices, "Display size", Modifier.weight(1f)) {
                    addTile(context, DisplayTileService::class.java, R.string.display_tile_label, R.drawable.ic_devices)
                }
            }
        }
    }
}

@Composable
private fun TileButton(@DrawableRes icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(vertical = 14.dp)) {
        Icon(painterResource(icon), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1)
    }
}

private fun addTile(context: Context, service: Class<*>, label: Int, icon: Int) {
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, service),
        context.getString(label),
        Icon.createWithResource(context, icon),
        context.mainExecutor,
    ) { }
}
