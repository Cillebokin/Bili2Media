# Task 6: Orchestrate discovery, planning, output, and cleanup

## Files

- Create `app/src/main/java/com/example/bili2media/export/m4a/usecase/M4aExportRequest.kt`
- Create `app/src/main/java/com/example/bili2media/export/m4a/usecase/M4aExportOutcome.kt`
- Create `app/src/main/java/com/example/bili2media/export/m4a/usecase/M4aExportUseCase.kt`
- Create `app/src/test/java/com/example/bili2media/export/m4a/usecase/M4aExportUseCaseTest.kt`

## Required interfaces

```kotlin
data class M4aExportRequest(
    val entryId: String,
    val title: String,
    val location: CacheEntryLocation
)

interface M4aExportListener : M4aEngineListener {
    fun onAnalyzing()
}
```

`M4aExportOutcome` must independently model success with output URI, unsupported with `M4aUnsupportedReason`, stable string-coded failure, and cancellation.

## Required behavior

- Synchronous orchestration only; no WorkManager or Android UI dependencies.
- Check cancellation before work, after discovery, after probing, and after pending output creation.
- Notify `onAnalyzing()` before discovery; a listener exception returns stable `LISTENER_FAILED`.
- Locate files from the request location.
- Filter candidates case-insensitively to extensions `m4s`, `mp4`, and `m4a` only.
- Probe filtered candidates and pass all probe results to `M4aExportPlanner`.
- Unsupported planning must return without creating output.
- Create exactly one pending M4A output only for a ready plan.
- Call `M4aExportEngine` with the plan, pending URI, and listener.
- Engine success commits and returns the URI.
- Engine failure, cancellation, or exception abandons the pending output.
- Commit failure also attempts abandon.
- If abandon itself fails, return stable `OUTPUT_CLEANUP_FAILED`.
- Do not include any source write/delete/move API.

## Stable failure codes

- `LISTENER_FAILED`
- `LOCATE_FAILED`
- `PROBE_FAILED`
- `PLANNING_FAILED`
- `OUTPUT_CREATE_FAILED`
- `ENGINE_EXCEPTION`
- `ENGINE_<M4aEngineError.name>`
- `OUTPUT_COMMIT_FAILED`
- `OUTPUT_CLEANUP_FAILED`

## TDD coverage

Write fakes and prove:

- success commits exactly once;
- unsupported creates no output;
- engine failure abandons;
- engine cancellation abandons and returns cancelled;
- engine exception abandons;
- commit failure attempts abandon and returns commit failure when cleanup succeeds;
- cleanup failure returns `OUTPUT_CLEANUP_FAILED`;
- locator, probe, planning, output-create, listener and engine error codes remain stable;
- non-M4S/MP4/M4A files are not probed;
- case-insensitive M4A is accepted.

Run RED before production implementation and GREEN after:

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat testDebugUnitTest --tests "*M4aExportUseCaseTest" --console=plain
```

## Global constraints

- Never run Git commands and do not commit.
- Every file edit must use `apply_patch`.
- Cache sources are strictly read-only.
- Do not introduce FFmpeg, NDK, LAME, decoding, or re-encoding.
- Keep this package independent from MP4 use-case classes and from Worker/UI code.
- Reuse shared cache/media models and M4A planner/engine/output interfaces only.
- Follow high cohesion and low coupling; do not implement Task 7 or later.
