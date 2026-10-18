plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.peicheng0413.classpass"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.peicheng0413.classpass"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    // 正式版簽章：金鑰與密碼放在 ~/.gradle/gradle.properties（CLASSPASS_*），不進 repo。
    // 沒設定時 release 不簽章，assembleRelease 產出的 APK 無法安裝。
    val storeFile = providers.gradleProperty("CLASSPASS_STORE_FILE").orNull
    signingConfigs {
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = providers.gradleProperty("CLASSPASS_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("CLASSPASS_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("CLASSPASS_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    val camerax = "1.6.2"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // 模型內建在 app 裡的版本（不是 play-services-mlkit-barcode-scanning），完全離線可用
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")
}
