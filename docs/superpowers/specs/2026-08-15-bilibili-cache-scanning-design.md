# Bili2Media 本地缓存扫描设计

## 目标与边界

首版扫描用户手动复制到公共存储目录的 Bilibili 缓存，解析基础元数据和媒体片段并在列表中展示。目标设备为 Android 16、非 Root。

本阶段不尝试读取 Bilibili 自身的 `/Android/data/tv.danmaku.bili/`，不使用 Root 或 Shizuku，也不实现 MP3/MP4 合并与转换。

## 存储入口

应用提供两种公共目录入口：

1. 默认目录：`Download/Bili2Media/Input`。获得“所有文件访问权限”后，应用创建并直接扫描该目录。
2. 用户选择目录：使用系统 `ACTION_OPEN_DOCUMENT_TREE` 选择任意可访问的公共目录，并持久化 URI 授权。该入口可在未授予“所有文件访问权限”时使用。

若保存的 URI 授权失效，应用停止读取该目录、清除无效选择并提示重新选择，不回退到未经授权的路径。

## 代码边界

- `BiliCacheEntry`：扫描结果模型，保存稳定标识、标题、分集名称、相对路径、媒体片段数量、总大小和状态。
- `BiliCacheMetadataParser`：只负责解析 `entry.json`，不访问 Android UI。
- `FileBiliCacheScanner`：扫描默认 `File` 目录。
- `DocumentTreeBiliCacheScanner`：扫描 SAF `DocumentFile` 目录。
- `CacheRootStore`：保存当前使用默认目录还是持久化 URI。
- `MainActivity`：负责权限、目录选择、刷新和渲染，不包含缓存格式解析逻辑。
- `BiliCacheAdapter`：使用 RecyclerView 展示扫描结果。

扫描在单线程后台执行器中完成，通过扫描代次丢弃过期结果；Activity 销毁时终止执行器，避免旧扫描覆盖新目录结果。

## 缓存识别

递归查找名为 `entry.json` 的文件，并将其父目录视为缓存候选目录。候选目录下继续递归收集以下扩展名的媒体文件：

- `.m4s`
- `.blv`
- `.flv`
- `.mp4`
- `.m4a`
- `.aac`

解析器使用 Android 自带 `org.json`，兼容读取顶层标题以及常见的 `page_data`、`ep`、`avid`、`cid` 字段。缺失字段使用目录名作为回退显示名称。

候选目录存在媒体片段时状态为“可用”；没有媒体片段时为“未发现媒体”；`entry.json` 无法解析时仍保留条目，状态为“元数据异常”。同一元数据目录只产生一个结果，结果按标题和路径稳定排序。

## 界面

首页继续使用 ComicLab 风格：浅灰背景、白色圆角卡片、深灰文字。

- 顶部卡片显示应用名、当前目录和访问状态。
- 操作区提供“授权访问”“选择目录”“刷新”三个按钮，并只在相关状态下启用。
- 内容区使用 RecyclerView 显示缓存卡片。
- 每项显示标题、分集或目录名、总大小、媒体片段数、相对路径和状态。
- 空目录显示明确空状态；扫描中显示进度指示；失败显示错误说明并保留重试入口。

## 权限与错误处理

- Manifest 声明 `MANAGE_EXTERNAL_STORAGE`，仅用于本地工具访问公共默认目录。
- 授权入口使用 `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`。
- 未授权时不读取默认绝对路径，但系统目录选择功能保持可用。
- 目录不存在时仅在已获得所有文件访问权限后创建。
- 单个目录、JSON 或文件读取失败不会终止整个扫描；结果中记录异常或跳过不可访问分支。
- 扫描结果不修改、不移动、不删除任何缓存文件。

## 验证

单元测试使用临时目录覆盖：合法 `entry.json` 与媒体片段、嵌套目录、损坏 JSON、缺失媒体、无关目录、稳定排序和大小汇总。

实施完成后执行：

- `testDebugUnitTest`
- `assembleDebug`
- 静态检查 Manifest 权限、启动 Activity 和默认路径常量

真机验收需要在 Android 16 设备上分别验证默认目录授权扫描、SAF 目录扫描、拒绝权限和空目录状态。未执行的真机步骤必须明确标记为未验证。
