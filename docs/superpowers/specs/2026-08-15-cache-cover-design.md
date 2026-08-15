# Bili2Media 缓存封面显示设计

## 目标

为缓存扫描列表增加视频封面。封面来源按“本地优先、网络兜底”选择：优先使用缓存目录中的本地封面文件，否则使用 `entry.json` 中的封面网址，均不可用时显示统一占位图。

## 模块化边界

当前项目规模保持单 `app` Gradle 模块，避免为一个小功能引入多模块构建成本；代码按可独立拆分的职责包组织，并强制单向依赖：

```text
cache/model <- cache/parser
cache/model <- cache/cover
cache/model + cache/cover <- cache/scanner
cache/model <- ui/adapter -> ui/image
ui/image/CoilCoverImageLoader -> Coil
```

- `cache/model`：纯数据模型，不依赖 Android View、Coil 或网络库。
- `cache/parser`：只解析 JSON，输出元数据，不加载图片。
- `cache/cover`：识别本地封面文件名、规范化网络 URL、决定来源优先级。
- `cache/scanner`：发现本地封面 URI，并将其交给封面策略；不下载图片。
- `ui/image`：定义图片加载接口及 Coil 实现。Coil 依赖只能出现在该包。
- `ui`：渲染 `CoverSource`，不理解封面字段解析和优先级规则。

未来增加转换功能时，可将 `cache/model`、`cache/parser`、`cache/cover` 和 File 扫描器平移到独立 `:core-cache` 模块，不需要修改 UI 契约。

## 数据模型

新增纯 Kotlin 封面来源类型：

- `CoverSource.Local(uri: String)`
- `CoverSource.Remote(url: String)`

`BiliCacheMetadata` 增加可空 `coverUrl`，`BiliCacheEntry` 增加可空 `coverSource`。

`BiliCacheCoverResolver.resolve(localCoverUri, remoteCoverUrl)` 负责唯一的优先级规则：有效本地 URI优先；否则规范化远程 URL；否则返回 `null`。远程地址的 `http://` 和 `//` 形式统一转成 `https://`。

## 封面发现与解析

元数据解析顺序：

1. 顶层 `cover`
2. `page_data.cover`
3. `ep.cover`

本地扫描只识别以下明确文件名，忽略大小写：

- `cover.jpg`
- `cover.jpeg`
- `cover.png`
- `cover.webp`

File 扫描器输出 `file:` URI；DocumentFile 扫描器输出持久化授权下的 `content:` URI。扫描过程不读取图片内容，也不执行网络请求。

## 图片加载适配层

新增 `CoverImageLoader` 接口，接收 `ImageView` 和 `CoverSource?`。`CoilCoverImageLoader` 是唯一 Coil 实现：

- 可见列表项才开始请求；
- 利用 Coil 的内存和磁盘缓存；
- 新绑定会取消旧请求，避免 RecyclerView 复用错图；
- 使用淡入效果；
- 空来源、加载中和失败都显示同一占位图。

Manifest 增加 `INTERNET` 权限。网络封面只使用 HTTPS；不允许明文 HTTP。

## 界面

缓存卡片改为横向结构：左侧固定 16:9 圆角缩略图，右侧保持现有标题、状态、分集、大小、ID 和路径。缩略图不改变列表项的点击行为，首版不增加封面预览页面。

## 测试与验证

单元测试覆盖：

- 解析三个位置的封面字段；
- 本地封面优先于网络地址；
- `http://` 和 `//` 地址升级为 HTTPS；
- 非 HTTP(S) 远程地址被拒绝；
- File 扫描器发现本地封面并写入 `CoverSource.Local`；
- 没有本地封面时使用 `CoverSource.Remote`。

完成后运行 `testDebugUnitTest`、`lintDebug` 和 `assembleDebug`。自动验证不代表网络图片已在 Android 16 真机成功加载，真机仍需分别检查本地封面、联网封面、离线和加载失败四种状态。
