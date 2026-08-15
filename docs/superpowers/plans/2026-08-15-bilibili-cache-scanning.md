# Bilibili Cache Scanning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scan Bilibili cache directories manually copied to public storage, parse their metadata and media fragments, and display read-only results in Bili2Media.

**Architecture:** Keep metadata parsing and file traversal independent from Android UI. Use a tested `File` scanner for the default `Download/Bili2Media/Input` directory, a `DocumentFile` scanner for persisted SAF trees, a small preference store for root selection, and an executor-backed XML/RecyclerView screen for permissions and rendering.

**Tech Stack:** Kotlin, Android XML Views, RecyclerView, AndroidX DocumentFile, `org.json`, JUnit 4, Gradle 8.13, AGP 8.13.1, minSdk 33, targetSdk 36.

## Global Constraints

- Target environment is Android 16 without Root or Shizuku.
- Never attempt to read `/Android/data/tv.danmaku.bili/`.
- Default input directory is `Download/Bili2Media/Input` and requires `MANAGE_EXTERNAL_STORAGE`.
- A persisted `ACTION_OPEN_DOCUMENT_TREE` selection remains usable without all-files access.
- Scanning is read-only: never modify, move, rename, or delete source files.
- Recognized media extensions are `.m4s`, `.blv`, `.flv`, `.mp4`, `.m4a`, and `.aac`.
- This phase does not merge or convert MP3/MP4 files.
- Keep the ComicLab-inspired XML, gray background, white rounded card visual language.
- Do not run Git commands or create commits.

---

### Task 1: Add scan models and metadata parser with tests

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/bili2media/cache/model/BiliCacheEntry.kt`
- Create: `app/src/main/java/com/example/bili2media/cache/model/BiliCacheMetadata.kt`
- Create: `app/src/main/java/com/example/bili2media/cache/parser/BiliCacheMetadataParser.kt`
- Create: `app/src/test/java/com/example/bili2media/cache/parser/BiliCacheMetadataParserTest.kt`

**Interfaces:**
- Produces: `BiliCacheMetadataParser.parse(jsonText: String): BiliCacheMetadata`.
- Produces: `BiliCacheEntry` with `id`, `title`, `subtitle`, `avid`, `cid`, `relativePath`, `mediaFileCount`, `totalBytes`, and `status`.
- Produces: `BiliCacheStatus` values `AVAILABLE`, `NO_MEDIA`, and `METADATA_ERROR`.

- [ ] **Step 1: Add dependencies needed by the feature and JVM tests**

Add RecyclerView 1.3.2, DocumentFile 1.0.1, and test-only `org.json:json:20240303` to the version catalog. Add RecyclerView and DocumentFile as `implementation` dependencies and JSON as `testImplementation`.

- [ ] **Step 2: Write failing metadata parser tests**

Create tests covering top-level title and IDs, `page_data.part`, `page_data.cid`, `ep.index_title`, and absent optional fields. The core test must include:

```kotlin
@Test
fun parse_readsCommonEntryFields() {
    val metadata = parser.parse(
        """{
            "title":"Example Video",
            "avid":123,
            "page_data":{"part":"Part 1","cid":456}
        }""".trimIndent()
    )

    assertEquals("Example Video", metadata.title)
    assertEquals("Part 1", metadata.subtitle)
    assertEquals(123L, metadata.avid)
    assertEquals(456L, metadata.cid)
}
```

- [ ] **Step 3: Run parser tests and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*BiliCacheMetadataParserTest"
```

Expected: compilation fails because `BiliCacheMetadataParser` and model classes do not exist.

- [ ] **Step 4: Implement the minimal parser and models**

`BiliCacheMetadataParser` must use `JSONObject`, prefer `page_data.part`, then `ep.index_title`, then `ep.index`, and read positive `avid`/`cid` values from the top level or nested object. Missing text becomes `null`; malformed JSON throws `JSONException` for the scanner to classify.

- [ ] **Step 5: Run parser tests and verify GREEN**

Run the same focused test command. Expected: all parser tests pass.

---

### Task 2: Implement the tested default-directory file scanner

**Files:**
- Create: `app/src/main/java/com/example/bili2media/cache/scanner/BiliCacheEntryFactory.kt`
- Create: `app/src/main/java/com/example/bili2media/cache/scanner/BiliCacheMedia.kt`
- Create: `app/src/main/java/com/example/bili2media/cache/scanner/FileBiliCacheScanner.kt`
- Create: `app/src/test/java/com/example/bili2media/cache/scanner/FileBiliCacheScannerTest.kt`

**Interfaces:**
- Produces: `FileBiliCacheScanner.scan(root: File): List<BiliCacheEntry>`.
- Produces: `BiliCacheEntryFactory.create(fallbackName: String, relativePath: String, jsonText: String, mediaFiles: List<BiliCacheMedia>): BiliCacheEntry`.
- Produces: `BiliCacheMedia(name: String, size: Long)`.

- [ ] **Step 1: Write failing scanner tests using temporary directories**

Use JUnit `TemporaryFolder`. Tests must prove:

- a directory with `entry.json`, video `.m4s`, and audio `.m4s` becomes one `AVAILABLE` entry;
- nested media sizes are summed and fragments counted;
- malformed JSON becomes `METADATA_ERROR` and uses the directory name;
- valid metadata with no media becomes `NO_MEDIA`;
- directories without `entry.json` are ignored;
- results sort by title and then relative path.

The main fixture must write known byte arrays so `totalBytes` has an exact assertion.

- [ ] **Step 2: Run scanner tests and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*FileBiliCacheScannerTest"
```

Expected: compilation fails because scanner classes do not exist.

- [ ] **Step 3: Implement media recognition and entry creation**

`BiliCacheMedia` must expose a case-insensitive extension check for the six approved extensions. `BiliCacheEntryFactory` must catch metadata parse failures, use `fallbackName` when title is blank, set the requested status, count media, and sum non-negative sizes.

- [ ] **Step 4: Implement iterative read-only file traversal**

`FileBiliCacheScanner` must:

- return an empty list when the root is absent or not a directory;
- traverse with an explicit stack and a canonical-path visited set;
- treat each `entry.json` parent as one candidate;
- recursively collect recognized media below that candidate;
- derive a stable relative path and stable ID from the canonical candidate path;
- catch inaccessible branches without aborting the entire scan;
- return a deterministic sorted list.

- [ ] **Step 5: Run parser and scanner tests and verify GREEN**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*BiliCacheMetadataParserTest" --tests "*FileBiliCacheScannerTest"
```

Expected: all focused tests pass.

---

### Task 3: Add default-root, persisted SAF selection, and DocumentFile scanning

**Files:**
- Create: `app/src/main/java/com/example/bili2media/storage/CacheRootSelection.kt`
- Create: `app/src/main/java/com/example/bili2media/storage/CacheRootStore.kt`
- Create: `app/src/main/java/com/example/bili2media/storage/DefaultCacheDirectory.kt`
- Create: `app/src/main/java/com/example/bili2media/cache/scanner/DocumentTreeBiliCacheScanner.kt`

**Interfaces:**
- Produces: `CacheRootSelection.Default` and `CacheRootSelection.Tree(uri: Uri)`.
- Produces: `CacheRootStore.current()`, `useDefault()`, `saveTree(uri: Uri)`, and `clearTree()`.
- Produces: `DefaultCacheDirectory.file(): File` for `Download/Bili2Media/Input`.
- Produces: `DocumentTreeBiliCacheScanner.scan(root: DocumentFile): List<BiliCacheEntry>`.

- [ ] **Step 1: Implement the root selection store**

Store only the selected tree URI string in private SharedPreferences. Absence means the default directory. Parsing an invalid saved URI must clear it and return the default selection.

- [ ] **Step 2: Implement the default directory provider**

Build the path from external shared storage plus `Download/Bili2Media/Input`. Directory creation occurs only in UI orchestration after `Environment.isExternalStorageManager()` is true.

- [ ] **Step 3: Implement read-only DocumentFile traversal**

Mirror the tested file scanner semantics using `DocumentFile.listFiles()`, `ContentResolver.openInputStream()`, accumulated relative path segments, `DocumentFile.length()`, the shared entry factory, and a URI visited set. A failed branch or stream must not abort other candidates.

- [ ] **Step 4: Compile the Android source set**

Run:

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: Kotlin compilation succeeds.

---

### Task 4: Build the permission-aware ComicLab-style scan screen

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/bili2media/MainActivity.kt`
- Create: `app/src/main/java/com/example/bili2media/ui/BiliCacheAdapter.kt`
- Replace: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/layout/item_bili_cache.xml`
- Create: `app/src/main/res/drawable/bg_cache_item.xml`
- Create: `app/src/main/res/drawable/bg_status_chip.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: both scanner implementations and `CacheRootStore`.
- Produces: buttons for all-files authorization, directory selection, and refresh; loading, empty, error, and populated states.

- [ ] **Step 1: Declare the special storage permission**

Add `MANAGE_EXTERNAL_STORAGE` with the tools namespace and scoped-storage lint suppression. Do not add legacy `READ_EXTERNAL_STORAGE` or media permissions because the scanner must also read JSON metadata.

- [ ] **Step 2: Create the RecyclerView item adapter**

`BiliCacheAdapter.submitList(entries)` must replace its immutable snapshot and refresh the list. Each row displays title, optional subtitle, formatted size, fragment count, relative path, and localized status text. Metadata errors use the existing danger color; usable and no-media states use neutral colors.

- [ ] **Step 3: Replace the placeholder layout**

Create:

- a top white rounded card with app name, current path, and access status;
- a horizontal action row with `授权访问`, `选择目录`, and `刷新`;
- a centered indeterminate ProgressBar;
- a message TextView for empty/error states;
- a RecyclerView filling the remaining page.

Preserve 12 dp outer margins, 16 dp card padding, an `#F2F3F5` background, and existing typography dimensions.

- [ ] **Step 4: Implement permission and directory selection flow**

`MainActivity` must:

- launch `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` for this package;
- launch `ActivityResultContracts.OpenDocumentTree` for alternate roots;
- call `takePersistableUriPermission` for the returned URI and save it;
- keep SAF scanning available without all-files access;
- switch to and create the default directory only after all-files access is granted;
- clear a saved tree when persisted access is invalid.

- [ ] **Step 5: Implement lifecycle-safe background scanning**

Use one executor, an atomic generation counter, and main-thread result delivery. A new refresh invalidates older results; `onDestroy` increments the generation and shuts down the executor. Render loading before work and exactly one of list, empty, permission, or error state afterward.

- [ ] **Step 6: Compile resources and Kotlin**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: the Android app compiles and packages successfully.

---

### Task 5: Run full automated and static verification

**Files:**
- Verify: all files created or modified in Tasks 1-4

**Interfaces:**
- Produces: a debug APK and documented verification boundary.

- [ ] **Step 1: Run the complete JVM unit suite**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
```

Expected: all parser and file scanner tests pass with zero failures.

- [ ] **Step 2: Build the debug APK**

Run:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL` and a non-empty `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Run static scope checks**

Confirm:

- Manifest contains `MANAGE_EXTERNAL_STORAGE` and no `READ_EXTERNAL_STORAGE`;
- source contains `Bili2Media/Input` and all six media extensions;
- source does not contain `/Android/data/tv.danmaku.bili`;
- source contains no conversion implementation or FFmpeg dependency;
- scanning code performs no delete, rename, move, or output write operation.

- [ ] **Step 4: Report the field-verification boundary**

State that automated tests and compilation are verified locally. Android 16 permission settings, persisted SAF behavior, and scans against a real copied Bilibili cache remain device checks until exercised on the user's phone.
