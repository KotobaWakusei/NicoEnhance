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
        applicationId = "io.github.nicoenhance"
        minSdk = 29
        targetSdk = 36
        // 可在 CI 中用 -PversionName=... -PversionCode=... 覆盖（tag 驱动发布）
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 5
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.3"
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
            // 本地/无 keystore 环境（如 fork PR）不强制签名，产出 unsigned APK
            if (file("keystore/nicoenhance.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
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
