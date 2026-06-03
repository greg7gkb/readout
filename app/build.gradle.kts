plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.greg7gkb.readout"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.greg7gkb.readout"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
