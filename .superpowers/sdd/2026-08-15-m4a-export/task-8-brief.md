# Task 8: Add independent per-row M4A actions and state

## Files

- Create `app/src/main/java/com/example/bili2media/ui/export/m4a/M4aExportUiState.kt`
- Create `app/src/main/java/com/example/bili2media/ui/export/m4a/M4aExportStateMapper.kt`
- Create `app/src/main/java/com/example/bili2media/ui/export/m4a/M4aExportViewModel.kt`
- Modify `app/src/main/java/com/example/bili2media/ui/export/BiliCacheListItem.kt`
- Modify `app/src/main/java/com/example/bili2media/ui/BiliCacheAdapter.kt`
- Modify `app/src/main/java/com/example/bili2media/MainActivity.kt`
- Modify `app/src/main/res/layout/item_bili_cache.xml`
- Modify `app/src/main/res/values/strings.xml`
- Modify `app/src/main/res/values/dimens.xml`
- Create `app/src/test/java/com/example/bili2media/ui/export/m4a/M4aExportStateMapperTest.kt`

## M4A UI state

Create an independent sealed state matching the user-visible lifecycle:

- `Idle`
- `Queued`
- `Analyzing`
- `Exporting(progress: Int)`
- `Succeeded(outputUri: String)`
- `Failed(errorCode: String)`
- `Cancelled`

Do not make it inherit from or wrap `Mp4ExportUiState`.

## State mapper

- Accept `List<WorkInfo>` and return `Map<entryId, M4aExportUiState>`.
- Parse only M4A entry/order tags through `M4aExportWorkContract`.
- Ignore MP4-only and malformed tags.
- Select the newest request order first; use WorkInfo generation only as tie-breaker.
- Missing request-order tag is a legacy value lower than every valid request order.
- Among legacy records, higher generation wins.
- ENQUEUED/BLOCKED -> Queued.
- RUNNING + analyzing phase -> Analyzing.
- Other RUNNING -> Exporting with progress clamped 0..100.
- SUCCEEDED requires nonblank output URI; otherwise `Failed("MISSING_OUTPUT_URI")`.
- FAILED uses nonblank error code or `UNKNOWN_ERROR`.
- CANCELLED -> Cancelled.

## ViewModel

- `M4aExportViewModel` is independent from MP4 ViewModel.
- Observe only `M4aExportWorkContract.TAG_ALL_EXPORTS`.
- Expose `LiveData<Map<String, M4aExportUiState>>`.
- `enqueue(entry)` builds `M4aExportRequest`, uses M4A unique name/policy/work request.
- `cancel(entryId)` cancels only the M4A unique work.

## List item and adapter

- Extend `BiliCacheListItem` with `m4aExportState: M4aExportUiState = Idle` while preserving the existing MP4 state and behavior.
- Add Adapter callbacks for M4A export, cancel, and open; Adapter/ViewHolder must not import or expose WorkManager.
- Keep existing MP4 row behavior unchanged.
- Add a second visually matching row below MP4 with exact IDs:
  - `progressM4aExport`
  - `txtM4aExportStatus`
  - `btnM4aExportAction`
- Use a named dimen for spacing between MP4 and M4A rows; no unexplained new hard-coded spacing.
- M4A Idle/Failed/Cancelled action enabled only when cache status is AVAILABLE.
- Render M4A actions as `导出 M4A`, `取消`, `打开 M4A`, or `重试`.
- Reuse generic queued/analyzing/progress/succeeded/failed/cancelled status strings where wording is format-neutral; add an M4A-specific ready string.

## MainActivity

- Own both MP4 and M4A ViewModels and both latest state maps.
- Observe each independently and combine scanned entries with both states in `BiliCacheListItem`.
- M4A export/cancel callbacks call only M4A ViewModel.
- Request POST_NOTIFICATIONS on the first export of either format using the existing one-time flow.
- Open successful M4A with `ACTION_VIEW`, MIME `audio/mp4`, and `FLAG_GRANT_READ_URI_PERMISSION`.
- Add an M4A-specific open-failure toast.
- A small shared private media-opening helper is allowed, but do not introduce a broad export UI framework.

## Strings

Add only the minimal UI strings needed for:

- `导出 M4A`
- `打开 M4A`
- `可无损导出为 M4A`
- no-app-can-open-M4A failure

Task 7 already added the M4A notification strings; do not duplicate them.

## TDD coverage

Write `M4aExportStateMapperTest` first and verify RED. Cover:

- ENQUEUED and BLOCKED;
- analyzing phase;
- exporting progress and clamping;
- succeeded URI;
- failed code;
- cancelled;
- missing terminal data fallbacks;
- newer request order wins even when generation is lower;
- equal order uses generation;
- legacy records without order use generation;
- any valid ordered retry beats legacy;
- MP4-only tags are ignored.

Run RED then GREEN/build:

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat testDebugUnitTest --tests "*M4aExportStateMapperTest" --console=plain
.\gradlew.bat testDebugUnitTest --tests "*M4aExportStateMapperTest" assembleDebug --console=plain
```

Also rerun `Mp4ExportStateMapperTest` after integration.

## Global constraints

- Never run Git commands and do not commit.
- Every edit must use `apply_patch`.
- Cache source files remain read-only.
- No FFmpeg, NDK, LAME, decoding, or re-encoding.
- Keep M4A UI packages independent from MP4 implementation classes except the combined list item/adapter/activity that intentionally render both.
- WorkManager imports are allowed only in the M4A ViewModel/mapper/work packages, never in Adapter or list-item rendering models.
- Preserve high cohesion and low coupling; do not start Task 9 changes beyond requested verification commands.
