# Task 7: Run M4A exports as independent foreground work

## Files

- Create `app/src/main/java/com/example/bili2media/export/work/ExportRequestOrder.kt`
- Modify `app/src/main/java/com/example/bili2media/export/work/Mp4ExportWorkContract.kt`
- Create `app/src/main/java/com/example/bili2media/export/m4a/work/M4aExportWorkContract.kt`
- Create `app/src/main/java/com/example/bili2media/export/m4a/work/M4aExportUseCaseFactory.kt`
- Create `app/src/main/java/com/example/bili2media/export/m4a/work/M4aExportWorker.kt`
- Create `app/src/test/java/com/example/bili2media/export/m4a/work/M4aExportWorkContractTest.kt`
- Add the minimal M4A foreground-notification strings to `app/src/main/res/values/strings.xml`

## Work contract

- Global tag: `bili2media:m4a-export`
- Unique work name: `bili2media:m4a-export:<entryId>`
- Entry tag: `bili2media:m4a-export-entry:<entryId>`
- Request-order tag prefix: `bili2media:m4a-export-order:`
- Existing work policy: `ExistingWorkPolicy.KEEP`
- Encode/decode `M4aExportRequest` for both `FileDirectory` and `DocumentDirectory`.
- Reject missing, blank, or unknown request `Data` by returning null.
- Every work request carries global, entry, and request-order tags.
- `entryIdFromTags` uses only the M4A entry prefix and returns a stable non-blank result.
- `requestOrderFromTags` uses only the M4A order prefix and selects the maximum valid order.
- MP4 and M4A unique names/tags must remain distinct.

## Shared request order

- Extract only the atomic monotonic request-order generator from `Mp4ExportWorkContract` into `ExportRequestOrder`.
- Its `next()` result must be `maxOf(System.currentTimeMillis(), previous + 1)` using one shared `AtomicLong`.
- MP4 and M4A contracts both call this generator for their default request order.
- Do not share tag prefixes, parsers, request serialization, or other format-specific behavior.

## Factory

`M4aExportUseCaseFactory.create(context)` constructs only:

- `AndroidCacheMediaLocator`
- `AndroidMediaExtractorProbe`
- `M4aExportPlanner`
- `MediaStoreM4aOutputStore`
- `AndroidM4aExportEngine`
- `M4aExportUseCase`

## Foreground worker

- `M4aExportWorker` is an independent `CoroutineWorker`.
- Decode with `M4aExportWorkContract`; invalid input returns failure code `INVALID_INPUT`.
- Enter foreground before blocking work.
- Run the synchronous use case inside `withContext(Dispatchers.IO)`.
- Publish analyzing phase and exporting progress through M4A contract keys.
- Clamp progress to 0..100.
- Listener cancellation is `isStopped`.
- Success returns entry id and output URI.
- Unsupported maps to `UNSUPPORTED_<reason.name>`.
- Use-case failure returns its stable code.
- Cancelled throws `CancellationException`.
- Notification channel id is exactly `m4a_exports`.
- Notification copy is M4A-specific (channel/title/analyzing/progress/cancel strings).
- Notification ID is derived from the Work UUID hash and is always positive.
- Cancellation action uses WorkManager's cancel pending intent.
- Foreground type is `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`.

## TDD coverage

Write the M4A contract test first and verify RED. Cover:

- File/Document request round-trip;
- invalid/missing/blank/unknown Data returns null;
- M4A tag/name exact values and KEEP policy;
- MP4 and M4A unique names differ;
- work request contains global, entry, and order tags;
- M4A tag parsers ignore MP4 tags;
- multiple valid order tags select the newest retry;
- default order generation is monotonic across MP4 and M4A requests.

Run RED then GREEN:

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat testDebugUnitTest --tests "*M4aExportWorkContractTest" --console=plain
.\gradlew.bat testDebugUnitTest --tests "*M4aExportWorkContractTest" assembleDebug --console=plain
```

Also run the existing `Mp4ExportWorkContractTest` after extracting the generator.

## Global constraints

- Never run Git commands and do not commit.
- Every edit must use `apply_patch`.
- Cache inputs remain read-only.
- No FFmpeg, NDK, LAME, decoding, or re-encoding.
- Keep M4A Worker/contract independent of MP4 Worker/use-case; only `ExportRequestOrder` is shared.
- Do not implement UI state or Task 8.
- Preserve existing MP4 behavior and tests.
