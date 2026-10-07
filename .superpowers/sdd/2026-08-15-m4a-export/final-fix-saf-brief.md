# Final fix: SAF document-directory media traversal

## Confirmed root cause

`DocumentTreeBiliCacheScanner` stores a candidate directory URI from a user-granted document tree in `CacheEntryLocation.DocumentDirectory`.

The production `DocumentCacheMediaLocator(context)` resolves that URI with `DocumentFile.fromSingleUri`. AndroidX `SingleDocumentFile` is suitable for metadata/read access but cannot enumerate children through `listFiles()`. The locator catches that failure and returns no media, so SAF-scanned entries export as `NO_MEDIA`.

## Required fix

- Keep `CacheEntryLocation.DocumentDirectory(uri)` compatible; do not redesign Work request serialization.
- Keep the locator traversal, nested-cache boundary rule, stable paths, and File locator behavior unchanged.
- Add a separate injected child-listing boundary to `DocumentCacheMediaLocator`.
- Test/fake construction may default to `DocumentFile.listFiles()` so existing FakeDocumentFile tests remain simple.
- The production Context constructor must use a ContentResolver/DocumentsContract child lister, not `DocumentFile.listFiles()`.
- For a directory document URI under a granted tree:
  - obtain its document ID;
  - build the children URI with `DocumentsContract.buildChildDocumentsUriUsingTree`;
  - query `DocumentsContract.Document.COLUMN_DOCUMENT_ID`;
  - build each child URI with `DocumentsContract.buildDocumentUriUsingTree`;
  - wrap child URIs with `DocumentFile.fromSingleUri` for metadata and input URI use.
- Query/cursor/resources must close safely. Permission/provider/query failures must remain a safe empty result, not crash.
- Do not use `DocumentFile.fromTreeUri` on the candidate child URI because that would resolve the original tree root rather than the candidate document.

## TDD

- First add a failing locator test proving a supplied child lister is used for root and nested directories. The test must fail before production code because the constructor/boundary does not exist.
- Preserve existing tests for content URI output, M4A discovery, stable sorting, and nested entry exclusion.
- Add a focused test for the production child-query adapter if practical without a new heavy dependency. Do not add Robolectric/Mockito solely for this fix.
- Device is currently disconnected, so compile `assembleDebugAndroidTest`; run the focused Android test if ADB becomes available. Clearly report if runtime instrumentation remains externally blocked.

## Verification

```powershell
$env:JAVA_HOME='D:\MySoftwares\Tools\Android Studio\Outter 2\jbr'
.\gradlew.bat assembleDebugAndroidTest --rerun-tasks --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.bili2media.cache.media.DocumentCacheMediaLocatorTest' --console=plain
```

## Constraints

- No Git commands; no commits.
- All edits use `apply_patch`.
- Cache sources remain read-only.
- No changes to MP4/M4A export planning or media encoding.
- Keep the Android provider adapter isolated in `cache/media`; scanners and locators still perform no output writes.
