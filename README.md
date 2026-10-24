# Debug Toggle

Personal-use Android app (not for Play): a Quick Settings tile that turns USB debugging and Developer options off and on,
so apps that refuse to run while debugging is on (banking apps) can be opened without a reboot.

- **Off** writes `Settings.Global` `adb_wifi_enabled=0`, `adb_enabled=0`, `development_settings_enabled=0`.
- **On** writes `development_settings_enabled=1`, `adb_enabled=1` (wireless debugging is left alone).
- The tile follows `adb_enabled` (ContentObserver), so it stays right when changed elsewhere.

No Shizuku, no root, no Accessibility, no overlay. The only requirement is `WRITE_SECURE_SETTINGS`, granted once from the PC:

```bash
adb shell pm grant dev.theiceji.debugtoggle android.permission.WRITE_SECURE_SETTINGS
```

Once the tile turns USB debugging off, adb is gone until you tap the tile again.

## Build

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

AGP 9.4.0, Gradle 9.7.1, Kotlin (built into AGP), minSdk 34, targetSdk 36, no dependencies.
