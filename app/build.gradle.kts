plugins {
    id("com.android.application")
}

val keystoreProperties = java.util.Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    } else {
        val env = System.getenv()
        if (env.containsKey("KS_STORE_PASSWORD")) {
            setProperty("storePassword", env["KS_STORE_PASSWORD"])
        }
        if (env.containsKey("KS_KEY_ALIAS")) {
            setProperty("keyAlias", env["KS_KEY_ALIAS"])
        }
        if (env.containsKey("KS_KEY_PASSWORD")) {
            setProperty("keyPassword", env["KS_KEY_PASSWORD"])
        }
    }
}

android {
    namespace = "io.github.nicoenhance"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nicoenhance"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.3"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/nicoenhance.jks")
            storePassword = keystoreProperties.getProperty("storePassword") ?: System.getenv("KS_STORE_PASSWORD") ?: ""
            keyAlias = keystoreProperties.getProperty("keyAlias") ?: System.getenv("KS_KEY_ALIAS") ?: ""
            keyPassword = keystoreProperties.getProperty("keyPassword") ?: System.getenv("KS_KEY_PASSWORD") ?: ""
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
