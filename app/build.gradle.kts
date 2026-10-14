import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// Server settings live in config.properties at the project root (edit it, then rebuild).
// Any value can also be overridden for one build with -Pfvst.<key>=..., e.g. -Pfvst.api.baseUrl=https://...
val appConfig = Properties().apply {
    val text = providers.fileContents(rootProject.layout.projectDirectory.file("config.properties")).asText.orNull
    if (text != null) load(StringReader(text))
}
fun appSetting(key: String, default: String): String =
    providers.gradleProperty("fvst.$key").orNull ?: appConfig.getProperty(key)?.trim() ?: default

val apiBaseUrl = appSetting("api.baseUrl", "http://10.0.2.2/driver-api/v1/").let { if (it.endsWith("/")) it else "$it/" }
val apiHostHeader = appSetting("api.hostHeader", "")
val useMockApi = appSetting("api.useMock", "false").toBoolean().toString()
val dispatchPhone = appSetting("dispatch.phone", "")

// Release signing key settings live in keystore.properties at the project root (not in version control).
// Without that file, release builds are left unsigned.
val signingProps = Properties().apply {
    val text = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
    if (text != null) load(StringReader(text))
}

android {
    namespace = "com.fvstexpress.driver"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.fvstexpress.driver"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "API_HOST_HEADER", "\"$apiHostHeader\"")
        buildConfigField("boolean", "USE_MOCK_API", useMockApi)
        buildConfigField("String", "DISPATCH_PHONE", "\"$dispatchPhone\"")
    }

    signingConfigs {
        if (signingProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.location)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
