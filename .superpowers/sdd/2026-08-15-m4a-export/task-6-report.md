# Task 6 Report: M4A 用例编排与失败清理

## 状态

完成。实现限定在 M4A 同步导出用例层；未执行 Git 命令，未提交任何更改。

## 实现

- 新增 `M4aExportRequest`，包含 `entryId`、`title` 和只读缓存位置 `CacheEntryLocation`。
- 新增独立的 `M4aExportOutcome`：`Success(outputUri)`、`Unsupported(M4aUnsupportedReason)`、稳定字符串 `Failure(code)` 与 `Cancelled`。
- 新增 `M4aExportListener : M4aEngineListener`，提供 `onAnalyzing()`。
- 新增同步 `M4aExportUseCase`：
  - 按顺序执行分析通知、定位、候选过滤、探测、规划、待定输出创建、引擎导出和提交；不含 Worker/UI 依赖。
  - 在工作前、定位后、探测后和创建待定输出后检查取消；创建输出后的取消会尝试放弃该输出。
  - 仅将扩展名（`Locale.ROOT` 小写）为 `m4s`、`mp4`、`m4a` 的候选交给探测；因此 `.M4A` 被接受。
  - 所有探测结果均传给 `M4aExportPlanner`。
  - 未支持规划结果不会创建输出。
  - 成功仅提交一次；引擎失败、引擎取消、引擎异常、提交异常和创建后取消均通过同一个清理路径尝试 `abandon`。
  - 若 `abandon` 失败，返回 `OUTPUT_CLEANUP_FAILED`；提交失败且清理成功时保持 `OUTPUT_COMMIT_FAILED`。
  - 为验证规划器异常到稳定失败码的映射，使用包内 `M4aPlanningDelegate` 和 `M4aExportUseCase.Testing.create` 测试工厂；生产构造仍只接受 `M4aExportPlanner`。

## 文件

- `app/src/main/java/com/example/bili2media/export/m4a/usecase/M4aExportRequest.kt`
- `app/src/main/java/com/example/bili2media/export/m4a/usecase/M4aExportOutcome.kt`
- `app/src/main/java/com/example/bili2media/export/m4a/usecase/M4aExportUseCase.kt`
- `app/src/test/java/com/example/bili2media/export/m4a/usecase/M4aExportUseCaseTest.kt`

## RED / GREEN 证据

### RED

先新增 M4A JVM 测试、未添加生产类型时运行指定命令。`compileDebugUnitTestKotlin` 失败，报出预期缺失：

- `Unresolved reference 'M4aExportRequest'`
- `Unresolved reference 'M4aExportOutcome'`
- `Unresolved reference 'M4aExportUseCase'`
- `Unresolved reference 'M4aExportListener'`

测试夹具的 `MediaTrackInfo` 缺少 `width` / `height` 参数已修正后再次执行，失败仍只来自上述 Task 6 生产 API 缺失，确认 RED 有效。

### GREEN

实现最小生产代码后，目标测试成功。随后将测试注入点重构为内部规划委托和测试工厂，并再次执行同一命令，最终结果为：

`BUILD SUCCESSFUL in 3s`

覆盖的行为包括：

- 成功输出只提交一次；
- 未支持时不创建输出；
- 引擎失败、取消和异常都会放弃输出；
- 提交失败会尝试放弃输出；清理失败返回 `OUTPUT_CLEANUP_FAILED`；
- locator、probe、planning、output-create、listener 和 engine 的稳定失败码；
- 四个要求的取消检查点；
- 非 `m4s` / `mp4` / `m4a` 候选不探测；
- 大小写不敏感的 `.M4A` 候选可导出。

## 命令结果

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat testDebugUnitTest --tests "*M4aExportUseCaseTest" --console=plain
```

最终执行：退出码 `0`，`24 actionable tasks: 5 executed, 19 up-to-date`，`BUILD SUCCESSFUL`。

额外静态边界检查在 `export/m4a/usecase` 中搜索 `Mp4Export*`、`WorkManager`、`Worker`、`androidx.work`、`.delete(`、`.move(` 和 `.write(`：无匹配；输出为 `NO_FORBIDDEN_REFERENCES`。

## 边界自审

- 未依赖任何 MP4 use-case 类、WorkManager、Android UI 或 Worker。
- 缓存输入仅由 `CacheMediaLocator.locate` 和 `MediaProbe.probe` 消费；本层没有源写、删或移动 API。
- 仅通过 `M4aOutputStore` 管理目标输出的 create / commit / abandon。
- 不涉及 FFmpeg、NDK、LAME、解码、重编码，未实现 Task 7 或后续功能。
- 验证为 JVM 单元测试；没有声称覆盖设备端 MediaStore 或实际媒体复用运行时行为。

## Fix round 1: cancellation-listener exception handling

### 根因与范围

Review 发现 `M4aExportUseCase.execute` 的四个 `listener.isCancelled()` 检查此前位于异常边界外。`isCancelled()` 抛出 `Exception` 时，前三个检查点会直接泄漏异常；第四个检查点位于 pending output 创建后，也会跳过 `abandon`，从而遗留待定输出。

本轮只修改 Task 6 的 M4A use-case 与其 JVM 测试，未执行 Git 命令。

### RED

先新增两个最小测试，再运行：

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat testDebugUnitTest --tests "*M4aExportUseCaseTest" --console=plain
```

结果为 `14 tests completed, 2 failed`，两个失败都为未捕获的 `java.lang.IllegalStateException`：

- `execute_returnsListenerFailureWithoutCreatingOutputWhenInitialCancellationCheckThrows`（初始检查，第 172 行）；
- `execute_abandonsOutputWhenPostCreationCancellationCheckThrows`（创建 pending output 后的检查，第 186 行）。

这证明旧实现没有将取消回调异常转换为 `LISTENER_FAILED`，且后一个路径没有清理 pending output。

### 最小修复

- 在 `M4aExportUseCase` 增加私有 `cancellationOutcome(listener)` helper：正常未取消时返回 `null`，取消时返回 `Cancelled`，`isCancelled()` 抛出 `Exception` 时返回 `Failure(LISTENER_FAILED)`。
- 四个取消检查点均复用该 helper。
- output 创建后的检查将任何非空结果传入 `abandonThen(output, outcome)`；因此取消和 listener 异常都会清理一次 pending output，若清理失败仍保留既有 `OUTPUT_CLEANUP_FAILED` 规则。

### Reviewer Minor 回归保护

增强测试 fake 并添加透传测试：

- `FakeProbe` 记录每个实际 `MediaProbeResult`，测试确认 planner delegate 收到完整结果列表；
- `FakeEngine` 记录 `plan`、`outputUri` 和 `listener`，测试确认它收到 planner 输出的同一个 plan、pending URI `content://output/1` 和同一个 listener 实例。

### GREEN

应用最小修复后，新增异常测试先通过；添加透传回归保护后重新运行同一命令，最终结果：

`BUILD SUCCESSFUL in 2s`

退出码为 `0`，`24 actionable tasks: 2 executed, 22 up-to-date`。

### 文件变化

- `app/src/main/java/com/example/bili2media/export/m4a/usecase/M4aExportUseCase.kt`
- `app/src/test/java/com/example/bili2media/export/m4a/usecase/M4aExportUseCaseTest.kt`
- 本报告文件

## Final fix: cancellation after engine success

### 问题与范围

Review 发现引擎返回 `M4aEngineResult.Success` 后、`outputStore.commit` 前没有取消检查。若引擎资源关闭阶段发生取消，Worker 最终可被取消，但 M4A 已被发布。本轮只修改 M4A Task 6 use-case 与它的 JVM 测试；未修改 MP4 用例，未执行 Git 命令。

### RED

先在 `M4aExportUseCaseTest` 增加 3 条测试。`FakeEngine` 在返回 `Success` 前令同一个 listener 的后续 `isCancelled()`：

- 返回 `true`：期待 `Cancelled`、仅 `abandon`，没有 `commit`；
- 抛异常：期待 `Failure(LISTENER_FAILED)`、仅 `abandon`；
- 抛异常且 `abandon` 失败：期待 `Failure(OUTPUT_CLEANUP_FAILED)`。

运行：

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat testDebugUnitTest --tests "*M4aExportUseCaseTest" --console=plain --rerun-tasks
```

结果为 `18 tests completed, 3 failed`。三个新测试均在断言处失败，证明旧 `Success -> commit` 路径没有观察引擎结束后的取消状态。

### 最小修复与 GREEN

仅修改 `M4aEngineResult.Success` 分支：在 `commit(output)` 前调用既有 `cancellationOutcome(listener)`；若为取消或 `LISTENER_FAILED`，立即通过 `abandonThen(output, outcome)` 返回，否则提交。

以同一 `--rerun-tasks` 命令重新运行，结果：`BUILD SUCCESSFUL in 15s`，退出码 `0`，`24 actionable tasks: 24 executed`。

### 文件变化

- `app/src/main/java/com/example/bili2media/export/m4a/usecase/M4aExportUseCase.kt`
- `app/src/test/java/com/example/bili2media/export/m4a/usecase/M4aExportUseCaseTest.kt`
- 本报告文件
