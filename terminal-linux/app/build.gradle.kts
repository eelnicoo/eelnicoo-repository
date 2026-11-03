import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma opcional con la clave de la app (firma/keystore.properties, fuera de git; en Actions
// sale de los secrets). Sin ella el APK de release sale sin firmar. Tiene que ser siempre la
// misma clave: si cambia, Android obliga a desinstalar y eso borraría el Ubuntu de la tablet.
val firma = Properties().apply {
    val f = rootProject.file("firma/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.nico.terminallinux"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.nico.terminallinux"
        minSdk = 24
        // 34 y no más: con 35 Android obliga al modo borde a borde y limita los servicios
        // en primer plano de tipo dataSync, y con 34 proot ya funciona desde jniLibs.
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        if (firma.getProperty("storeFile") != null) {
            create("nico") {
                storeFile = rootProject.file("firma/" + firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("nico")
        }
        debug {
            signingConfigs.findByName("nico")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // proot, su cargador y talloc van como «librerías» para que Android los deje
            // ejecutables en nativeLibraryDir (desde Android 10 no se puede ejecutar nada
            // de la carpeta de datos de la app).
            useLegacyPackaging = true
            keepDebugSymbols += listOf("**/libproot.so", "**/libproot-loader.so", "**/libproot-loader32.so", "**/libtalloc.so")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":terminal-view"))
}
