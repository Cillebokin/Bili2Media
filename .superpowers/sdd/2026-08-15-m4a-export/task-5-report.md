# Task 5 report: M4A direct copy and AAC remux engine

## Status

DONE. No Git commands were run and no cache source was modified.

## Implementation

- Added an independent `M4aExportEngine` contract, listener, result, and error model.
- Added `AndroidM4aExportEngine` with two isolated paths:
  - `CopyExistingM4a` opens file or content inputs plus the MediaStore output stream, copies with a 256 KiB buffer, checks cancellation for every chunk, and closes streams through `use`.
  - `RemuxAacTrack` uses exactly one `MediaExtractor` and one `MediaMuxer`, retains an `AssetFileDescriptor` for the extractor lifetime when reading content, accepts only the planned `audio/mp4a-latm` track, and writes only that track.
- Remux grows the sample buffer to the extractor-declared sample size, rejects encrypted samples, maps only sync and partial-frame flags, normalizes the first timestamp to zero, and never emits a negative or decreasing output timestamp.
- Copy and remux progress are capped at 99 during work. The terminal 100 is emitted only after stream/muxer closure succeeds and the final result is `Success`.
- Strengthened the instrumentation assertion so every emitted output sample timestamp is checked, rather than only the first sample.

## Files

- Added `app/src/main/java/com/example/bili2media/export/m4a/engine/M4aExportEngine.kt`
- Added `app/src/main/java/com/example/bili2media/export/m4a/engine/M4aEngineResult.kt`
- Added `app/src/main/java/com/example/bili2media/export/m4a/engine/M4aEngineError.kt`
- Added `app/src/main/java/com/example/bili2media/export/m4a/engine/AndroidM4aExportEngine.kt`
- Modified `app/src/androidTest/java/com/example/bili2media/export/m4a/engine/AndroidM4aExportEngineTest.kt`

## RED / GREEN evidence

- RED baseline: established before this task by the parent agent, as specified in `task-5-brief.md`: `assembleDebugAndroidTest` failed only because `AndroidM4aExportEngine`, `M4aEngineResult`, and `M4aEngineListener` were absent.
- GREEN: after adding the independent engine types and implementation, `assembleDebugAndroidTest` completed successfully.
- GREEN: after strengthening the timestamp assertion and correcting final-progress ordering, all three targeted Android instrumentation tests completed successfully on device `24117RK2CC - 16`.

## Verification commands and results

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat assembleDebugAndroidTest --console=plain
```

Result: `BUILD SUCCESSFUL` (47 actionable tasks; final run compiled both debug and Android test Kotlin).

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.bili2media.export.m4a.engine.AndroidM4aExportEngineTest' --console=plain
```

Result: `BUILD SUCCESSFUL`; 3 tests started and finished on `24117RK2CC - 16`.

The brief's unquoted `-P...class=...` form was also attempted first. PowerShell/Gradle treated it as a task path and failed before running tests; quoting the same Gradle project-property argument above preserved the requested test filter and ran the tests successfully.

## Self-review questions

- Does the M4A implementation depend on the MP4 engine? No. It shares only existing transport models (`MediaInputRef` and `MediaTrackSelection`), and does not call or reuse the MP4 implementation class.
- Can source cache inputs be changed? No. Both paths perform read-only reads; output is the supplied MediaStore URI.
- Is 100% visible on a failed muxer close? No. The implementation reports final 100 only after `stop`/`release` complete without changing the successful result.
- Are all required acceptance assertions exercised? Yes: byte-exact copy, M4S-to-AAC-only M4A, AAC extraction from combined MP4, audio format preservation, all output sample timestamps non-negative, and final progress 100.
- What remains outside this task's current instrumentation coverage? Cancellation, invalid/encrypted tracks, content-URI input, and oversized samples are implemented defensively but do not have dedicated device tests in the provided Task 5 suite.

## Fix round 1: terminal progress after resource closure

### Review finding and root cause

- The review finding was confirmed. `runRemux()` still emitted 100 before its caller had closed the output `ParcelFileDescriptor` and the `ExtractorSession`; a descriptor close exception could therefore escape after a visible completion event.
- During the regression run, the new progress assertions exposed a remaining inner `runRemux()` 100% call. It produced a duplicate 100 on normal remuxes and a premature 100 when the output-descriptor-close failure test was simulated. The inner call was removed.

### Regression tests

- Added a package-internal constructor seam that accepts a resource-close policy while preserving the existing public `AndroidM4aExportEngine(context)` constructor and the `M4aExportEngine` interface.
- `export_reportsCompletionAfterOutputAndExtractorSessionClose` records actual resource close calls and verifies that both the output descriptor and extractor session have completed closure when the listener receives 100.
- `export_mapsOutputDescriptorCloseFailureWithoutReportingCompletion` closes the actual resource, then reports an output descriptor close failure. It verifies the returned result is `Failure(MUXER_FAILED)` and that 100 is never emitted.
- Strengthened existing checks: the first output sample timestamp must be exactly zero, and every progress value before the terminal 100 must be at most 99.

### Production correction

- `runRemux()` now only muxes and releases the muxer; it never sends terminal progress.
- `remux()` closes the output descriptor and extractor session through a guarded close policy. Any close exception or failed close is converted to `MUXER_FAILED`; no close exception escapes.
- Only after both close operations succeed and the result remains `Success` does `remux()` notify progress 100.

### RED / GREEN evidence

- RED 1: after adding the closing-order regression tests and before adding the internal close-policy constructor, `assembleDebugAndroidTest` failed at `AndroidM4aExportEngineTest.kt:84` and `:113` with `Too many arguments for constructor(context: Context)`. This established the missing close-observation seam.
- RED 2: the first device run after adding the seam failed 4/5 relevant tests. The stack traces pointed to `assertTerminalProgress`; inspection showed the remaining `runRemux()` terminal-progress call, which emitted 100 before outer resource closure.
- GREEN: after removing the inner terminal-progress call, compilation and all targeted device tests passed.

### Fix round 1 verification

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat assembleDebugAndroidTest --console=plain
```

Result: `BUILD SUCCESSFUL` (47 actionable tasks; 3 executed).

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.bili2media.export.m4a.engine.AndroidM4aExportEngineTest' --console=plain
```

Result: `BUILD SUCCESSFUL`; 5 tests started and finished on Android 16 device `24117RK2CC - 16`.
