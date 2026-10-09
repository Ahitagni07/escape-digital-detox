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

val keystoreProperties = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = rootProject.file("key.properties").exists()

android {
    namespace = "com.example.escape"
    compileSdk = maxOf(36, flutter.compileSdkVersion)
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.example.escape"
        minSdk = 24
        targetSdk = 36

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

    signingConfigs {
        create("playUpload") {
            if (hasReleaseKey) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = keystoreProperties.getProperty("storeFile")?.let { file(it) }
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Debug signing only for local experiments. For Play publishing,
            // add android/key.properties and use the playUpload key.
            signingConfig = if (hasReleaseKey)
                signingConfigs.getByName("playUpload")
            else signingConfigs.getByName("debug")
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
    implementation("androidx.core:core:1.15.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
    // Small bundled offline classifier: no network on first photo.
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
