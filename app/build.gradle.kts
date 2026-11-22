plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dsha.nonogram"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dsha.nonogram"
        minSdk = 23
        targetSdk = 34
        versionCode = 11
        versionName = "1.3.2"
    }

    // 固定签名（仓库内自带的测试用 keystore），保证每次 CI 构建的 APK 都能覆盖安装升级
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/nonogram-release.p12")
            storePassword = "nonogram123"
            keyAlias = "nonogram"
            keyPassword = "nonogram123"
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
