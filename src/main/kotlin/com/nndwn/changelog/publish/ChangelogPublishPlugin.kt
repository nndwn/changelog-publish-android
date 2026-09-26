package com.nndwn.changelog.publish

import com.nndwn.changelog.publish.data.AndroidMetadataResolver
import org.gradle.api.Plugin
import org.gradle.api.Project

class ChangelogPublishPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("changelogPublish", ChangelogPublishExtension::class.java)

        val resolvedMetadata = target.provider { AndroidMetadataResolver.resolve(target) }

        target.tasks.register("generateChangelog", ChangelogPublishTask::class.java) {
            group = "publishing"
            description = "Generates and outputs release changelog payload for CI/CD"

            appName.convention(extension.appName.orElse(resolvedMetadata.map { it.appName }))
            versionName.convention(extension.versionName.orElse(resolvedMetadata.map { it.versionName }))
            versionCode.convention(extension.versionCode.orElse(resolvedMetadata.map { it.versionCode }))
            releaseNotes.convention(extension.releaseNotes.orElse("No release notes provided"))
        }
    }
}
