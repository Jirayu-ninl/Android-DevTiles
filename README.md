# Debug Toggle

Personal-use Android app (not for Play) with two Quick Settings tiles:

1. **USB debugging** turns USB debugging and Developer options off and on, so apps that refuse to run while debugging is
   on (banking apps) can be opened without a reboot.
   - **Off** writes `Settings.Global` `adb_wifi_enabled=0`, `adb_enabled=0`, `development_settings_enabled=0`.
   - **On** writes `development_settings_enabled=1`, `adb_enabled=1` (wireless debugging is left alone).
   - The tile follows `adb_enabled` (ContentObserver), so it stays right when changed elsewhere.
2. **Display size** steps through the current screen's display size presets. A preset is a smallest width in dp, like
   Developer options' "Smallest width": density = shortest side px × 160 / dp, truncated both ways as Settings does
   (on the Find N6, 412 dp on the cover and 813 dp inside are both 442 dpi).
   - **Per screen.** Android keeps a forced density per physical screen, so a foldable's cover and inner screens each
     have their own presets and default (the screen is told apart by its panel resolution; near square = inner).
     First-run presets: cover Mobile 412 (default) / Tablet 600 / Desktop 840; inner Default 813 / Dense 1183 /
     Densest 1657. The inner ones are the cover's steps at the same scale, so the same density on both screens:
     442 dpi (default), 304 dpi (×1.45), 217 dpi (×2.04).
   - In the app: tap a preset to apply it, long-press to edit (name, dp, default for this screen), up to 5 per screen;
     the reset button goes back to the screen's default preset. The tile goes to the next preset, or to the default
     from a size no preset has.
   - Set with `IWindowManager.setForcedDisplayDensityForUser` (checks `WRITE_SECURE_SETTINGS`), the same call as
     `adb shell wm density`. It is max-target-o hidden API, so the app uses
     [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass). Writing `display_density_forced` alone only
     takes effect after a reboot.
   - Tested on the Find N6 (Android 16, both screens) and the Android 17 emulator (API 37).

Long-press either tile to open the app. No Shizuku, no root, no Accessibility, no overlay. The only requirement is
`WRITE_SECURE_SETTINGS`, granted once from the PC:

```bash
adb shell pm grant dev.theiceji.debugtoggle android.permission.WRITE_SECURE_SETTINGS
```

On ColorOS 16 the grant fails with "Neither user 2000 nor current process has GRANT_RUNTIME_PERMISSIONS" until
Developer options → **Disable system optimization** is on. The grant survives app updates.

Once the tile turns USB debugging off, adb is gone until you tap the tile again.

## Build

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleRelease assembleBenchmark assembleDebug
```

Every assemble copies its APK to `build/<versionName>/` at the project root (`debugtoggle-0.2.2-release.apk`, `-benchmark`,
`-debug`), and release also its R8 mapping (`debugtoggle-0.2.2-mapping.txt`). Bump `versionName` and `versionCode` for a
new version, or the last one is overwritten. Gradle's own root-project output goes to `.gradle/root-build` so `build/`
only holds versions.

- **release**: R8, signed with `~/.keystores/debugtoggle-release.jks`; the passwords and alias are in
  `~/.keystores/debugtoggle-release.properties` (read by `app/build.gradle.kts`, never in the repo). Without that file
  release builds unsigned.
- **benchmark**: the release build signed with the debug key, profileable.
- **debug**: debug key.

Release and debug are signed with different keys: switching between them on a phone means uninstalling first (and
granting `WRITE_SECURE_SETTINGS` again).

AGP 9.4.0, Gradle 9.7.1, Kotlin (built into AGP) with Compose (BOM 2026.09.00, Material 3 dynamic colour), minSdk 34,
targetSdk 36.
