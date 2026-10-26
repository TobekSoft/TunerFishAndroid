// The Android UI and playback. Testable behaviour goes in :core, not here.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// No APK with a core call too new for minSdk (see core/build.gradle.kts).
tasks.named("preBuild") { dependsOn(":core:animalsnifferMain") }

android {
    namespace = "com.tobeksoft.tunerfish"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.tobeksoft.tunerfish"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 911 // major * 10000 + minor * 100 + patch, so it always goes up
        versionName = "0.9.11"
    }

    // The release key and its password live outside the repo, in ~/.gradle/gradle.properties
    // (TUNERFISH_KEYSTORE, TUNERFISH_KEYSTORE_PASSWORD, TUNERFISH_KEY_ALIAS). Without them a
    // release build comes out unsigned and won't install.
    val keystore = providers.gradleProperty("TUNERFISH_KEYSTORE").orNull
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.gradleProperty("TUNERFISH_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("TUNERFISH_KEY_ALIAS").get()
                keyPassword = storePassword // a PKCS12 keystore has one password for both
            }
        }
    }

    buildTypes {
        // Its own app id and name (src/debug/res), so a development build sits beside the beta.
        debug {
            applicationIdSuffix = ".debug"
        }
        // No R8 shrinking yet: it needs keep rules (kotlinx.serialization, Media3) that are better
        // tried outside a beta.
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.documentfile)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
}
