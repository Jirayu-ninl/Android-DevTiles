import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing lives outside the repo: ~/.keystores/debugtoggle-release.properties names the
// .jks next to it (storeFile, storePassword, keyAlias, keyPassword). Without it release is unsigned.
val signingProps =
    File(System.getProperty("user.home"), ".keystores/debugtoggle-release.properties")
        .takeIf { it.isFile }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } to f.parentFile }

android {
    namespace = "dev.theiceji.debugtoggle"
    compileSdk = 37
    // Without it AGP packs Compose's libandroidx.graphics.path.so unstripped.
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "dev.theiceji.debugtoggle"
        minSdk = 34
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1"
    }

    signingConfigs {
        if (signingProps != null) {
            val (props, dir) = signingProps
            create("release") {
                storeFile = File(dir, props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release code signed with the debug key, for profiling on a device.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isProfileable = true
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Every assemble of debug, release or benchmark also copies its APK to build/<versionName>/ at the
// project root (debugtoggle-0.2.0-release.apk), and release its R8 mapping
// (debugtoggle-0.2.0-mapping.txt). Bump versionName for a new version, or the last one is overwritten.
androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val version = android.defaultConfig.versionName ?: "unversioned"
        // Plain strings: the rename lambda is stored in the configuration cache, the variant can't be.
        val apkName = "debugtoggle-$version-${variant.name}.apk"
        val mappingName = "debugtoggle-$version-mapping.txt"
        val export =
            tasks.register<Copy>("export${name}Apk") {
                from(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)) { include("*.apk") }
                if (variant.buildType == "release") {
                    from(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.OBFUSCATION_MAPPING_FILE))
                }
                into(rootProject.layout.projectDirectory.dir("build/$version"))
                rename { if (it.endsWith(".apk")) apkName else mappingName }
            }
        tasks.matching { it.name == "assemble$name" }.configureEach { finalizedBy(export) }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    // IWindowManager.setForcedDisplayDensityForUser is max-target-o hidden API (DisplaySize).
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.2")
}
