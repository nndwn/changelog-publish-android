package com.nndwn.changelog.publish

import com.nndwn.changelog.publish.data.AndroidMetadataResolver
import org.gradle.api.DomainObjectCollection
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
            if (extension.releaseNotes.isPresent) {
                releaseNotes.convention(extension.releaseNotes)
            }
        }

        target.tasks.register("releaseChangelog", ChangelogReleaseTask::class.java) {
            group = "publishing"
            description = "Promotes ## [Unreleased] section in CHANGELOG.md to a new release version"

            versionName.convention(extension.versionName.orElse(resolvedMetadata.map { it.versionName }))
        }

        configureArtifactRenaming(target, extension)
    }

    private fun configureArtifactRenaming(project: Project, extension: ChangelogPublishExtension) {
        project.plugins.withId("com.android.application") {
            try {
                val androidExt = project.extensions.findByName("android") ?: return@withId
                val getAppVariants = androidExt.javaClass.getMethod("getApplicationVariants")
                val variants = getAppVariants.invoke(androidExt) as? DomainObjectCollection<*> ?: return@withId

                variants.all { variant ->
                    if (variant != null) {
                        try {
                            val buildTypeObj = variant.javaClass.getMethod("getBuildType").invoke(variant)
                            val buildTypeName = buildTypeObj?.javaClass?.getMethod("getName")?.invoke(buildTypeObj) as? String ?: "release"
                            if (!buildTypeName.equals("release", ignoreCase = true)) {
                                return@all true
                            }
                            val rawFlavorName = variant.javaClass.getMethod("getFlavorName").invoke(variant) as? String
                            val flavorName = if (!rawFlavorName.isNullOrBlank()) rawFlavorName else null

                            val outputs = variant.javaClass.getMethod("getOutputs").invoke(variant) as? DomainObjectCollection<*>
                            outputs?.all { output ->
                                if (output != null) {
                                    try {
                                        val resolvedMeta = AndroidMetadataResolver.resolve(project, flavorName)
                                        val rawAppName = if (extension.appName.isPresent && extension.appName.get().isNotBlank()) {
                                            extension.appName.get()
                                        } else {
                                            resolvedMeta.appName
                                        }
                                        val sanitizedAppName = rawAppName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                        val vName = if (extension.versionName.isPresent && extension.versionName.get().isNotBlank()) {
                                            extension.versionName.get()
                                        } else {
                                            resolvedMeta.versionName
                                        }
                                        val vCode = if (extension.versionCode.isPresent) extension.versionCode.get() else resolvedMeta.versionCode
                                        val flavorPart = if (!flavorName.isNullOrBlank()) "_$flavorName" else ""
                                        val newFileName = "${sanitizedAppName}_v${vName}(${vCode})${flavorPart}_${buildTypeName}.apk"

                                        val setOutputFileNameMethod = output.javaClass.getMethod("setOutputFileName", String::class.java)
                                        setOutputFileNameMethod.invoke(output, newFileName)
                                    } catch (_: Exception) {
                                    }
                                }
                                true
                            }
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
            } catch (_: Exception) {
            }
        }
    }
}
