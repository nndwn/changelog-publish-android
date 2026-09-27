<!-- Keep a Changelog guide -> https://keepachangelog.com -->
# Changelog

## [Unreleased]
- Fixed minor issues
- Added `generatePlayReleaseNotes` task to generate Triple-T Gradle Play Publisher (GPP) release notes from `CHANGELOG.md`
- Added multi-locale support (`#### [locale]`) with Google Play 500-character limit validation
- Added configurable release-notes track (`playTrack`) — output follows GPP track naming (`<locale>/<track>.txt`, default `production`; supports `beta`, `alpha`, `internal`)
- Added per-flavor release-notes generation to `src/<flavor>/play/release-notes/...` with automatic flavor resolution from `android.productFlavors` (override via `playFlavors`)
- Distinguish flavor vs category headings: `### [X]` is a flavor only when it matches a known product flavor; otherwise treated as a category heading (rendered as heading line + indented bullets)
- Added `playSourceSetsRoot` configuration and stale locale directory cleanup

## [0.2.0] - 2026-09-27
- initial release
