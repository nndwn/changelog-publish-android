# Changelog Publish Android Plugin

[![Build](https://github.com/nndwn/changelog-publish-android/actions/workflows/build.yml/badge.svg)](https://github.com/nndwn/changelog-publish-android/actions/workflows/build.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.nndwn.changelog-publish.svg)](https://plugins.gradle.org/plugin/io.github.nndwn.changelog-publish)

A Gradle Plugin designed to extract Android project metadata, integrate CI/CD environment information, automate `CHANGELOG.md` updates, automatically rename release build artifacts (APKs), and generate release changelog JSON payloads for automated publishing.

---

## Quick Start

Get up and running in seconds. The plugin is **zero-configuration** — all settings below are optional.

### 1. Install the Plugin

Add the plugin to your **application module** `build.gradle.kts` (e.g., `:app`):

```kotlin
plugins {
    id("io.github.nndwn.changelog-publish") version "0.4.0"
}
```

### 2. Add a `CHANGELOG.md` at the Project Root

```markdown
# Changelog

## [Unreleased]
- Improved main navigation button responsiveness
- Optimized image loading speed

## [1.0.0] - 2026-03-01
- Initial release
```

### 3. Run a Task

```bash
# Prints the CI/CD JSON payload to the console AND saves it to
# build/reports/changelog/changelog.json for tools like `jq`.
./gradlew generateChangelog
```

That's it! Metadata (`appName`, `versionName`, `versionCode`) is auto-resolved from your `AndroidManifest.xml` and `android.defaultConfig`.

### 4. Automate Your Release (Add Workflows)

Copy the production-ready workflow templates from the [`samples/workflows`](file:///home/nndwn/dev/changelog-publish-android/samples/workflows) directory into your `.github/workflows/` folder and adjust them to your needs:

* **[android-build-and-draft.yml](/samples/workflows/android-build-and-draft.yml)** — CI: runs unit tests, generates the changelog JSON payload, and creates a GitHub Draft Release on every push to `main`.
* **[android-release-and-publish.yml](/samples/workflows/android-release-and-publish.yml)** — Release: signs APKs/AABs, publishes to the Google Play Store (Triple-T GPP), promotes `CHANGELOG.md`, and opens an automated PR.

> [!IMPORTANT]
> **GitHub Actions Permissions**:
> To enable automated Pull Request creation for updated `CHANGELOG.md` files (used in `android-release-and-publish.yml`), make sure to enable the permission in GitHub:
> Go to **Settings** $\rightarrow$ **Actions** $\rightarrow$ **General** $\rightarrow$ **Workflow permissions** and check **"Allow GitHub Actions to create and approve pull requests"**.

> [!TIP]
> New here? Jump straight to [Usage Guide](#usage-guide) for detailed task examples, or read the full [How This Work](#How-This-Work) for advanced behavior.

---

## Requirements (Minimum Specification)

Minimum environment required to consume this plugin in an Android project:

| Component | Minimum | Notes |
| :--- | :--- | :--- |
| **Gradle** | 8.0 | Plugin is developed against the Gradle API and is fully compatible with **Gradle Configuration Cache** (`--configuration-cache`). |
| **Android Gradle Plugin (AGP)** | 7.0 | Artifact renaming uses the `androidComponents.onVariants` Variant API (AGP 7+), with an automatic fallback to the legacy `applicationVariants` API on older AGP. |
| **JDK** | 17 | Required to run AGP 8.x builds. |
| **Kotlin** | 1.9 | Plugin is authored with Kotlin 2.0.21. |
| **Android module** | — | Module must apply `com.android.application` for artifact renaming. |

> [!NOTE]
> Metadata resolution and changelog generation also work on non-Android Gradle modules; only the APK artifact renaming and variant-specific tasks require the Android application plugin.

---

## How This Work

### 1. Android Metadata & Variant Support
The plugin resolves application metadata based on the Android project configuration:
* **`appName`**: Extracted from `AndroidManifest.xml` (and `strings.xml`) matching the target build variant with fallback to `project.name`. Supports flavor overrides (e.g., `src/foss/res/values/strings.xml`).
* **`versionName`**: Base value extracted from `android.defaultConfig.versionName`. When naming build artifacts or generating variant payloads, any variant `versionNameSuffix` (e.g., `-foss`) is appended.
* **`versionCode`**: Extracted from `android.defaultConfig.versionCode`.
* **`flavorName`**: Extracted for variant-specific tasks (e.g., `playstore` or `foss`).
* **`variantName`**: Extracted for variant-specific tasks (e.g., `playstoreRelease`).
* **`releaseNotes`**: Dynamically parsed from the `CHANGELOG.md` file located at the project root or overridden via the `changelogPublish` extension.

---

### 2. `CHANGELOG.md` Parsing Contract & Strict Error Handling

The `CHANGELOG.md` file in the root directory adheres strictly to the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) convention with the following parsing rules:

1. **Flexible File Name Resolution**:
   * Automatically resolves the changelog file in order of preference (`CHANGELOG.md`, `changelog.md`, `Changelog.md`, `ChangeLog.md`).
2. **Target Section (`## [Unreleased]`)**:
   * The plugin extracts release notes exclusively under the `## [Unreleased]` section.
3. **First Match Rule**:
   * If multiple `## [Unreleased]` sections exist, the plugin **only processes the first `## [Unreleased]` section** found from top to bottom.
4. **Section Termination**:
   * Parsing terminates immediately when encountering the next version header (e.g., `## [x.x.x]` or a second `## [Unreleased]`) or reaching the end of the file.
5. **Flavor & Localization Subsections**:
   * `CHANGELOG.md` supports flavor subsections (e.g., `### [FOSS]` or `### [Playstore]`) and BCP 47 locale/language subsections (e.g., `#### [en-US]`, `#### [id-ID]`).
   * A `### [X]` heading is treated as a **flavor** only when `X` matches (case-insensitive) a real product flavor from `com.android.application` or an explicit `changelogPublish.playFlavors` entry. Otherwise it is treated as a **category heading** (e.g., `### Added`, `### [Removed]`).
   * Category headings are preserved: the heading text becomes a line and its bullets are indented.
   * **Scope follows position**: content before the first `### [Flavor]` section is global (`src/main`); content under a `### [Flavor]` section belongs to that flavor (`src/<flavor>`), including any category headings placed there. A category heading can therefore be global or flavor-specific depending on where it is placed.
6. **JSON Escaping**:
   * Special characters such as double quotes (`"`), newlines (`\n`), and backslashes (`\`) are safely escaped in the generated JSON payload.
7. **Version Normalization**:
   * When checking for duplicate versions, an optional leading `v` is ignored (e.g., `## [v1.0.0]` is detected as version `1.0.0`).
8. **Validation Policies (Lenient payload tasks vs. strict release tasks)**:
   * **Payload tasks are lenient** (`generateChangelog` and the per-variant `generateChangelog<VariantName>` tasks): they only *report* the changelog, so a missing file, a missing `## [Unreleased]` section, or an empty section is **not** a failure. The JSON payload is still emitted - with an empty `"releaseNotes"` field (a warning is logged) - so CI stays green right after a release emptied `## [Unreleased]`.
   * **Release tasks stay strict** (`releaseChangelog`, `generatePlayReleaseNotes`): they still **throw a `GradleException` (build failure)** with actionable error instructions when there is nothing to release, so a release can never be finalized or published without notes.

> [!IMPORTANT]
> **Google Play Store (GPP) 500-Character Limit Rule**:
> When using release notes with the Triple-T Gradle Play Publisher (`com.github.triplet.play`) or publishing directly to Google Play (`src/main/play/release-notes/<locale>/production.txt`), Google Play limits release notes to **a maximum of 500 characters per locale**. Ensure localized notes under `#### [<locale>]` stay within this limit to prevent publishing failures.

---

### 3. Task Registration Contract

* **`generateChangelog` Task**:
  * Global Gradle task that extracts metadata, parses `CHANGELOG.md`, logs the JSON changelog payload to console, and automatically saves it to `build/reports/changelog/changelog.json` (or `app/build/reports/changelog/changelog.json` in `:app`) for direct consumption by CI/CD tools (e.g., `jq`, GitHub Actions).
  * **An empty `## [Unreleased]` is not a failure**: the task never throws for a missing changelog file, a missing `## [Unreleased]` section, or an empty section. It writes the payload with an empty `"releaseNotes"` field and logs a warning, leaving validation to the release tasks.
* **Per-Variant `generateChangelog<VariantName>` Tasks**:
  * Automatically registered for Android application modules with product flavors (e.g., `generateChangelogPlaystoreRelease`, `generateChangelogFossRelease`, `generateChangelogPlaystore`, `generateChangelogFoss`).
  * Automatically filters `CHANGELOG.md` for flavor-specific release notes (`### [Flavor]`), appends flavor `versionNameSuffix` (if defined), includes `"flavorName"` and `"variantName"` fields in the `metadata` JSON payload, and writes the JSON payload file. Like the global task, these are **lenient**: an empty `## [Unreleased]` produces an empty `"releaseNotes"` field instead of a failure.
* **`releaseChangelog` Task**:
  * Single root project task executed during release finalization.
  * **Strict Release Validation**:
    * **Fails if `## [Unreleased]` is empty**: Throws `GradleException` if no notes exist under `## [Unreleased]`.
    * **Fails on Duplicate Version**: Throws `GradleException` if current `versionName` already exists in `CHANGELOG.md`.
  * Promotes `## [Unreleased]` to `## [<versionName>] - YYYY-MM-DD` (using system local date in `YYYY-MM-DD` format).
  * Prepends a fresh, empty `## [Unreleased]` section at the top for future development.
* **`generatePlayReleaseNotes` Task**:
  * Extracts localized release notes from `CHANGELOG.md` (`## [Unreleased]`) and writes text files formatted for [Triple-T Gradle Play Publisher (GPP)](https://github.com/Triple-T/gradle-play-publisher#release-notes).
  * **Per-variant output**: notes are written to `src/<variantName>/play/release-notes/<locale>/<track>.txt` for every **release** variant (e.g. `src/playstoreRelease/play/release-notes/en-US/default.txt`). That is GPP's highest priority source set, so the changelog **overrides** the notes GPP's `bootstrap` task downloads into `src/<flavor>/play/release-notes/`.
  * **Multi-track output**: one file is written per entry of `changelogPublish.playTracks` (default `["default", "internal", "production"]`), so the notes are found whichever track is published. `default.txt` is GPP's universal fallback, `<track>.txt` wins for that specific track. Stale track files are removed automatically.
  * **Flavor-level Triple-T output (F-Droid)**: when the project already ships Triple-T metadata at `src/<flavor>/play/release-notes/`, the plugin also refreshes its `default.txt` changelog. F-Droid resolves store metadata from `<module>/src/<buildFlavor>/play/` inside a clean git clone and never looks at the variant source sets, which are build outputs. This pass is **non-destructive**: `default.txt` is the only file written, and no file or locale directory is ever deleted.
  * **Flavor resolution**: `### [X]` is a flavor only if it matches a real product flavor (or `changelogPublish.playFlavors`); otherwise it is a category heading. Each variant file contains the global notes plus that variant's flavor notes.
  * **Strict 500-Character Validation**: Automatically verifies that each locale's release notes do not exceed [Google Play Console's 500-character limit](https://support.google.com/googleplay/android-developer/answer/9866151). Throws a `GradleException` if any locale exceeds 500 characters.

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

## Usage Guide

### 1. Apply the Plugin

Add the plugin to your application module's `build.gradle.kts` (e.g., `:app`):

```kotlin
plugins {
    id("io.github.nndwn.changelog-publish") version "0.4.0"
}
```

### 2. Prepare `CHANGELOG.md` (at Root Project)

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
- Improved main navigation button responsiveness

### Added
- Optimized image loading speed

### [FOSS]
- Added local data export to JSON
- Removed Google Play Services dependencies

### Fixed
- FOSS-only crash fix

### [Playstore]
#### [en-US]
- Integrated Google Billing v7
- Added Firebase push notifications support

#### [id-ID]
- Integrasi Google Billing v7
- Dukungan notifikasi push Firebase

## [1.0.0] - 2026-03-01
- Initial release
```

---

### 3. Executing Tasks

#### A. Generate Changelog JSON Payload (CI/CD)

The changelog generation tasks log the payload to the console and automatically save the `.json` report file to `build/reports/changelog/changelog.json` (e.g. `app/build/reports/changelog/changelog.json`), making it instantly available for `jq` or GitHub Actions steps.

**Global Task:**
```bash
./gradlew generateChangelog
```

**Per-Variant / Per-Flavor Tasks:**
For Android projects with product flavors (e.g., `playstore` and `foss`), run the variant-specific task to extract flavor-filtered release notes and flavor metadata:
```bash
./gradlew generateChangelogPlaystoreRelease
```
or
```bash
./gradlew generateChangelogFossRelease
```

**Sample Output JSON Payload:**
```json
{
  "metadata": {
    "appName": "My App",
    "versionName": "1.2.0-foss",
    "versionCode": 120,
    "flavorName": "foss",
    "variantName": "fossRelease"
  },
  "releaseNotes": "- Improved main navigation button responsiveness\n- Optimized image loading speed\n- Added local data export to JSON\n- Removed Google Play Services dependencies",
  "commitHash": "a1b2c3d4",
  "branchName": "main",
  "buildEnvironment": "GitHub Actions"
}
```

#### B. Finalize Release Version (`CHANGELOG.md` Update)
Execute this task when finalizing a new release version. It promotes `## [Unreleased]` to `## [1.2.0] - YYYY-MM-DD` and prepares a new `## [Unreleased]` section:

```bash
./gradlew releaseChangelog
```

#### C. Generate Google Play Store Release Notes (GPP)
Extracts localized release notes under `## [Unreleased]`, validates length (<= 500 characters), and writes GPP-compatible files into `src/<variantName>/play/release-notes/<locale>/` for every release variant - one file per configured track. Nothing is written to `src/main` or `src/<flavor>`, which are owned by GPP's `bootstrap` task.

```kotlin
//Optional
changelogPublish {
    playTracks.set(listOf("default", "internal", "production"))  // optional: one <locale>/<track>.txt per entry
    playVariants.set(mapOf("playstoreRelease" to "playstore"))    // optional: override AGP-resolved release variants
    playFlavors.set(listOf("foss", "playstore"))                  // optional: explicit flavor list (else read from android.productFlavors)
    // playSourceSetsRoot.set(layout.projectDirectory.dir("src")) // optional: override source-set root
}
```

```bash
./gradlew generatePlayReleaseNotes
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
| Plugin JAR | `io.github.nndwn:changelog-publish-android:<version>` | The plugin implementation |
| Plugin Marker | `io.github.nndwn.changelog-publish:io.github.nndwn.changelog-publish.gradle.plugin:<version>` | Resolves the `id("io.github.nndwn.changelog-publish")` request |

### Consuming the Published Plugin
```kotlin
plugins {
    id("io.github.nndwn.changelog-publish") version "0.4.0"
}
```

---

## Sample GitHub Actions Workflows

Ready-to-use production GitHub Actions workflow templates integrating this plugin:

* **[android-build-and-draft.yml](file:///home/nndwn/dev/changelog-publish-android/samples/workflows/android-build-and-draft.yml)**: Continuous integration workflow that runs unit tests, generates changelog JSON payloads, cleans up stale draft releases, and creates updated GitHub Draft Releases on pushes to `main`.
* **[android-release-and-publish.yml](file:///home/nndwn/dev/changelog-publish-android/samples/workflows/android-release-and-publish.yml)**: Full release automation workflow triggered when publishing a GitHub release. Decodes Google Play credentials, generates GPP release notes, signs APKs/AABs, publishes to Google Play Store, promotes `CHANGELOG.md` via `releaseChangelog`, and opens an automated PR to update `main`.
