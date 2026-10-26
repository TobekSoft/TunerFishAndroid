// All behaviour that can be tested without a phone: Jellyfin client, filing rules, album sorting.
// Plain Kotlin/JVM. NO Android or Compose dependencies, ever.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.animalsniffer)
}

// Built with the installed JDK 21, emitting Java 17 bytecode like the app module.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Core runs on phones back to minSdk, whose Java library is older than the JDK this builds with:
// fail on any call Android 9 doesn't have (URLEncoder.encode(String, Charset) is API 33). Main code
// only; the tests run on the JDK. Runs before the tests and before every app build.
animalsniffer {
    sourceSets = listOf(project.sourceSets.main.get())
}
tasks.test { dependsOn(tasks.named("animalsnifferMain")) }

dependencies {
    signature(libs.android.api.signature) { artifact { type = "signature" } }
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
