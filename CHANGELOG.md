<!-- Keep a Changelog guide -> https://keepachangelog.com -->
# Changelog

## [Unreleased]

## [0.3.1] - 2026-10-02
### Added
- `generatePlayReleaseNotes` now also refreshes the `default.txt` changelog of every **flavor** source set that already ships Triple-T metadata (`src/<flavor>/play/release-notes/<locale>/default.txt`). That is the layout F-Droid reads from `<module>/src/<buildFlavor>/play/`, and it lives in the app's VCS tree instead of a build output directory, so it has to be updated in place. The pass is strictly non-destructive: `default.txt` is the only file written, and no file or locale directory is ever removed, because everything inside that directory is hand-authored and committed. Flavors that do not already have a `play/release-notes/` directory are left untouched.
- Documented the flavor-level Triple-T output in the `generatePlayReleaseNotes` task contract in `README.md`.

### Changed
- `generateChangelog` (and the per-variant `generateChangelog<VariantName>` tasks) no longer validate the changelog. A missing changelog file, a missing `## [Unreleased]` section, or an empty section now yields a payload with an empty `"releaseNotes"` field plus a warning, instead of a `GradleException`. This keeps CI green on a push to `main` that arrives right after `releaseChangelog` emptied `## [Unreleased]`. The release tasks (`releaseChangelog`, `generatePlayReleaseNotes`) keep their strict fail-fast behaviour.

## [0.3.0] - 2026-10-02
### Changed
- **BREAKING**: `generatePlayReleaseNotes` now writes release notes to the highest priority Gradle Play Publisher source set, one directory per release variant: `src/<variantName>/play/release-notes/<locale>/<track>.txt`. Notes are no longer written to `src/main/play/release-notes/` or `src/<flavor>/play/release-notes/`, which belong to GPP's `bootstrap` task, so the changelog always wins over bootstrapped notes.
- **BREAKING**: `changelogPublish.playTrack` (single track) has been replaced by `changelogPublish.playTracks` (list). One file is written per entry so the notes are found whichever track is published. Defaults to `["default", "internal", "production"]`; `default.txt` is GPP's universal fallback, so the changelog is picked up even for tracks that are not explicitly configured.
- Added `changelogPublish.playVariants` (variant name -> flavor name) to override the release variants resolved from AGP.
- Stale track files (e.g. a `beta.txt` left behind by a previous configuration) are now removed when generating.

### Added
- Added a **Quick Start** section to `README.md` for faster onboarding
- Added production-ready GitHub Actions workflow templates under `samples/workflows/` (`android-build-and-draft.yml`, `android-release-and-publish.yml`)
- samples workflow

## [0.2.4] - 2026-10-01
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
