# Changelog Publish Android Plugin

A Gradle Plugin designed to extract Android project metadata, integrate CI/CD environment information, automate `CHANGELOG.md` updates, automatically rename release build artifacts (APKs), and generate release changelog JSON payloads for automated publishing.

---

## 📋 Specification & Contract Rules

### 1. Android Metadata & Variant Support
The plugin resolves application metadata based on the Android project configuration:
* **`appName`**: Extracted from `AndroidManifest.xml` (and `strings.xml`) matching the target build variant with fallback to `project.name`. Supports flavor overrides (e.g., `src/foss/res/values/strings.xml`).
* **`versionName`**: Extracted from `android.defaultConfig.versionName`.
* **`versionCode`**: Extracted from `android.defaultConfig.versionCode`.
* **`releaseNotes`**: Dynamically parsed from the `CHANGELOG.md` file located at the project root or overridden via the `changelogPublish` extension.

---

### 2. `CHANGELOG.md` Parsing Contract & Strict Error Handling

The `CHANGELOG.md` file in the root directory adheres to the following parsing rules:

1. **Target Section (`## [Unreleased]`)**:
   * The plugin extracts release notes exclusively under the `## [Unreleased]` section.
2. **First Match Rule**:
   * If multiple `## [Unreleased]` sections exist, the plugin **only processes the first `## [Unreleased]` section** found from top to bottom.
3. **Section Termination**:
   * Parsing terminates immediately when encountering the next version header (e.g., `## [x.x.x]` or a second `## [Unreleased]`) or reaching the end of the file.
4. **Flavor / Variant Support**:
   * `CHANGELOG.md` may contain flavor-specific subsections (e.g., `### [FOSS]` or `### [Playstore]`) under `## [Unreleased]`.
   * The unified `generateChangelog` task captures **all** release notes (global and flavor subsections) into a single JSON payload.
5. **JSON Escaping**:
   * Special characters such as double quotes (`"`), newlines (`\n`), and backslashes (`\`) are safely escaped in the generated JSON payload.
6. **Strict Mode (Fail-Fast Exception)**:
   * **No silent fallbacks**. If `CHANGELOG.md` is missing, or if the `## [Unreleased]` section is missing or empty, the plugin **throws a `GradleException` (build failure)** with actionable error instructions.

---

### 3. Task Registration Contract

* **`generateChangelog` Task**:
  * Unified Gradle task that extracts metadata, parses `CHANGELOG.md`, and outputs the JSON changelog payload for CI/CD pipelines.
* **`releaseChangelog` Task**:
  * Single root project task executed during release finalization.
  * **Strict Release Validation**:
    * **Fails if `## [Unreleased]` is empty**: Throws `GradleException` if no notes exist under `## [Unreleased]`.
    * **Fails on Duplicate Version**: Throws `GradleException` if current `versionName` already exists in `CHANGELOG.md`.
  * Promotes `## [Unreleased]` to `## [<versionName>] - YYYY-MM-DD` (using system local date in `YYYY-MM-DD` format).
  * Prepends a fresh, empty `## [Unreleased]` section at the top for future development.

---

### 4. Automatic Build Artifact (APK) Output Renaming

When applied to an Android application project (`com.android.application`), the plugin automatically hooks into the Android Gradle Plugin build pipeline to rename output APK files to a clean, standardized format:

* **Format Pattern**: `{AppName}_v{VersionName}({VersionCode})_{Flavor}_{BuildType}.apk`
* **Examples**:
  * Without flavors: `My_App_v1.2.0(120)_release.apk`
  * With `foss` flavor: `My_App_FOSS_v1.2.0(120)_foss_release.apk`

---

## 🚀 Usage Guide

### 1. Apply the Plugin

Add the plugin to your application module's `build.gradle.kts` (e.g., `:app`):

```kotlin
plugins {
    id("com.nndwn.changelog-publish") version "0.1.0"
}
```

### 2. Prepare `CHANGELOG.md` (at Root Project)

```markdown
# Changelog

## [Unreleased]
- Improved main navigation button responsiveness
- Optimized image loading speed

### [FOSS]
- Added local data export to JSON
- Removed Google Play Services dependencies

### [Playstore]
- Integrated Google Billing v7
- Added Firebase push notifications support

## [1.0.0] - 2026-03-01
- Initial release
```

---

### 3. Executing Tasks

#### A. Generate Changelog JSON Payload (CI/CD)
```bash
./gradlew generateChangelog
```

#### B. Finalize Release Version (`CHANGELOG.md` Update)
Execute this task when finalizing a new release version. It promotes `## [Unreleased]` to `## [1.2.0] - YYYY-MM-DD` and prepares a new `## [Unreleased]` section:

```bash
./gradlew releaseChangelog
```

#### Sample `CHANGELOG.md` Output After Running `releaseChangelog`:
```markdown
# Changelog

## [Unreleased]

## [1.2.0] - 2026-03-27
- Improved main navigation button responsiveness
- Optimized image loading speed

### [FOSS]
- Added local data export to JSON

### [Playstore]
- Integrated Google Billing v7
```
