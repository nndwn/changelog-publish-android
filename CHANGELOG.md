<!-- Keep a Changelog guide -> https://keepachangelog.com -->
# Changelog

## [Unreleased]
### Added
- Added automatic JSON file output writing to `build/reports/changelog/changelog.json` for `generateChangelog` tasks to simplify CI/CD consumption (e.g. via `jq`)
## [0.2.3] - 2026-10-01
### Fixed
- Fixed Gradle JVM compatibility error by explicitly setting Java 17 target bytecode compatibility (`JavaVersion.VERSION_17` & `JvmTarget.JVM_17`) in `build.gradle.kts`

## [0.2.2] - 2026-09-30
### Added
- Added variant-specific changelog tasks (`generateChangelog<VariantName>`) for Android product flavors (e.g., `generateChangelogPlaystoreRelease`, `generateChangelogPlaystore`)
- Added optional `flavorName` and `variantName` fields to `AndroidMetadata` and `ChangelogData` JSON output payloads
- Added `ConfigurationCacheIntegrationTest` to verify task caching and configuration cache compatibility

### Fixed
- Fixed Gradle Configuration Cache error (`Invocation of 'Task.project' at execution time`) across all plugin tasks by converting `changelogFile` to an explicit `@get:InputFile` task property
- Fixed AGP reflection method type mismatch on `androidComponents.onVariants` to ensure seamless compatibility with AGP 7+, 8+, and 9+


## [0.2.1] - 2026-09-28
- Fixed minor issues
- Added `generatePlayReleaseNotes` task to generate Triple-T Gradle Play Publisher (GPP) release notes from `CHANGELOG.md`
- Added multi-locale support (`#### [locale]`) with Google Play 500-character limit validation
- Added configurable release-notes track (`playTrack`) — output follows GPP track naming (`<locale>/<track>.txt`, default `production`; supports `beta`, `alpha`, `internal`)
- Added per-flavor release-notes generation to `src/<flavor>/play/release-notes/...` with automatic flavor resolution from `android.productFlavors` (override via `playFlavors`)
- Distinguish flavor vs category headings: `### [X]` is a flavor only when it matches a known product flavor; otherwise treated as a category heading (rendered as heading line + indented bullets)
- Added `playSourceSetsRoot` configuration and stale locale directory cleanup

## [0.2.0] - 2026-09-27
- initial release
