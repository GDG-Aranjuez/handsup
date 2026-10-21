import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Build-time configuration, all optional (defaults target the production Firebase project):
//   ./gradlew :androidApp:assembleDebug -Phandsup.useEmulator=true
//   handsup.firebase.apiKey / appId / projectId, handsup.defaultEvent, handsup.webBaseUrl, handsup.googleWebClientId
fun prop(name: String, default: String): String = (findProperty("handsup.$name") as String?) ?: default

val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "es.gdgaranjuez.handsup.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "es.gdgaranjuez.handsup"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("boolean", "USE_EMULATOR", prop("useEmulator", "false"))
        buildConfigField("String", "EMULATOR_HOST", "\"${prop("emulatorHost", "10.0.2.2")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${prop("firebase.apiKey", "AIzaSyDPkM3wx1CHnHL6xCQcELLoJoB8YawiXmA")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${prop("firebase.appId", "1:55603056127:android:57079ae1a2e18dd991ebca")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${prop("firebase.projectId", "handsup-5f58e")}\"")
        buildConfigField("String", "DEFAULT_EVENT", "\"${prop("defaultEvent", "fresonfest-2026")}\"")
        buildConfigField("String", "WEB_BASE_URL", "\"${prop("webBaseUrl", "https://gdg-aranjuez.github.io/handsup")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${prop("googleWebClientId", "")}\"")
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without keystore.properties the release APK is signed with the debug key so it can still be installed.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.gitlive.app)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.runtime)
}
