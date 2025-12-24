

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    kotlin("kapt") // <-- CHANGE THIS LINE
}

android {
    namespace = "com.kooduXA.opendash"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.kooduXA.opendash"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // --- Core Android & Kotlin ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // --- Jetpack Compose (UI) ---
    // BOM (Bill of Materials) ensures all compose libs use compatible versions
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Lifecycle & ViewModel support for Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Icons (Note: Extended icons significantly increase APK size)
    implementation(libs.androidx.compose.material.icons.extended)

    // --- Data Storage & Networking ---
    // Preferences DataStore (Key-Value storage)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation(libs.androidx.datastore.core)

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- Media & Image Loading ---
    // Coil: Image and Video thumbnail loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // VLC: Powerful video playback engine
    implementation("org.videolan.android:libvlc-all:3.6.0")
    implementation(libs.play.services.appsearch)
    implementation(libs.androidx.tracing.perfetto.handshake)

    // --- Hilt ---
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)


    // --- Debug & Tooling ---
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)



}