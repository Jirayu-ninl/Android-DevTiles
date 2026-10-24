# Debug Toggle

Personal-use Android app (not for Play) with two Quick Settings tiles:

1. **USB debugging** turns USB debugging and Developer options off and on, so apps that refuse to run while debugging is
   on (banking apps) can be opened without a reboot.
   - **Off** writes `Settings.Global` `adb_wifi_enabled=0`, `adb_enabled=0`, `development_settings_enabled=0`.
   - **On** writes `development_settings_enabled=1`, `adb_enabled=1` (wireless debugging is left alone).
   - The tile follows `adb_enabled` (ContentObserver), so it stays right when changed elsewhere.
2. **Display size** steps through display size presets (default Mobile 411 dp, Tablet 600 dp, Desktop 840 dp, then back
   to the screen's default size unless a preset already is it). A preset is a smallest width in dp, like Developer
   options' "Smallest width": density = shortest side px × 160 / dp, so on a foldable the same preset gives the same
   layout class on either screen. Presets are edited in the app (tap to apply, long-press to edit, up to 5).
   - Set with `IWindowManager.setForcedDisplayDensityForUser` (checks `WRITE_SECURE_SETTINGS`), the same call as
     `adb shell wm density`. It is max-target-o hidden API, so the app uses
     [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass). Writing `display_density_forced` alone only
     takes effect after a reboot.
   - Tested on the Android 17 emulator (API 37): presets, the tile cycle and reset all work.

Long-press either tile to open the app. No Shizuku, no root, no Accessibility, no overlay. The only requirement is
`WRITE_SECURE_SETTINGS`, granted once from the PC:

```bash
adb shell pm grant dev.theiceji.debugtoggle android.permission.WRITE_SECURE_SETTINGS
```

Once the tile turns USB debugging off, adb is gone until you tap the tile again.

## Build

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleRelease assembleBenchmark assembleDebug
```

Every assemble copies its APK to `build/<versionName>/` at the project root (`debugtoggle-0.2.0-release.apk`, `-benchmark`,
`-debug`), and release also its R8 mapping (`debugtoggle-0.2.0-mapping.txt`). Bump `versionName` and `versionCode` for a
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
