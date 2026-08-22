import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.1.10"
    id("com.google.devtools.ksp") version "2.1.10-1.0.29"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
}

android {
    namespace  = "fr.bonobo.phonezen"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.bonobo.phonezen"
        minSdk        = 24
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.4e"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── Supabase : lecture depuis local.properties ──
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("supabase_url", "")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "")}\"")
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/DEPENDENCIES"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ── Ktor (client HTTP pour Supabase) ──
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    val composeBom = platform("androidx.compose:compose-bom:2025.03.00")
    implementation(composeBom)

    // Core
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.compose.foundation:foundation:1.10.5")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.graphics:graphics-path:1.1.0-rc01")

    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // WorkManager Kotlin
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // JavaMail pour Android (IMAP)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    implementation("com.google.android.gms:play-services-location:21.3.0")

}
