import java.util.Properties

plugins {
    id("com.android.application")
    id("dev.flutter.flutter-gradle-plugin")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val gemmaModelUrl =
    providers.gradleProperty("GEMMA_MODEL_URL").orNull
        ?: localProperties.getProperty("GEMMA_MODEL_URL")
        ?: System.getenv("GEMMA_MODEL_URL")
        ?: ""

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.escape"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.example.escape"
        minSdk = 24
        targetSdk = flutter.targetSdkVersion

        versionCode = flutter.versionCode
        versionName = flutter.versionName

        // Read from android/local.properties or environment/Gradle property.
        // Never hard-code a Hugging Face token in source control.
        buildConfigField(
            "String",
            "GEMMA_MODEL_URL",
            gemmaModelUrl.asBuildConfigString()
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // Development/testing only. Replace before Play Store release.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
}
