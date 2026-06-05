import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Read cloud-LLM API keys out of local.properties (gitignored). When unset
// the BuildConfig fields default to empty strings — fine for `dev`, surfaces
// as an HTTP 401 from the provider for the `cloud` flavor so the misconfig is
// obvious in logcat. Expected keys: `anthropic.api.key`, `gemini.api.key`.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun stringField(value: String): String = "\"${value.replace("\"", "\\\"")}\""

android {
    namespace = "com.greg7gkb.readout"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.greg7gkb.readout"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "ANTHROPIC_API_KEY",
            stringField(localProperties.getProperty("anthropic.api.key", "")),
        )
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            stringField(localProperties.getProperty("gemini.api.key", "")),
        )
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

    // Flavor dimension picks which LlmClient implementation gets wired in :app's
    // DI module. Each flavor will eventually contribute its own Hilt module under
    // src/<flavor>/kotlin/...; for now the flavors are declared empty.
    flavorDimensions += "llm"
    productFlavors {
        create("dev") {
            dimension = "llm"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        create("cloud") {
            dimension = "llm"
            applicationIdSuffix = ".cloud"
            versionNameSuffix = "-cloud"
        }
        create("onDevice") {
            dimension = "llm"
            applicationIdSuffix = ".ondevice"
            versionNameSuffix = "-ondevice"
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
        // Re-enabled per-module per the project default (off). Needed for the
        // cloud-LLM API-key fields above.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:audio"))
    implementation(project(":core:llm"))
    implementation(project(":core:screen"))
    implementation(project(":core:session"))
    implementation(project(":core:wake"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
