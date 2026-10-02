package io.github.nndwn.changelog.publish

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

interface ChangelogPublishExtension {
    val appName: Property<String>
    val versionName: Property<String>
    val versionCode: Property<Int>
    val releaseNotes: Property<String>
    val defaultLocale: Property<String>

    /**
     * Google Play release tracks to emit release notes for.
     *
     * One file is written per entry (`<locale>/<track>.txt`), so the notes are picked up no matter
     * which track is published. `default` is the universal fallback understood by
     * Gradle Play Publisher, while `internal`/`production` override it for those specific tracks.
     *
     * Defaults to `["default", "internal", "production"]`.
     */
    val playTracks: ListProperty<String>

    /**
     * Release variants to generate release notes for, mapped from variant name to product flavor
     * name (`""` when the variant has no flavor), e.g. `mapOf("playstoreRelease" to "playstore")`.
     *
     * Notes are written to `src/<variantName>/play/release-notes/<locale>/`, which is the highest
     * priority Gradle Play Publisher source set and therefore overrides files produced by
     * Gradle Play Publisher's own `bootstrap` task.
     *
     * Defaults to the release variants reported by the Android Gradle Plugin.
     */
    val playVariants: MapProperty<String, String>

    val playFlavors: ListProperty<String>
    val maxPlayNotesLength: Property<Int>
    val playSourceSetsRoot: DirectoryProperty
}
