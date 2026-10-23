plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.glassvpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.glassvpn"
        minSdk = 29
        targetSdk = 35
        versionCode = 11
        versionName = "0.6.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")


    // ---- Нативные ядра ----
    // tun2socks: app/libs/hev-socks5-tunnel.aar (скачивается scripts/fetch-natives.sh)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    // AmneziaWG 2.0: официальный amnezia-vpn/amneziawg-android, собирается scripts/build-awg.sh -> app/libs/amneziawg-tunnel.aar
    // (транзитивные зависимости локального AAR gradle сам не подтянет, поэтому перечислены здесь)
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.collection:collection:1.5.0")
    implementation("com.getkeepsafe.relinker:relinker:1.4.5")
    // Xray-core: бинарник app/src/main/jniLibs/<abi>/libxray.so (тоже из scripts/fetch-natives.sh)
}
