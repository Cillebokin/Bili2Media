# Cache Cover Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display a rounded video cover in each scanned Bilibili cache row using a local image when available and a cached HTTPS image otherwise.

**Architecture:** Keep cover parsing and source-selection policy in pure Kotlin packages, keep filesystem discovery inside scanner adapters, and isolate Coil behind a UI-owned `CoverImageLoader` interface. The existing single Gradle module remains, but dependency direction is enforced so the cover core can later move into `:core-cache` without changing UI contracts.

**Tech Stack:** Kotlin, Android XML Views, RecyclerView, Coil 2.7.0, JUnit 4, `org.json`, Android SDK 36.

## Global Constraints

- Apply high cohesion and low coupling to every new class and dependency.
- `cache/model`, `cache/parser`, and `cache/cover` must not depend on Coil or Android Views.
- Scanners discover cover references but never load or decode images.
- Coil imports are allowed only in `ui/image/CoilCoverImageLoader.kt`.
- Local covers named `cover.jpg`, `cover.jpeg`, `cover.png`, or `cover.webp` take precedence.
- Remote URLs come from `entry.json`, must normalize to HTTPS, and must reject non-HTTP(S) schemes.
- Missing and failed covers display a local placeholder.
- No changes to cache conversion behavior and no source-file mutation.
- Do not run Git commands or create commits.

---

### Task 1: Add pure cover model, metadata parsing, and resolution policy

**Files:**
- Create: `app/src/main/java/com/example/bili2media/cache/model/CoverSource.kt`
- Modify: `app/src/main/java/com/example/bili2media/cache/model/BiliCacheMetadata.kt`
- Modify: `app/src/main/java/com/example/bili2media/cache/model/BiliCacheEntry.kt`
- Create: `app/src/main/java/com/example/bili2media/cache/cover/BiliCacheCoverResolver.kt`
- Modify: `app/src/main/java/com/example/bili2media/cache/parser/BiliCacheMetadataParser.kt`
- Modify: `app/src/test/java/com/example/bili2media/cache/parser/BiliCacheMetadataParserTest.kt`
- Create: `app/src/test/java/com/example/bili2media/cache/cover/BiliCacheCoverResolverTest.kt`

**Interfaces:**
- Produces: `CoverSource.Local(uri: String)` and `CoverSource.Remote(url: String)`.
- Produces: `BiliCacheCoverResolver.isLocalCoverFileName(fileName: String): Boolean`.
- Produces: `BiliCacheCoverResolver.resolve(localCoverUri: String?, remoteCoverUrl: String?): CoverSource?`.
- Extends: `BiliCacheMetadata.coverUrl` and `BiliCacheEntry.coverSource`.

- [ ] **Step 1: Write failing parser and resolver tests**

Add parser tests for top-level `cover`, `page_data.cover`, and `ep.cover`. Add resolver tests proving:

```kotlin
assertEquals(
    CoverSource.Local("content://local/cover"),
    resolver.resolve("content://local/cover", "http://i0.hdslb.com/remote.jpg")
)
assertEquals(
    CoverSource.Remote("https://i0.hdslb.com/remote.jpg"),
    resolver.resolve(null, "http://i0.hdslb.com/remote.jpg")
)
assertNull(resolver.resolve(null, "ftp://example.com/cover.jpg"))
```

Filename tests must accept only the four explicit cover names, ignoring case.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*BiliCacheMetadataParserTest" --tests "*BiliCacheCoverResolverTest"
```

Expected: compilation fails because cover types and resolver do not exist.

- [ ] **Step 3: Implement the pure cover types and policy**

`BiliCacheCoverResolver` must trim values, prefer a non-blank local URI, convert `//host/path` to `https://host/path`, convert `http://` to `https://`, preserve `https://`, and return null for every other scheme.

- [ ] **Step 4: Extend metadata parsing**

Parse cover URL in this order: root `cover`, `page_data.cover`, `ep.cover`. Do not normalize URLs in the parser; normalization belongs exclusively to the cover resolver.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the same command. Expected: parser and resolver tests pass.

---

### Task 2: Integrate local-cover discovery into scanners

**Files:**
- Modify: `app/src/main/java/com/example/bili2media/cache/scanner/BiliCacheEntryFactory.kt`
- Modify: `app/src/main/java/com/example/bili2media/cache/scanner/FileBiliCacheScanner.kt`
- Modify: `app/src/main/java/com/example/bili2media/cache/scanner/DocumentTreeBiliCacheScanner.kt`
- Modify: `app/src/test/java/com/example/bili2media/cache/scanner/FileBiliCacheScannerTest.kt`

**Interfaces:**
- Extends: `BiliCacheEntryFactory.create(..., localCoverUri: String?)`.
- Consumes: `BiliCacheCoverResolver` for filename recognition and final source selection.

- [ ] **Step 1: Write failing File scanner cover tests**

Extend the real temporary-directory tests to prove:

- `cover.webp` creates `CoverSource.Local` with a `file:` URI;
- local cover wins over `entry.json` remote cover;
- remote cover becomes `CoverSource.Remote` when no local cover exists;
- unrelated JPG files are not treated as covers.

- [ ] **Step 2: Run scanner tests and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*FileBiliCacheScannerTest"
```

Expected: cover assertions fail or do not compile because entries have no cover source.

- [ ] **Step 3: Update entry creation policy**

Add the local URI parameter to `BiliCacheEntryFactory`, pass metadata `coverUrl` and local URI to the resolver, and place the resulting `CoverSource?` on the entry. No scanner may duplicate URL normalization logic.

- [ ] **Step 4: Discover local covers in both storage adapters**

Search candidate subtrees for explicit cover filenames only. Use deterministic path ordering when multiple valid covers exist. File scanning creates a `file:` URI; SAF scanning uses the selected `DocumentFile.uri`. Do not open or decode cover contents during scanning.

- [ ] **Step 5: Run all cache core tests and verify GREEN**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*BiliCacheMetadataParserTest" --tests "*BiliCacheCoverResolverTest" --tests "*FileBiliCacheScannerTest"
```

Expected: all cache tests pass.

---

### Task 3: Isolate Coil and render rounded cover thumbnails

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/example/bili2media/ui/image/CoverImageLoader.kt`
- Create: `app/src/main/java/com/example/bili2media/ui/image/CoilCoverImageLoader.kt`
- Modify: `app/src/main/java/com/example/bili2media/ui/BiliCacheAdapter.kt`
- Modify: `app/src/main/java/com/example/bili2media/MainActivity.kt`
- Replace: `app/src/main/res/layout/item_bili_cache.xml`
- Create: `app/src/main/res/drawable/bg_cover_frame.xml`
- Create: `app/src/main/res/drawable/ic_cover_placeholder.xml`
- Modify: `app/src/main/res/values/dimens.xml`

**Interfaces:**
- Produces: `CoverImageLoader.load(target: ImageView, source: CoverSource?)`.
- Implements: `CoilCoverImageLoader`, the only class allowed to import Coil.
- Injects: `BiliCacheAdapter(coverImageLoader: CoverImageLoader)`.

- [ ] **Step 1: Add Coil and network permission**

Add `io.coil-kt:coil:2.7.0` as an implementation dependency and add only `android.permission.INTERNET`. Do not enable cleartext traffic because all remote sources are normalized to HTTPS.

- [ ] **Step 2: Implement the image-loader boundary**

`CoverImageLoader` references Android `ImageView` and the pure `CoverSource`, but no scanner. `CoilCoverImageLoader` maps Local and Remote variants to Coil data and calls `ImageView.load` with placeholder, error, fallback, and crossfade settings. Passing null must cancel/replace any recycled row request and show the placeholder.

- [ ] **Step 3: Inject the loader into the adapter**

Remove any direct image-loading responsibility from the adapter. The adapter owns binding order only and calls the injected interface after binding text. `MainActivity` constructs `BiliCacheAdapter(CoilCoverImageLoader())`.

- [ ] **Step 4: Reshape the cache row**

Use a horizontal root: a fixed 112 x 63 dp, center-crop, outline-clipped ImageView on the left and the existing text/status content on the right. Use a rounded neutral frame and a local play-style placeholder drawable.

- [ ] **Step 5: Compile and package**

Run:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

Expected: resource linking, Kotlin compilation, and APK packaging succeed.

---

### Task 4: Verify behavior and architectural boundaries

**Files:**
- Verify: all files modified in Tasks 1-3

**Interfaces:**
- Produces: tested cover policy, buildable APK, and evidence that Coil remains isolated.

- [ ] **Step 1: Run all unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
```

Expected: all previous cache tests and new cover tests pass.

- [ ] **Step 2: Run Lint and build**

Run:

```powershell
.\gradlew.bat lintDebug assembleDebug --console=plain
```

Expected: zero Lint errors and a non-empty debug APK.

- [ ] **Step 3: Verify dependency direction**

Static checks must prove:

- Coil imports occur only in `ui/image/CoilCoverImageLoader.kt`;
- `cache/model`, `cache/parser`, and `cache/cover` contain no `android.view`, `android.widget`, or Coil imports;
- scanners contain no Coil imports and no network requests;
- Manifest contains `INTERNET` without cleartext enablement;
- no conversion code or source-file writes were added.

- [ ] **Step 4: Report the device boundary**

State that unit, Lint, architecture, and build checks are local evidence. Real Android 16 validation still needs one local cover, one network-only cover, an offline launch, and a failed URL to verify the four visual states.
