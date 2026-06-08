import java.net.HttpURLConnection
import java.net.URI

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.greg7gkb.readout.wake"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    api(project(":core:common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)

    // OpenWakeWord runtime — Phase 4. Models live in src/main/assets/wake/
    // and are fetched at build time by the downloadOwwModels task below.
    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// --- OpenWakeWord model assets -----------------------------------------------
//
// OWW ships pre-trained .onnx files as GitHub release assets. We fetch them at
// build time rather than checking the ~3.7 MB of binaries into git, so a fresh
// clone + `./gradlew :app:assembleDevDebug` produces a runnable APK with no
// manual setup. The task is wired into `preBuild` so it runs before any task
// reads the assets directory.
//
// Gradle's up-to-date check (declared outputs) skips the task entirely when
// all files are present. The inner exists() check makes a partial state (one
// file deleted manually) re-fetch only the missing ones.
val owwModels = mapOf(
    "melspectrogram.onnx" to "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/melspectrogram.onnx",
    "embedding_model.onnx" to "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/embedding_model.onnx",
    "hey_jarvis_v0.1.onnx" to "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/hey_jarvis_v0.1.onnx",
)
val owwAssetsDir = layout.projectDirectory.dir("src/main/assets/wake")

val downloadOwwModels by tasks.registering {
    description = "Downloads OpenWakeWord ONNX models for the Phase 4 wake-word detector."
    group = "build setup"

    // Capture as locals so the doLast closure doesn't reference script-level
    // vals — Gradle's configuration cache can't serialize those references.
    val models = owwModels
    val assetsDir = owwAssetsDir.asFile

    inputs.property("modelUrls", models)
    models.keys.forEach { name -> outputs.file(File(assetsDir, name)) }

    doLast {
        assetsDir.mkdirs()
        models.forEach { (name, url) ->
            val dest = File(assetsDir, name)
            if (dest.exists() && dest.length() > 0) {
                logger.info("OWW model already present: $name (${dest.length()} bytes)")
                return@forEach
            }
            logger.lifecycle("Downloading OWW model: $name")
            logger.lifecycle("  from $url")
            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.inputStream.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            logger.lifecycle("  -> ${dest.length()} bytes")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadOwwModels)
}
