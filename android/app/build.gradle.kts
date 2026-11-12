plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------------------
// release 签名：仅当 CI 通过 GitHub Secrets 注入了完整签名信息时启用。
// 私钥绝不入库；未配置时 assembleRelease 产出未签名包，workflow 会自动跳过该任务。
// 本地无签名配置时不影响 debug 构建，保证「没配 Secrets = 维持原有行为」。
// ---------------------------------------------------------------------------
val releaseKeystoreFile = System.getenv("RELEASE_KEYSTORE_FILE")
val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
// 在 Project 作用域解析成 File，避免 android{} 内依赖隐式 receiver
val releaseKeystore: java.io.File? = releaseKeystoreFile
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
val releaseSigningReady = releaseKeystore != null && releaseKeystore.exists() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.beichen.dongguatv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.beichen.dongguatv"
        minSdk = 24
        targetSdk = 34
        // CI 可注入 VERSION_CODE（用 github.run_number 自增），实现后续版本覆盖安装；
        // 本地构建默认 1，不影响开发。
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseKeystore!!
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 关闭 R8 混淆 / 资源压缩：工程用了 Gson 反射、Room、Retrofit、Glide、ViewBinding，
            // 本机无真机验证条件，开混淆极易导致运行时崩溃。自用分发优先保证与 debug 行为一致，
            // 代价只是 APK 大约大几百 KB。
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // AndroidX 基础
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    // 首页 Hero 轮播（Emby 风格大图 Banner）
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // 网络：Retrofit + OkHttp + Gson
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room 本地数据库（收藏/历史/追剧）
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Media3 ExoPlayer 播放器
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    // 采集站播放地址 99% 是 m3u8（HLS），必须单独引入 HLS 模块，否则 UnrecognizedInputFormat
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.2.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")

    // 弹幕：DanmakuFlameMaster jitpack 无 0.9.25 可用版本，0.3.5 与 AndroidX 冲突。
    // 改用自绘方案（自定义 View + B站XML解析），不依赖第三方库，后续在 player 模块实现。

    // 图片加载
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // WorkManager（追剧更新轮询通知）
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // 测试
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
