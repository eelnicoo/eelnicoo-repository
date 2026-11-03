// Emulador de terminal de Termux (Apache 2.0, ver LICENSE). Copiado de
// github.com/termux/termux-app v0.118.3. Único cambio: el aviso de «proceso terminado»
// de TerminalSession está en español.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.emulator"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 24
        externalNativeBuild {
            ndkBuild {
                cFlags += listOf("-std=c11", "-Wall", "-Wextra", "-Os", "-fno-stack-protector")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
}
