import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
    id("idea")  // Needed for download sources?
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "org.righteffort.ourgrocerylist"
    compileSdk = 36  // https://developer.android.com/tools/releases/platforms
    defaultConfig {
        applicationId = "org.righteffort.ourgrocerylist"
        minSdk = 31  // Android 12
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        // Only initialize the release config if the properties file is present
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Apply the release config if available; otherwise, use the debug key
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                throw org.gradle.api.GradleException(
                    "Missing keystore.properties for release signing"
                )
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    flavorDimensions += "environment"
    productFlavors {
        create("local") {
            dimension = "environment"
            isDefault = true
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "true")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "false")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            all { it.useJUnitPlatform() }
            isIncludeAndroidResources = true // Required for integration tests using Firebase emulators
	}
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.commons.csv)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.espresso.core)
    testImplementation(libs.bundles.integration.test)
}

// This hack couples unit vs. integration with junit5 vs. junit4, but it works.
// TODO: maybe someday separate build.gradle.kts files
tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        // If we pass the flag, run only JUnit 4 (Robolectric Integration)
        if (project.hasProperty("runIntegration")) {
            includeEngines("junit-vintage")
        }
        // Otherwise, default to running only JUnit 5 (Unit Tests)
        else {
            includeEngines("junit-jupiter")
        }
    }
}

// Needed for download sources?
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

