# SDD ledger - plan: docs/superpowers/plans/2026-08-15-m4a-export.md

Task 1: complete - media discovery and probe extension
Task 2: complete - shared file name policy
Task 3: complete - pure Kotlin M4A planner
Task 4: complete - MediaStore M4A output
Task 5: complete - Android M4A export engine; fix round 1 addressed terminal progress ordering; Android 16 focused tests 5/5
Task 5: minor (deferred) - direct-copy test checks terminal 100 but not explicitly every prior value <= 99
Task 6: complete - M4A use-case orchestration and cleanup; fix round 1 handled listener cancellation exceptions and pending cleanup
Task 7: complete - independent M4A foreground work; fix round 1 added M4A-only worker coordinator tests
Task 8: complete - independent per-row M4A UI state and actions; review clean
Task 9: in progress - full verification and dependency boundary audit

Final SAF fix: complete - `DocumentCacheMediaLocator` now injects a child-listing
boundary; production traversal queries child document IDs through
`ContentResolver`/`DocumentsContract` while fake tests retain `DocumentFile.listFiles()`.
Android 16 focused locator tests: 3/3.

Collection output title: complete - MP4 and M4A share a pure title resolver that
uses `Title - Subtitle` when the parsed subtitle is non-blank and distinct from
the title. Missing or duplicate subtitles keep the original title.

Fresh verification after the SAF and collection-title changes:

- JVM tests: 118/118, zero failures/errors/skips.
- Android 16 instrumentation tests: 26/26.
- Lint: zero errors, 21 non-blocking warnings; no warning points to changed files.
- Debug APK: 16,329,376 bytes.
- AndroidTest APK: 1,538,959 bytes.
- Static dependency/read-only boundary checks: all passed.
- Latest Debug APK installation retry: externally blocked by
  `INSTALL_FAILED_USER_RESTRICTED` (`Install canceled by user`).

Execution constraint: do not run Git commands. Reviews use current-file inspection and fresh test evidence instead of commit ranges.
