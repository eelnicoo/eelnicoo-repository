plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.eelnicoo.pocketcode"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.eelnicoo.pocketcode"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Debug keystore generated in CI (or the default ~/.android/debug.keystore).
        // Release uses the same key so CI can produce an installable artifact without secrets.
        create("release") {
            val ks = System.getenv("POCKETCODE_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("POCKETCODE_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("POCKETCODE_KEY_ALIAS") ?: "pocketcode"
                keyPassword = System.getenv("POCKETCODE_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val ks = System.getenv("POCKETCODE_KEYSTORE")
            if (ks != null) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.version",
                "META-INF/MANIFEST.MF",
                "META-INF/versions/**",
                "META-INF/services/**",
                "META-INF/maven/**",
                "module-info.class",
                "**/module-info.class",
                "about.html",
                "**/*.proto",
            )
            // r8/ecj ship overlapping resource files; keep the first.
            pickFirsts += setOf(
                "**/*.properties",
            )
        }
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Shizuku: elevated shell / system access on non-rooted devices (incl. GrapheneOS).
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // On-device APK build toolchain (pure-JVM, runs on ART).
    implementation("com.android.tools.build:apksig:8.7.3")
    // Bundled compiler (ECJ) + dexer (D8/R8) jars live in app/libs and run on-device.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    testImplementation("junit:junit:4.13.2")
}
