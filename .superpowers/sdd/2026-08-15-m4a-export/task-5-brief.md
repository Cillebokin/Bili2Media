# Task 5: Implement direct copy and AAC remux engine

## Files

- Create `app/src/main/java/com/example/bili2media/export/m4a/engine/M4aExportEngine.kt`
- Create `app/src/main/java/com/example/bili2media/export/m4a/engine/M4aEngineResult.kt`
- Create `app/src/main/java/com/example/bili2media/export/m4a/engine/M4aEngineError.kt`
- Create `app/src/main/java/com/example/bili2media/export/m4a/engine/AndroidM4aExportEngine.kt`
- Modify `app/src/androidTest/java/com/example/bili2media/testmedia/TestMediaFixtureFactory.kt` only if required
- Complete `app/src/androidTest/java/com/example/bili2media/export/m4a/engine/AndroidM4aExportEngineTest.kt`

## Required interfaces

```kotlin
interface M4aEngineListener {
    fun onProgress(percent: Int)
    fun isCancelled(): Boolean
}

sealed interface M4aEngineResult {
    data object Success : M4aEngineResult
    data object Cancelled : M4aEngineResult
    data class Failure(val error: M4aEngineError) : M4aEngineResult
}
```

Use errors `INPUT_OPEN_FAILED`, `OUTPUT_OPEN_FAILED`, `INVALID_TRACK`, `MUXER_FAILED`, and `COPY_FAILED`.

## Direct copy

- Open File/content sources and the MediaStore output stream.
- Copy with a 256 KiB buffer.
- Check cancellation every chunk.
- Close all streams with `use`.
- Report final progress 100 only on success.

## AAC remux

- Use one `MediaExtractor` and one `MediaMuxer`.
- Keep any backing `AssetFileDescriptor` open for the extractor lifetime.
- Validate the planned track index and exact AAC MIME `audio/mp4a-latm`.
- Select only the planned audio track and add only that format to the muxer.
- Normalize the first presentation timestamp to zero and keep later relative intervals non-negative.
- Grow the sample buffer when necessary.
- Reject encrypted samples and map only safe sample flags.
- Report duration-based progress capped at 99, then 100 only after successful completion.
- Stop the muxer only after it successfully starts; always release resources.

## Verification

The existing Android instrumentation test must prove:

- Existing M4A bytes are copied exactly.
- Audio-only M4S becomes one AAC-only M4A track.
- Combined MP4 exports only its AAC track.
- Sample rate and channel count are preserved.
- Output timestamps are non-negative.
- Final progress is 100.

Run:

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.bili2media.export.m4a.engine.AndroidM4aExportEngineTest --console=plain
```

## Global constraints

- Do not run any Git command and do not commit.
- Make every file edit through `apply_patch`.
- Cache source files are strictly read-only.
- Do not introduce FFmpeg, NDK, LAME, decoding, or AAC re-encoding.
- Keep the M4A engine independent of MP4 engine implementation classes; sharing existing data models is allowed.
- Preserve high cohesion and low coupling; do not implement Task 6 or later work.
- TDD RED is already established: `assembleDebugAndroidTest` fails only because `AndroidM4aExportEngine`, `M4aEngineResult`, and `M4aEngineListener` are missing.
