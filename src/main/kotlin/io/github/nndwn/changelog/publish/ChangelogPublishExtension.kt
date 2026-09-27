package io.github.nndwn.changelog.publish

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface ChangelogPublishExtension {
    val appName: Property<String>
    val versionName: Property<String>
    val versionCode: Property<Int>
    val releaseNotes: Property<String>
    val defaultLocale: Property<String>
    val playTrack: Property<String>
    val playFlavors: ListProperty<String>
    val maxPlayNotesLength: Property<Int>
    val playSourceSetsRoot: DirectoryProperty
}
