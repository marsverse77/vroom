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
        versionCode = 1
        versionName = "0.1.0"
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
}

dependencies {
    implementation("com.google.android.filament:filament-android:$filamentVersion")
    implementation("com.google.android.filament:filament-utils-android:$filamentVersion")
    implementation("com.google.android.filament:filamat-android:$filamentVersion")
    testImplementation("junit:junit:4.13.2")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
