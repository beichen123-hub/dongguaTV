# 冬瓜TV Android

基于冬瓜TV影视聚合后端的原生 Android 客户端，自部署自用。

## 技术栈

- Kotlin + ViewBinding + Material3
- Retrofit + OkHttp + Coroutines
- 媒体：Media3 (ExoPlayer) + DanmakuFlameMaster（阶段2接入）
- 图片：Glide
- 追剧提醒：WorkManager + 本地通知
- 深色主题，暖橙红强调色 (#D85A30)

## 配置

- 包名：`com.beichen.dongguatv`
- minSdk 24 (Android 7.0) / targetSdk 34
- 影视后端：`https://tv.bcpan.icu/`
- 弹幕：走后端代理（阶段0后端改造后接入）

## 编译

GitHub Actions 自动编译，push 到 main/master 即触发。
编译产物在 Actions → 对应 run → Artifacts 下载 `dongguatv-debug-apk`。

本地编译（需 JDK 17 + Android SDK）：
```bash
./gradlew assembleDebug
```

## 开发阶段

- [x] 阶段1：工程骨架 + GitHub Actions 编译链路
- [ ] 阶段0：后端改造（模块化 + SQLite + 用户系统 + 弹幕代理 + 收藏/历史/追剧）
- [ ] 阶段2：核心功能（Retrofit + 登录/首页/搜索/详情/播放器+弹幕）
- [ ] 阶段3：增值功能（收藏/历史/追剧 + WorkManager 通知）
- [ ] 阶段4：打包签名 + release APK 分发
