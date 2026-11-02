plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "de.filmregal"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.filmregal"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        // Galaxy Tab S9+ ist 64-Bit-ARM – hält das APK klein
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Fester Schlüssel, damit sich neue Builds über die alte Version installieren lassen
    signingConfigs {
        getByName("debug") {
            storeFile = file("filmregal-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
            pickFirsts += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("io.coil-kt:coil-compose:2.7.0")          // Poster laden
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")         // NAS / SMB1 bis SMB3
    implementation("org.videolan.android:libvlc-all:3.5.1")  // Player inkl. AV1
}
