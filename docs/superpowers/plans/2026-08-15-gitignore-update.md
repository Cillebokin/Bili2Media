# Bili2Media Gitignore Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Bili2Media's incomplete `.gitignore` with a safe Android/Gradle rule set that ignores generated and machine-specific files without hiding source-controlled project assets.

**Architecture:** Keep one categorized root `.gitignore`. Use narrow patterns for Android Studio state, build caches, packaged outputs, signing material, diagnostics, and OS metadata; avoid broad extension rules for properties, XML, JAR, native libraries, or database schemas.

**Tech Stack:** Git ignore syntax, Android Studio, Gradle, Kotlin Android project layout.

## Global Constraints

- Completely ignore `.idea/`, `.gradle/`, and `.kotlin/`.
- Ignore build outputs for the root project and every module.
- Ignore `local.properties`, NDK/CMake temporary directories, APK/AAB outputs, signing files, logs, dumps, and OS metadata.
- Do not ignore Gradle Wrapper files, source code, resources, build scripts, documentation, tests, Room schemas, or Baseline Profiles.
- Do not run Git commands or create commits.

---

### Task 1: Replace and verify the root ignore rules

**Files:**
- Modify: `.gitignore`

**Interfaces:**
- Consumes: the Bili2Media Android project directory layout.
- Produces: categorized ignore rules used by future Git operations.

- [ ] **Step 1: Verify the current file is missing required coverage**

Run a PowerShell content assertion that requires `.idea/`, `.kotlin/`, `**/build/`, `*.aab`, and `*.jks`. Expected result before editing: exit code 1 because the current file lacks at least one required pattern.

- [ ] **Step 2: Replace `.gitignore` with categorized Android rules**

Write these categories and exact protections:

```gitignore
# Android Studio and editors
.idea/
*.iml
.vscode/
.fleet/
/out/

# Gradle and Kotlin caches
.gradle/
.kotlin/
/build/
**/build/

# Local machine configuration
local.properties

# Android and native build intermediates
captures/
.navigation/
.externalNativeBuild/
.cxx/

# Packaged outputs
*.apk
*.aab
*.ap_
/app/release/

# Signing material
*.jks
*.keystore
keystore.properties

# Logs and diagnostics
*.log
*.hprof
hs_err_pid*

# Operating system metadata
.DS_Store
Thumbs.db
Desktop.ini
```

- [ ] **Step 3: Verify required patterns are present**

Run the same PowerShell content assertion. Expected result after editing: exit code 0 and `Required ignore patterns are present.`

- [ ] **Step 4: Verify dangerous broad patterns are absent**

Check that `.gitignore` does not contain `*.properties`, `*.xml`, `*.jar`, `*.so`, `src/`, `docs/`, `schemas/`, or `gradle/wrapper/`. Expected result: no dangerous patterns found.

- [ ] **Step 5: Verify important project files still exist**

Confirm that `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `app/src/main/AndroidManifest.xml`, and `app/src/main/java/com/example/bili2media/MainActivity.kt` remain present.
