plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.yang.jizhang"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yang.jizhang"
        minSdk = 26
        targetSdk = 35
        versionCode = 35
        versionName = "5.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // v3.9.0：用 debug keystore 签 release（与历史 debug 包同签名，可直接覆盖安装升级）。
            // 密钥文件不存在（换机构建）时跳过，产物为未签名 APK，自行签名即可。
            val debugKs = File(System.getProperty("user.home") ?: ".", ".android/debug.keystore")
            if (debugKs.exists()) {
                signingConfig = signingConfigs.create("releaseDebug") {
                    storeFile = debugKs
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 桌面小组件（Jetpack Glance）
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // 液态玻璃毛玻璃（iOS 27 Liquid Glass 风，v5.0.0）
    implementation("dev.chrisbanes.haze:haze:1.3.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

