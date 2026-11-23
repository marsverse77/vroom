plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val filamentVersion = "1.81.0"

android {
    namespace = "com.vroombuddies.game"
    compileSdkVersion = "android-37.0"
    defaultConfig {
        applicationId = "com.vroombuddies.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the APK installs straight away; swap in a real key before any store upload.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging { jniLibs { useLegacyPackaging = true } }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.defaultRepositoryUrl", "https://maven-central.storage-download.googleapis.com/maven2")
            // set ROBOLECTRIC_DEPS to a folder holding android-all-instrumented jars to run without downloads
            // screenshot export (tools/preview) only runs when asked for
            if (System.getenv("PREVIEW_DIR") == null) it.exclude("**/Preview*")
            System.getenv("ROBOLECTRIC_DEPS")?.let { d -> it.systemProperty("robolectric.offline", "true"); it.systemProperty("robolectric.dependency.dir", d) }
        }
    }
}

dependencies {
    implementation("com.google.android.filament:filament-android:$filamentVersion")
    implementation("com.google.android.filament:filament-utils-android:$filamentVersion")
    implementation("com.google.android.filament:filamat-android:$filamentVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
