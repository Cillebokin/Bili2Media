# Bili2Media Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a minimal Bili2Media Android Studio project that opens, builds, installs, and launches to a ComicLab-styled placeholder page.

**Architecture:** Use one Android application module with a single `AppCompatActivity` and XML layout. Reuse ComicLab's Gradle, SDK, Kotlin, repository, Material3, color, spacing, and rounded-card conventions while excluding all ComicLab business code and permissions.

**Tech Stack:** Kotlin 2.0.21, Android Gradle Plugin 8.13.1, Gradle 8.13, Android SDK 36, Java/Kotlin JVM 11, AndroidX AppCompat, Material3, ConstraintLayout.

## Global Constraints

- Project name and app label are `Bili2Media`.
- namespace and applicationId are `com.example.bili2media`.
- `compileSdk = 36`, `targetSdk = 36`, and `minSdk = 33`.
- Use a single `app` module and XML View UI.
- Do not add storage permissions, Bilibili cache parsing, FFmpeg, media conversion, archive libraries, or ComicLab business code.
- Do not run Git commands or create commits without separate user authorization.

---

### Task 1: Create the buildable Android application scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/example/bili2media/MainActivity.kt`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/dimens.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-night/themes.xml`
- Create: `app/src/main/res/drawable/bg_home_card.xml`
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

**Interfaces:**
- Consumes: local Android SDK and JDK available to Gradle.
- Produces: launcher activity `com.example.bili2media.MainActivity` and debug application package `com.example.bili2media`.

- [ ] **Step 1: Create Gradle root configuration**

Use ComicLab's repository ordering and exact version baseline. `settings.gradle.kts` must declare `rootProject.name = "Bili2Media"` and `include(":app")`. The version catalog must contain only `core-ktx`, `appcompat`, `material`, `activity`, `constraintlayout`, JUnit, AndroidX JUnit, and Espresso dependencies.

- [ ] **Step 2: Reuse the known-good Gradle Wrapper**

Copy `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` from the adjacent ComicLab project. Set the wrapper distribution URL to:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-all.zip
```

- [ ] **Step 3: Create the application module**

Configure `app/build.gradle.kts` with the required package and SDK values, Java/Kotlin 11, a non-minified release build, and only the approved AndroidX/Material dependencies.

- [ ] **Step 4: Create the launcher activity and manifest**

The activity implementation must be limited to:

```kotlin
package com.example.bili2media

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
```

The manifest must register this activity as exported with `MAIN` and `LAUNCHER`, use `Theme.Bili2Media`, and declare no permissions.

- [ ] **Step 5: Create the ComicLab-styled placeholder UI**

Use a `ConstraintLayout` with `#F2F3F5` page background and one white rounded card with 12 dp outer margins and 16 dp content padding. Display `Bili2Media` as an 18 sp bold title and `工程已就绪` as 15 sp secondary text. Do not add interactive controls.

- [ ] **Step 6: Perform static identity and scope checks**

Run:

```powershell
rg -n "com\.example\.comiclab|ComicLabApplication|MANAGE_EXTERNAL_STORAGE|sevenzip|ffmpeg" . -g "!docs/**" -g "!.gradle/**" -g "!build/**"
```

Expected: no matches. Then run:

```powershell
rg -n "com\.example\.bili2media|Bili2Media|Theme\.Bili2Media" settings.gradle.kts app
```

Expected: the new project identity appears in Gradle, manifest, Kotlin, strings, and theme resources.

- [ ] **Step 7: Build the debug APK**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: exit code 0 and `BUILD SUCCESSFUL`.

- [ ] **Step 8: Verify the build artifact**

Run:

```powershell
Get-Item .\app\build\outputs\apk\debug\app-debug.apk | Select-Object FullName,Length,LastWriteTime
```

Expected: one non-empty APK at the requested path. If no emulator/device is connected, report launch verification as not performed rather than inferred.
