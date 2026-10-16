import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
}
val localSettings = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val googleWebClientId = providers.gradleProperty("google.webClientId").orNull
    ?: localSettings.getProperty("google.webClientId", "")
require(googleWebClientId.isEmpty() || googleWebClientId.matches(Regex("[A-Za-z0-9.-]+\\.apps\\.googleusercontent\\.com"))) { "google.webClientId 格式錯誤" }
android {
    namespace = "com.nqutaoitfteikc.notehub"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "com.nqutaoitfteikc.notehub"
        minSdk = 28
        targetSdk = 36
        versionCode = 9
        versionName = "0.9.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    buildFeatures { compose = true; buildConfig = true }
    // Keep Traditional Chinese resources available when a system picker overrides locale.
    bundle { language { enableSplit = false } }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(libs.core)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.work)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.credentials)
    implementation(libs.credentials.google)
    implementation(libs.googleid)
    implementation(libs.googleauth)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.android.junit)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.testManifest)
}




