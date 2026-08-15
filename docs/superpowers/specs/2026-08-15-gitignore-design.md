# Bili2Media .gitignore 设计

## 目标

将当前零散的 Android 忽略规则替换为结构清晰、覆盖完整且不误伤工程源文件的规则。

## 忽略范围

- Android Studio 与编辑器本机状态：`.idea/`、`*.iml`、`.vscode/`、`.fleet/`
- Gradle 与 Kotlin 本地缓存：`.gradle/`、`.kotlin/`
- 根工程及所有模块的构建输出：`build/`、`**/build/`
- 本机 Android SDK 配置：`local.properties`
- NDK/CMake 临时目录：`.externalNativeBuild/`、`.cxx/`
- 安装包及发布产物：`*.apk`、`*.aab`、`*.ap_`
- 签名材料与本机签名配置：`*.jks`、`*.keystore`、`keystore.properties`
- 调试、崩溃和性能转储：`*.log`、`*.hprof`、`hs_err_pid*`
- 常见操作系统生成文件：`.DS_Store`、`Thumbs.db`、`Desktop.ini`

## 必须保留

- `gradlew`、`gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar` 和 `gradle-wrapper.properties`
- Kotlin/Java 源码、Manifest、XML 和其他资源
- `gradle.properties`、版本目录和构建脚本
- 文档、测试、Room schema、Baseline Profile 等应纳入版本管理的工程资产

## 验证

通过规则级检查确认关键缓存和产物均能匹配，同时确认 Gradle Wrapper、源码、资源与文档不被任何宽泛规则覆盖。本任务只修改 `.gitignore` 和本设计说明，不执行 Git 命令。
