# Bili2Media MP4 导出设计

## 目标

为已扫描的 Bilibili 缓存增加 MP4 导出能力。首版只支持：

- 将同一缓存媒体分组中的一个 M4S 视频轨和一个 M4S 音频轨无损封装为 MP4；
- 将已经包含有效音视频轨的 MP4 直接复制到输出目录；
- 输出到公共目录 `Download/Bili2Media/Output`；
- 显示分析、排队、导出进度、成功、失败和取消状态。

首版不支持 MP3、FLV、BLV、多段拼接、转码和 FFmpeg。缓存源文件始终只读。

## 技术方案

首版使用 Android 原生 `MediaExtractor` 和 `MediaMuxer`。M4S 导出只复制编码后的音视频 sample，不解码、不重新编码，因此速度快且不降低画质。已有 MP4 使用流复制。

转换能力必须隐藏在 `Mp4ExportEngine` 接口后。未来增加 FFmpeg 或 FLV/BLV 支持时，可以增加新的引擎实现，不修改扫描、规划和 UI 契约。

## 模块边界

当前继续保持单一 `app` Gradle 模块，使用可独立拆分的包边界：

```text
cache/model <- cache/scanner
cache/model <- cache/media
media/model <- media/probe
cache/media + media/model <- export/planner
export/planner -> export/engine
export/engine -> export/output
export/work -> planner + engine + output
ui/export -> WorkManager
```

### `cache/model`

新增纯 Kotlin `CacheEntryLocation`：

- `FileDirectory(path: String)`
- `DocumentDirectory(uri: String)`

`BiliCacheEntry` 保存其缓存目录位置。位置只描述来源，不提供文件读取、媒体分析或转换方法。

### `cache/media`

`CacheMediaLocator` 根据 `CacheEntryLocation` 发现当前条目直接拥有的媒体文件，输出纯数据 `CacheMediaFile`：文件名、相对路径、大小和 `MediaInputRef`。

- File 适配器输出绝对文件路径；
- SAF 适配器输出 `content:` URI；
- 遇到包含另一个 `entry.json` 的子目录时停止下探，防止父子缓存串数据；
- 只发现文件，不打开、探测、合并或写入文件。

### `media/model`

定义纯数据模型：

- `MediaInputRef.FilePath`
- `MediaInputRef.ContentUri`
- `MediaTrackInfo`：类型、MIME、时长、分辨率、码率；
- `ProbedMediaFile`：媒体文件及其轨道信息。

### `media/probe`

`MediaProbe` 是探测接口。`AndroidMediaExtractorProbe` 是唯一依赖 `MediaExtractor` 的实现，负责从 File 或 SAF 输入中读取轨道元数据并释放所有文件描述符和 Extractor。

探测失败只返回结构化错误，不修改源文件，也不通过扩展名猜测轨道类型。

### `export/planner`

纯 Kotlin `Mp4ExportPlanner` 接收探测结果并生成以下计划之一：

- `CopyExistingMp4(input)`
- `MuxM4s(videoInput, audioInput)`
- `Unsupported(reason)`

选择规则：

1. 已有 MP4 必须同时包含可用于 MP4 的视频轨和音频轨；
2. M4S 只允许同一相对父目录中的一个视频文件和一个音频文件配对；
3. 包含多个视频或多个音频、缺少轨道、多段文件或不受 MediaMuxer 支持的编码时，不进行猜测；
4. 多个有效候选按分辨率像素数、视频码率排序，选择质量最高者；质量相同时优先直接复制 MP4，再按路径稳定排序。

### `export/engine`

`Mp4ExportEngine` 接收已经确定的计划、输出文件描述符、进度回调和取消检查。

`AndroidMp4ExportEngine`：

- 复制计划按字节流复制并报告进度；
- M4S 计划使用两个 `MediaExtractor` 和一个 `MediaMuxer`；
- 时间戳以各轨道第一帧为基准归零，保持 sample 原始顺序和标志；
- 不执行解码、编码或源文件修复；
- 所有 Stream、ParcelFileDescriptor、Extractor 和 Muxer 都在成功、失败和取消路径释放。

### `export/output`

`Mp4OutputStore` 接口负责创建、提交和放弃输出。

`MediaStoreMp4OutputStore` 在 `MediaStore.Downloads` 中创建 `video/mp4`，相对路径固定为 `Download/Bili2Media/Output`。写入期间使用 pending 状态；成功后发布，失败或取消时删除未完成条目。

文件名由标题清理得到。空标题使用 `Bili2Media-<时间>`；重名依次生成 `标题 (1).mp4`、`标题 (2).mp4`，不覆盖已有文件。

### `export/work`

每次导出使用一个唯一 WorkManager 任务。Worker 输入只包含缓存目录位置、标题和条目 ID，不传递大文件列表。Worker 运行时重新发现当前条目的媒体文件并生成计划，因此进程重启后仍可恢复执行。

长任务切换为前台 Worker，并显示导出通知。Android 13 以上首次导出时请求通知权限；用户拒绝通知权限不改变导出逻辑。取消任务后引擎停止并清理 pending 输出。

同一条目同一时间只允许一个导出任务，防止重复写入。

### `ui/export`

列表卡片增加“导出 MP4”操作。Adapter 只负责渲染状态和发送点击/取消事件，不引用 MediaExtractor、MediaMuxer、MediaStore 或 WorkManager。

`Mp4ExportViewModel` 负责：

- 提交 WorkManager 请求；
- 观察任务状态并映射为 `Idle`、`Analyzing`、`Queued`、`Exporting(progress)`、`Succeeded`、`Failed`、`Cancelled`；
- 向 Adapter 提供以条目 ID 为键的状态；
- 在成功时提供输出 URI，用于打开生成的 MP4。

## 数据流

1. 用户点击缓存卡片的“导出 MP4”。
2. ViewModel 以条目 ID 创建唯一 WorkManager 任务。
3. Worker 根据 `CacheEntryLocation` 发现媒体文件。
4. `MediaProbe` 探测实际轨道信息。
5. `Mp4ExportPlanner` 选择直接复制、M4S 合并或返回不支持原因。
6. OutputStore 创建 pending MP4。
7. ExportEngine 执行计划并更新进度。
8. 成功后发布 MediaStore 条目；失败或取消时删除未完成输出。
9. UI 显示最终状态，并允许打开成功导出的文件。

## 错误处理

错误使用稳定错误码传递，UI 负责本地化文案：

- 缓存目录或媒体文件已被移除；
- SAF 授权已失效；
- 缺少视频轨或音频轨；
- 存在多个无法确定的轨道或多段媒体；
- 编码或容器不受原生引擎支持；
- MediaStore 输出创建失败；
- 空间不足、读取失败或封装失败；
- 用户取消。

任何错误都不得留下可见的半成品 MP4，也不得修改缓存源文件。

## 测试与验证

### JVM 单元测试

- 媒体候选分组与父子缓存所有权；
- MP4 直接复制计划；
- M4S 音视频配对计划；
- 最高质量选择及稳定排序；
- 缺轨、多轨、多段和不支持编码；
- 文件名清理和重名递增；
- Worker 输入序列化与导出状态映射。

### Android 仪器测试

- File 和 `content:` URI 的 MediaExtractor 探测；
- 小型测试素材的 M4S 音视频封装；
- 已有 MP4 的直接复制；
- MediaStore pending、提交和取消清理；
- SAF 授权与资源关闭路径。

### Android 16 真机验证

- 一个真实 Bilibili M4S 音视频缓存成功导出并可播放；
- 一个已有 MP4 成功复制；
- 重名文件自动递增；
- 导出进度、取消和失败提示；
- 切换后台、旋转屏幕和进程重建后任务状态可恢复；
- 输入源保持未修改。

## 非目标

本阶段不实现：

- MP3；
- FLV、BLV；
- FFmpeg；
- 转码、改变分辨率或码率；
- 字幕、弹幕或封面写入 MP4；
- 批量导出；
- 多段视频拼接。
