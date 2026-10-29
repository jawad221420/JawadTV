plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jawad.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jawad.tvlauncher"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "5.0"
    }

    signingConfigs {
        create("shared") {
            val ks = rootProject.file("app/jawadtv.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = "jawadtv"
                keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            if (rootProject.file("app/jawadtv.keystore").exists()) {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
        release {
            isMinifyEnabled = false
            if (rootProject.file("app/jawadtv.keystore").exists()) {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.tvprovider:tvprovider:1.0.0")
}
