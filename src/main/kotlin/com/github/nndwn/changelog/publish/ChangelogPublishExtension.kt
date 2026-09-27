package com.github.nndwn.changelog.publish

import org.gradle.api.provider.Property

interface ChangelogPublishExtension {
    val appName: Property<String>
    val versionName: Property<String>
    val versionCode: Property<Int>
    val releaseNotes: Property<String>
}
