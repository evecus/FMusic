plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "cn.toside.music.compose"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.toside.music.compose"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // CI 传参控制 ABI：
        //   -PonlyArm64  → 只出 arm64-v8a（release 默认）
        //   -PonlyArmV7a → 只出 armeabi-v7a
        // 本地构建不带参数，保留全部 ABI 以便模拟器调试
        when {
            project.hasProperty("onlyArm64") -> {
                ndk { abiFilters += "arm64-v8a" }
            }
            project.hasProperty("onlyArmV7a") -> {
                ndk { abiFilters += "armeabi-v7a" }
            }
        }
    }

    // CI 通过环境变量注入签名信息（见 .github/workflows/release.yml）
    // 本地没有这些变量时不配置签名，仍可正常 debug / 本地 assembleRelease（会用 debug 签名）
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("RELEASE_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 开启混淆 + 资源收缩：material-icons-extended 上千个未引用的
            // 图标类、Compose 未用组件等全部被 R8 剥离，体积大幅下降
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 只有在 CI 注入了 keystore 路径时才使用 release 签名
            if (!System.getenv("RELEASE_STORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("libs")
        }
    }
}

dependencies {
    // 本地库：quickjs wrapper 核心 jar + android aar（含四 ABI .so）
    implementation(files("libs/wrapper-java-2.4.0.jar"))
    implementation(files("libs/wrapper-android-2.4.0.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jaudiotagger)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
