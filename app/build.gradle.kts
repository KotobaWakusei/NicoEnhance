plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.nicoenhance"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nicoenhance"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/nicoenhance.jks")
            storePassword = "nicoenhance"
            keyAlias = "nicoenhance"
            keyPassword = "nicoenhance"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")

    implementation("org.luckypray:dexkit:2.2.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.cardview:cardview:1.0.0")
}
