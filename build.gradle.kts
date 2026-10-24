plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}

// build/ at the root holds the exported APKs (build/<versionName>/), so the root project's own
// Gradle output (problem reports and the like) goes under .gradle/ instead.
layout.buildDirectory = layout.projectDirectory.dir(".gradle/root-build")
