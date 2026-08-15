# Bili2Media 空工程设计

## 目标

在 `Bili2Media` 工作区创建一个可由 Android Studio 打开、编译和启动的全新 Android 应用，为后续读取哔哩哔哩本地缓存并转换为 MP3/MP4 奠定基础。

本阶段只创建空工程和占位首页，不实现缓存扫描、存储权限申请、音视频解析或格式转换。

## 工程基线

直接复用相邻 `ComicLab` 工程的环境与组织方式：

- Kotlin + Android XML View
- 单 `app` 模块
- Gradle Kotlin DSL 与 Version Catalog
- Android Gradle Plugin 8.13.1
- Gradle Wrapper 8.13
- Kotlin 2.0.21
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 33`
- Java/Kotlin JVM 11
- AndroidX、AppCompat、Material3、ConstraintLayout
- 优先使用阿里云 Maven 镜像，同时保留 Google、Maven Central 和 Gradle Plugin Portal

## 应用标识

- 工程名：`Bili2Media`
- 应用名：`Bili2Media`
- namespace/applicationId：`com.example.bili2media`
- 启动 Activity：`com.example.bili2media.MainActivity`

## 初始界面

首页沿用 ComicLab 的灰白色 Material3 视觉基础：浅灰背景、白色圆角卡片、深灰主文字和克制的间距。页面只显示应用名称和“工程已就绪”占位信息，不提供尚未实现的按钮或虚假操作入口。

## 文件范围

创建标准 Gradle 工程文件、Gradle Wrapper、`app` 模块、Manifest、Kotlin 启动 Activity，以及首页布局、主题、颜色、字符串和基础启动图标资源。不会复制 ComicLab 的漫画业务代码、归档依赖、文件管理权限或应用数据。

## 验证标准

1. Gradle 工程可完成同步所需的配置解析。
2. `gradlew.bat assembleDebug` 退出码为 0。
3. 生成 `app-debug.apk`。
4. Manifest 中的 launcher Activity、包名和主题均指向 Bili2Media 自身资源。

若本机缺少 Android SDK、JDK 或依赖下载失败，将明确区分工程代码问题与环境问题，不把未执行的启动验证描述为已通过。
