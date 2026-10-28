import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Credenciales de firma de release: local.properties (ignorado por git) o
// variables de entorno. El keystore vive FUERA del repo y debe respaldarse
// aparte; si se pierde, no se puede volver a publicar bajo la misma identidad.
// Ver docs/conventions/build-test-release.md.
val signingProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}

fun signingProperty(name: String): String? =
    signingProperties.getProperty(name) ?: System.getenv(name)

android {
    namespace = "com.example.projectacc"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.projectacc"
        minSdk = 24
        targetSdk = 36
        versionCode = 69
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Sin estas propiedades AGP falla en validateSigningRelease en vez
            // de emitir un APK sin firmar.
            signingProperty("RELEASE_STORE_FILE")?.let { storeFile = file(it) }
            signingProperty("RELEASE_STORE_PASSWORD")?.let { storePassword = it }
            signingProperty("RELEASE_KEY_ALIAS")?.let { keyAlias = it }
            signingProperty("RELEASE_KEY_PASSWORD")?.let { keyPassword = it }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.google.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
