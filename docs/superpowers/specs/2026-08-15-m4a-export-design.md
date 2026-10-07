# Bili2Media 无损 M4A 导出设计

## 目标

在现有 MP4 导出能力之外，为已扫描的 Bilibili 缓存增加独立的无损 M4A 导出能力。这里的“无损”指不对缓存中的 AAC 音频重新编码，只把原有 AAC sample 复制到 M4A 容器，因此不会产生额外音质损失。

首版支持：

- 将仅含 AAC 音轨的 M4S 无损封装为 M4A；
- 从含 AAC 音轨的 MP4 中提取并无损封装为 M4A；
- 将已经有效的 M4A 直接复制到输出目录；
- 在存在多个有效音频候选时，按音频码率、采样率、声道数和稳定路径选择质量最高者；
- 显示独立于 MP4 的分析、排队、导出进度、成功、失败和取消状态。

缓存源文件始终只读。输出继续放在公共目录 `Download/Bili2Media/Output`。

## 技术方案选择

采用独立 M4A 导出管线，不把音频逻辑塞入现有 `Mp4ExportEngine`，也不在本阶段把所有导出能力重构为一个大型通用框架。

原因：

- MP4 导出处理音视频配对，M4A 导出只处理单条音轨，两者规划规则不同；
- 独立引擎便于分别测试、替换和演进；
- 扫描、媒体探测等稳定基础设施仍可复用；
- 避免为了两个格式提前设计复杂的通用抽象。

M4A 封装使用 Android 原生 `MediaExtractor` 和 `MediaMuxer`。不引入 FFmpeg、NDK、LAME 或新的转码依赖。

## 模块边界

继续保持单一 `app` Gradle 模块，通过包边界维持低耦合：

```text
cache/media + media/model <- export/m4a/planner
export/m4a/planner <- export/m4a/usecase
export/m4a/engine <- export/m4a/usecase
export/m4a/output <- export/m4a/usecase
export/m4a/work -> usecase + Android adapters
ui/export/m4a -> WorkManager
```

现有 MP4 包保持原有职责。M4A 代码放在独立的 `export/m4a` 和 `ui/export/m4a` 包中。

### 复用部分

直接复用：

- `CacheEntryLocation`、`CacheMediaFile` 和 `MediaInputRef`；
- `CacheMediaLocator` 的 File/SAF 文件发现能力；
- `MediaProbe` 和 `AndroidMediaExtractorProbe`；
- WorkManager 前台任务模式、请求顺序标签和取消机制；
- MediaStore pending、成功提交和失败清理的所有权模式。

只把真正与格式无关的文件名清理逻辑提取为共享纯 Kotlin 组件。MP4 和 M4A 的输出仓库、规划器、引擎和 Worker 不互相依赖。

现有 `CacheMediaLocator` 的扩展名过滤将从 MP4 专用判断调整为通用媒体候选判断，识别 `.mp4`、`.m4s` 和 `.m4a`。Locator 仍然只负责发现文件，不打开或判断轨道类型；MP4 用例继续只探测 MP4/M4S，M4A 用例只探测 MP4/M4S/M4A。

## 媒体探测扩展

扩展纯数据 `MediaTrackInfo`，增加可空字段：

- `sampleRate: Int?`
- `channelCount: Int?`

`AndroidMediaExtractorProbe` 在 `MediaFormat` 包含对应键时读取：

- `MediaFormat.KEY_SAMPLE_RATE`
- `MediaFormat.KEY_CHANNEL_COUNT`

现有 MP4 规划逻辑无需依赖这些新字段，保持兼容。

## M4A 导出规划

新增纯 Kotlin `M4aExportPlanner`，输出以下计划之一：

- `CopyExistingM4a(input, sourceBytes, durationUs)`
- `RemuxAacTrack(input, trackIndex, durationUs)`
- `Unsupported(reason)`

支持的音频 MIME 首版仅为 `audio/mp4a-latm`。

### 候选规则

1. `.m4a` 只有在恰好包含一条受支持 AAC 音轨且不包含视频轨时才能直接复制。
2. `.m4s` 和 `.mp4` 只有在文件中恰好存在一条音频轨，且该轨为受支持 AAC 时才能成为提取候选。
3. 同一文件含多条音频轨时返回歧义错误，不猜测语言、声道或轨道用途。
4. 缺少 AAC、探测失败或存在不支持的音频编码时返回结构化错误。
5. 多个有效文件候选按以下顺序排序：
   - 音频码率降序；
   - 采样率降序；
   - 声道数降序；
   - 完整 M4A 直接复制优先；
   - 相对路径稳定排序。
6. 单个音频文件内部不支持多段拼接，也不跨缓存条目组合音频。

## M4A 导出引擎

新增 `M4aExportEngine` 接口和 Android 实现。

### 直接复制

`CopyExistingM4a` 使用输入流和 MediaStore 输出流按块复制：

- 每个数据块检查取消状态；
- 按源文件字节数报告进度；
- 所有流在成功、失败和取消路径关闭；
- 不修改源 M4A。

### AAC 无损封装

`RemuxAacTrack` 使用一个 `MediaExtractor` 和一个 `MediaMuxer`：

- 只选择计划指定的 AAC 音轨；
- 输出格式使用 `MUXER_OUTPUT_MPEG_4`；
- 将第一帧时间戳归零，后续时间戳保持相对间隔；
- 保留可安全映射的 sample 标志；
- 明确拒绝加密 sample；
- sample 超过缓冲区时动态扩容；
- 每帧检查取消状态并报告基于时长的进度；
- 只在 Muxer 成功启动后执行 `stop`，始终执行资源释放。

整个过程不解码 AAC，也不重新编码。

## 输出管理

新增独立 `M4aOutputStore` 和 `MediaStoreM4aOutputStore`：

- MediaStore 集合使用 Downloads；
- MIME 为 `audio/mp4`；
- 相对路径为 `Download/Bili2Media/Output`；
- 文件扩展名固定为 `.m4a`；
- 写入期间设置 `IS_PENDING=1`；
- 成功后发布，失败或取消时删除 pending 条目；
- 不覆盖已有文件，使用 `标题.m4a`、`标题 (1).m4a`、`标题 (2).m4a`。

文件名非法字符、空标题、Unicode 长度和重名规则与 MP4 保持一致，由共享的纯 Kotlin 文件名清理组件提供基础能力。

## 用例与任务

新增同步 `M4aExportUseCase`，只负责以下编排：

1. 根据 `CacheEntryLocation` 发现媒体文件；
2. 探测 M4S、MP4 和 M4A 候选；
3. 调用纯 Kotlin 规划器选择来源；
4. 创建 pending M4A；
5. 调用 M4A 引擎；
6. 成功时提交，失败或取消时清理。

新增独立 `M4aExportWorker`：

- 使用 `bili2media:m4a-export` 独立标签；
- 唯一任务名包含格式和条目 ID，不与 MP4 任务冲突；
- 同一条目只允许一个 M4A 导出任务，但 MP4 与 M4A 可以分别排队；
- 使用前台 `dataSync` 通知；
- 支持进度、取消、进程重建和稳定错误码；
- 使用请求顺序标签避免旧 WorkInfo 覆盖新任务状态。

## UI 设计

每个缓存卡片保留现有 MP4 操作行，并在其下增加独立的 M4A 操作行：

- 状态文字；
- 分析或导出进度；
- `导出 M4A`、`取消`、`打开 M4A` 或 `重试`按钮。

MP4 与 M4A 状态分别观察和渲染。Adapter 只接收组合后的 UI 状态与点击回调，不引用 MediaExtractor、MediaMuxer、MediaStore 或 WorkManager。

成功后使用 `ACTION_VIEW`、`audio/mp4` 和临时读取授权打开输出文件。

## 错误处理

首版需要稳定区分：

- 没有可用媒体；
- 文件或 SAF 授权失效；
- 媒体探测失败；
- 缺少 AAC 音轨；
- 同一文件存在多条音频轨；
- 音频编码不是 AAC；
- 轨道索引失效；
- MediaStore 创建或提交失败；
- 输入读取、输出写入或 Muxer 失败；
- 用户取消。

任何失败都不得留下可见的半成品 M4A，也不得修改或删除缓存源文件。

## 测试与验证

### JVM 单元测试

- AAC MIME 和候选识别；
- M4A 直接复制计划；
- M4S、MP4 的 AAC 提取计划；
- 码率、采样率、声道数和路径排序；
- 多音轨、缺轨、探测失败和不支持编码；
- `.m4a` 文件名清理及重名递增；
- UseCase 的提交、取消和异常清理；
- WorkManager 输入序列化、唯一名称和状态映射。

### Android 仪器测试

- File 与 `content:` URI 的 AAC 探测；
- 现有 M4A 字节复制；
- 从音频 M4S 无损封装 M4A；
- 从含音视频的 MP4 中只提取 AAC 音轨；
- 输出只有一条 AAC 音轨且时间戳非负；
- MediaStore pending、发布、重名和取消清理。

### Android 16 真机验收

- 真实 Bilibili `audio.m4s` 成功导出并可播放；
- 输出时长与源 AAC 音轨一致；
- 码率、采样率和声道数保持不变；
- MP4 音频提取与已有 M4A 复制成功；
- 重名递增、进度、取消、后台和旋转状态正确；
- 导出前后源缓存哈希不变。

## 非目标

本阶段不实现：

- MP3、WAV、FLAC 或其他音频格式；
- AAC 解码或重新编码；
- 音量标准化、降噪、裁剪或变速；
- 多段音频拼接；
- ID3、歌词、封面或其他元数据写入；
- 批量导出；
- 将 MP4 与 M4A 管线重构为大型通用导出框架。
