# Changelog Publish Android Plugin

[![Build](https://github.com/nndwn/changelog-publish-android/actions/workflows/build.yml/badge.svg)](https://github.com/nndwn/changelog-publish-android/actions/workflows/build.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.github.nndwn.changelog-publish.svg)](https://plugins.gradle.org/plugin/com.github.nndwn.changelog-publish)

A Gradle Plugin designed to extract Android project metadata, integrate CI/CD environment information, automate `CHANGELOG.md` updates, automatically rename release build artifacts (APKs), and generate release changelog JSON payloads for automated publishing.

---

## Requirements (Minimum Specification)

Minimum environment required to consume this plugin in an Android project:

| Component | Minimum | Notes |
| :--- | :--- | :--- |
| **Gradle** | 8.0 | Plugin is developed against the Gradle API (this project builds with Gradle 9.3). |
| **Android Gradle Plugin (AGP)** | 7.0 | Artifact renaming uses the `androidComponents.onVariants` Variant API (AGP 7+), with an automatic fallback to the legacy `applicationVariants` API on older AGP. |
| **JDK** | 17 | Required to run AGP 8.x builds. |
| **Kotlin** | 1.9 | Plugin is authored with Kotlin 2.0.21. |
| **Android module** | — | Module must apply `com.android.application` for artifact renaming. |

> [!NOTE]
> Metadata resolution and changelog generation also work on non-Android Gradle modules; only the APK artifact renaming requires the Android application plugin.

---

## 📋 Specification & Contract Rules

### 1. Android Metadata & Variant Support
The plugin resolves application metadata based on the Android project configuration:
* **`appName`**: Extracted from `AndroidManifest.xml` (and `strings.xml`) matching the target build variant with fallback to `project.name`. Supports flavor overrides (e.g., `src/foss/res/values/strings.xml`).
* **`versionName`**: Base value extracted from `android.defaultConfig.versionName`. When naming build artifacts, any variant `versionNameSuffix` (e.g., `-foss`) is appended.
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
6. **Version Normalization**:
   * When checking for duplicate versions, an optional leading `v` is ignored (e.g., `## [v1.0.0]` is detected as version `1.0.0`).
7. **Strict Mode (Fail-Fast Exception)**:
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

When applied to an Android application project (`com.android.application`), the plugin hooks into the Android Gradle Plugin build pipeline and renames **release** APK outputs to a clean, standardized format:

* **AGP integration**: Uses the modern `androidComponents.onVariants` Variant API (AGP 7+) and automatically falls back to the legacy `applicationVariants` API on older AGP versions.
* **Format Pattern**: `{AppName}_v{VersionName}({VersionCode})_{Flavor}_{BuildType}[_{OutputVariant}].apk`
* **App Name Sanitization**: Spaces, symbols and non-ASCII characters are normalized. If nothing meaningful remains, the module name is used as a fallback.
* **Version Suffix**: Any variant `versionNameSuffix` (e.g., `-foss`) is appended to the version in the file name.
* **ABI / Density Splits**: When the build produces split APKs, a unique suffix (e.g., `arm64-v8a`, `xhdpi`) is appended to prevent file name collisions.
* **Debug builds**: Are left untouched (their default names are preserved).
* **Examples**:
  * Without flavors: `My_App_v1.2.0(120)_release.apk`
  * With `foss` flavor: `My_App_FOSS_v1.2.0-foss(120)_foss_release.apk`
  * With ABI split: `My_App_v1.2.0(120)_release_arm64-v8a.apk`

---

### 5. CI/CD Environment Detection

The plugin automatically detects common CI providers and extracts commit/branch metadata for the JSON payload:

* **Supported platforms**: GitHub Actions, GitLab CI, Bitrise, or `Local / Unknown`.
* **Commit hash / build number**: Resolved from `GITHUB_SHA`, `GIT_COMMIT`, `BITRISE_GIT_COMMIT`, `GITHUB_RUN_NUMBER`, `BUILD_NUMBER`, etc.
* **Branch detection**: On GitHub Actions tag builds (`GITHUB_REF_TYPE = tag`), the branch is left empty so that a tag name is never mistaken for a branch name.

---

## 🚀 Usage Guide

### 1. Apply the Plugin

Add the plugin to your application module's `build.gradle.kts` (e.g., `:app`):

```kotlin
plugins {
    id("com.github.nndwn.changelog-publish") version "0.1.0"
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

---

## Publishing

This plugin is published as a **Gradle Plugin** through the [Gradle Plugin Portal](https://plugins.gradle.org/) using the `com.gradle.plugin-publish` plugin.

### Local Testing
Publish to your local Maven repository (`~/.m2`) to consume it in another project without a remote round-trip:
```bash
./gradlew publishToMavenLocal
```

### Publishing to the Gradle Plugin Portal
1. Register at [plugins.gradle.org](https://plugins.gradle.org/) and obtain your **API Key** and **Secret**.
2. Bump the `version` in `build.gradle.kts`.
3. Run:
```bash
./gradlew publishPlugins
```
Provide the credentials either via environment variables (`GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET`) or in `~/.gradle/gradle.properties`:
```properties
gradle.publish.key=<API_KEY>
gradle.publish.secret=<SECRET>
```

### Published Artifacts
Gradle automatically publishes two artifacts:

| Artifact | Coordinate | Purpose |
| :--- | :--- | :--- |
| Plugin JAR | `com.github.nndwn:changelog-publish-android:<version>` | The plugin implementation |
| Plugin Marker | `com.github.nndwn.changelog-publish:com.github.nndwn.changelog-publish.gradle.plugin:<version>` | Resolves the `id("com.github.nndwn.changelog-publish")` request |

### Consuming the Published Plugin
```kotlin
plugins {
    id("com.github.nndwn.changelog-publish") version "0.1.0"
}
```
