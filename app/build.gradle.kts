plugins {
    id("com.android.application")
}

android {
    namespace = "dev.theiceji.debugtoggle"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.theiceji.debugtoggle"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
