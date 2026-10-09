plugins {
    id("com.android.application")
}

val ksStorePassword: String? = System.getenv("KS_STORE_PASSWORD")
val ksKeyAlias: String?     = System.getenv("KS_KEY_ALIAS")
val ksKeyPassword: String?  = System.getenv("KS_KEY_PASSWORD")

android {
    namespace = "io.github.nicoenhance"
    compileSdk = 36

    defaultConfig {
        // Must follow io.github.<github-username>.<app> for LSPosed repo ownership checks.
        applicationId = "io.github.kotobawakusei.nicoenhance"
        minSdk = 29
        targetSdk = 36
        // 可在 CI 中用 -PversionName=... -PversionCode=... 覆盖（tag 驱动发布）
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 6
        versionName = (project.findProperty("versionName") as String?) ?: "1.2.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/nicoenhance.jks")
            storePassword = ksStorePassword ?: ""
            keyAlias = ksKeyAlias ?: ""
            keyPassword = ksKeyPassword ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Always produce an installable APK. If the private release keystore is
            // unavailable in CI, use Android's debug key instead of publishing an
            // unsigned APK (unsigned APKs commonly fail with "package parse error").
            signingConfig = if (file("keystore/nicoenhance.jks").exists()
                && !ksStorePassword.isNullOrBlank()
                && !ksKeyAlias.isNullOrBlank()
                && !ksKeyPassword.isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
