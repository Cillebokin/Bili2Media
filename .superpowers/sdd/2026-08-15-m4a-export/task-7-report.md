# Task 7 report: independent M4A foreground export work

## Result

Implemented the independent M4A WorkManager export contract, use-case factory, and foreground `CoroutineWorker`. M4A stays independent from MP4; the only shared implementation is the atomic request-order generator.

## TDD evidence

### RED

1. Added `M4aExportWorkContractTest` before any M4A work-contract production code.
2. Ran:

   ```powershell
   $env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
   .\gradlew.bat testDebugUnitTest --tests "*M4aExportWorkContractTest" --console=plain
   ```

3. Result: expected failure in `:app:compileDebugUnitTestKotlin`, caused by unresolved `M4aExportWorkContract`. This established the missing feature boundary before implementation.

### GREEN

Implemented the smallest production surface required by the new contract test:

- File and document-directory request serialization and decoding.
- Missing, blank, and unknown request data rejection.
- M4A-specific work names and tags, `KEEP` policy, and robust tag parsers.
- Shared monotonic default request ordering.

Then reran the focused M4A contract test; it passed.

## Verification

All commands below were run with `JAVA_HOME` set to `D:\MySoftwares\Tools\Android Studio\Outter 2\jbr`.

| Check | Command | Result |
| --- | --- | --- |
| M4A contract GREEN | `./gradlew.bat testDebugUnitTest --tests "*M4aExportWorkContractTest" --console=plain` | Passed, exit 0 |
| MP4 contract regression | `./gradlew.bat testDebugUnitTest --tests "*Mp4ExportWorkContractTest" --console=plain` | Passed, exit 0 |
| Required M4A test plus debug APK | `./gradlew.bat testDebugUnitTest --tests "*M4aExportWorkContractTest" assembleDebug --console=plain` | Passed, exit 0 |

## Changed files

- `app/src/main/java/com/example/bili2media/export/work/ExportRequestOrder.kt` (new): one shared `AtomicLong` generator using `maxOf(System.currentTimeMillis(), previous + 1)`.
- `app/src/main/java/com/example/bili2media/export/work/Mp4ExportWorkContract.kt`: now delegates its default request order to `ExportRequestOrder` without changing MP4 tags, serialization, or parsing.
- `app/src/main/java/com/example/bili2media/export/m4a/work/M4aExportWorkContract.kt` (new): M4A-only request data, tags, naming, parsing, and work-request construction.
- `app/src/main/java/com/example/bili2media/export/m4a/work/M4aExportUseCaseFactory.kt` (new): constructs exactly the prescribed M4A locator, probe, planner, output store, engine, and use case.
- `app/src/main/java/com/example/bili2media/export/m4a/work/M4aExportWorker.kt` (new): independent foreground `CoroutineWorker` with M4A notification channel, M4A data keys, progress, cancellation, and outcome mapping.
- `app/src/test/java/com/example/bili2media/export/m4a/work/M4aExportWorkContractTest.kt` (new): contract coverage for both locations, invalid data, names/tags/policy, request tags, M4A-only tag parsing, newest valid order, and cross-format monotonic default ordering.
- `app/src/main/res/values/strings.xml`: minimal M4A foreground-notification strings.

## Boundary self-review

- `ExportRequestOrder` is the sole MP4/M4A shared code. M4A does not call MP4 worker, MP4 factory, or MP4 use case.
- M4A work identifiers are strictly `bili2media:m4a-export...`; M4A parsers only inspect M4A prefixes and ignore MP4 tags.
- Every M4A request contains the global, entry, and request-order tags. Retry ordering chooses the maximum valid M4A order.
- The worker rejects invalid input with `INVALID_INPUT`, enters foreground before blocking work, executes the synchronous use case on `Dispatchers.IO`, publishes M4A analyzing/exporting data, clamps progress, and throws `CancellationException` for cancellation.
- Foreground settings use channel `m4a_exports`, a positive Work UUID-derived notification ID, WorkManager's cancel pending intent, and `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.
- No UI work, cache mutation, FFmpeg, NDK, LAME, decoding, or re-encoding was added.

## Fix round 1: Worker behavior coverage

### Review finding and technical decision

The first version covered only the work contract. `M4aExportWorker` directly combined Android foreground construction, WorkManager calls, and the concrete use-case factory, so its runtime result mapping and progress behavior could not be verified by a JVM unit test.

Added an M4A-only, package-internal `M4aExportWorkerCoordinator` instead of `work-testing`/instrumentation:

- It owns input validation, foreground-before-export ordering, listener progress data construction and clamping, cancellation propagation, and mapping all M4A outcomes to actual `ListenableWorker.Result` output `Data`.
- `M4aExportWorker` keeps its standard WorkManager constructor and still creates the real use case through `M4aExportUseCaseFactory` in its production path.
- No MP4/M4A shared worker framework and no test dependency were introduced.
- `M4aExportForegroundSpec` is also package-internal and supplies the actual Worker with the M4A channel, positive UUID-derived ID, data-sync foreground type, and the same Work UUID passed to `createCancelPendingIntent`.

### RED

Added `M4aExportWorkerCoordinatorTest` before implementing the coordinator/spec and ran:

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat testDebugUnitTest --tests "*M4aExportWorkerCoordinatorTest" --console=plain
```

The expected RED failed in `:app:compileDebugUnitTestKotlin` because `M4aExportWorkerCoordinator` and `M4aExportForegroundSpec` did not yet exist. One test-only `Data` API signature mismatch was corrected before the production implementation, leaving the intended missing-production-type failure as the RED evidence.

### GREEN coverage

`M4aExportWorkerCoordinatorTest` asserts actual worker-result data and behavior for:

- invalid input: `INVALID_INPUT` failure and no foreground entry;
- success: foreground is requested before export and output contains entry ID plus URI;
- unsupported: `UNSUPPORTED_<reason>` code;
- use-case failure: stable code preserved;
- cancelled: `CancellationException` is thrown;
- analyzing and out-of-range progress: M4A phase/data keys and clamped `0`/`100` values;
- foreground boundary: M4A channel `m4a_exports`, positive Work-UUID-derived notification ID, data-sync type, and the exact Work UUID used for cancellation.

`M4aExportWorkContractTest` now also separately rejects missing title, missing location type, missing location value, and a blank location type. The pre-existing decoder already implemented these checks, so these supplemental contract assertions were immediately green rather than a new RED condition.

### Fix round verification

All commands used `JAVA_HOME=D:\MySoftwares\Tools\Android Studio\Outter 2\jbr` and passed with exit code 0:

| Check | Command |
| --- | --- |
| New Worker behavior test | `./gradlew.bat testDebugUnitTest --tests "*M4aExportWorkerCoordinatorTest" --console=plain` |
| M4A contract | `./gradlew.bat testDebugUnitTest --tests "*M4aExportWorkContractTest" --console=plain` |
| MP4 contract regression | `./gradlew.bat testDebugUnitTest --tests "*Mp4ExportWorkContractTest" --console=plain` |
| Debug APK | `./gradlew.bat assembleDebug --console=plain` |

Instrumentation was not selected: no `work-testing` dependency was added, and the focused internal seam verifies the M4A runtime coordination without an emulator/device requirement.
