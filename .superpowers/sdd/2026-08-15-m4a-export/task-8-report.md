# Task 8 报告：独立 M4A 每行状态与操作 UI

## 状态

Task 8 已完成。M4A 使用独立的 UI 状态、状态映射器、ViewModel、列表回调和 Work 标签；MP4 原有操作行及其状态绑定行为保持不变。

## TDD：RED / GREEN

### RED

先创建 `M4aExportStateMapperTest`，随后运行：

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat testDebugUnitTest --tests "*M4aExportStateMapperTest" --console=plain
```

结果：预期失败，`compileDebugUnitTestKotlin` 报 `M4aExportStateMapper` 和 `M4aExportUiState` 未解析；失败原因仅为待实现类型缺失。

### GREEN

最小实现 `M4aExportUiState` 与 `M4aExportStateMapper` 后重新运行同一命令。

结果：`BUILD SUCCESSFUL`。最终测试结果文件确认 M4A mapper 共 10 个测试，0 failure、0 error、0 skipped。

覆盖内容：

- ENQUEUED、BLOCKED；
- analyzing phase；
- exporting progress 及 0..100 截断；
- succeeded URI、failed error code、cancelled；
- 缺失或空白终态数据的稳定 fallback；
- request order 优先于 generation；
- 相同 order 使用 generation；
- legacy 记录使用 generation；
- 任意合法 ordered retry（包括 `Long.MIN_VALUE`）优先于 legacy；
- MP4-only 与 malformed entry tags 被忽略。

## 回归与构建

集成后运行：

```powershell
.\gradlew.bat testDebugUnitTest --tests "*M4aExportStateMapperTest" --tests "*Mp4ExportStateMapperTest" --console=plain
```

结果：`BUILD SUCCESSFUL`，M4A 与 MP4 mapper 回归通过。

最终按 brief 运行：

```powershell
.\gradlew.bat testDebugUnitTest --tests "*M4aExportStateMapperTest" assembleDebug --console=plain
```

结果：`BUILD SUCCESSFUL`，`testDebugUnitTest` 与 `assembleDebug` 均完成。

## 修改文件

新增：

- `app/src/main/java/com/example/bili2media/ui/export/m4a/M4aExportUiState.kt`
- `app/src/main/java/com/example/bili2media/ui/export/m4a/M4aExportStateMapper.kt`
- `app/src/main/java/com/example/bili2media/ui/export/m4a/M4aExportViewModel.kt`
- `app/src/test/java/com/example/bili2media/ui/export/m4a/M4aExportStateMapperTest.kt`

修改：

- `app/src/main/java/com/example/bili2media/ui/export/BiliCacheListItem.kt`
- `app/src/main/java/com/example/bili2media/ui/BiliCacheAdapter.kt`
- `app/src/main/java/com/example/bili2media/MainActivity.kt`
- `app/src/main/res/layout/item_bili_cache.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/dimens.xml`

## 模块边界自审

- `M4aExportUiState` 不继承、不包装 MP4 状态。
- M4A mapper 只通过 `M4aExportWorkContract` 解析 M4A entry/order tags。
- M4A ViewModel 只观察 `M4aExportWorkContract.TAG_ALL_EXPORTS`，enqueue/cancel 只操作 M4A unique work。
- `BiliCacheAdapter` 与 `BiliCacheListItem` 无 WorkManager import；它们只接收状态和业务回调。
- `MainActivity` 只组合扫描条目与两份独立状态 map，并负责权限请求和系统 `ACTION_VIEW`。
- MP4 与 M4A 的 ViewModel、状态 map、回调和打开逻辑互不串用。
- M4A 打开 MIME 为精确的 `audio/mp4`，并添加 `FLAG_GRANT_READ_URI_PERMISSION`。
- 未引入 Task 9 范围的框架、FFmpeg、NDK、LAME、解码或重编码改动。

## UI 自审

- 第二操作行位于 MP4 行下方，使用精确 ID：`progressM4aExport`、`txtM4aExportStatus`、`btnM4aExportAction`。
- 行内 ProgressBar、状态文本、按钮尺寸/颜色/间距结构与 MP4 行一致。
- 两行间距使用命名 dimen `export_row_spacing`，无新增硬编码间距。
- M4A Idle/Failed/Cancelled 仅在缓存 `AVAILABLE` 时允许导出/重试。
- Queued/Analyzing/Exporting 提供独立取消；Succeeded 提供独立“打开 M4A”。
- 通用状态文案复用既有字符串，仅新增四个所需 M4A UI 字符串。
- 本次完成静态布局与资源编译验证；未进行真机/模拟器视觉和系统应用选择器手工验收。
