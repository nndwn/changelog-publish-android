package io.github.nndwn.changelog.publish

import io.github.nndwn.changelog.publish.data.ChangelogManager
import io.github.nndwn.changelog.publish.data.ChangelogParser
import io.github.nndwn.changelog.publish.data.CiPublisher
import io.github.nndwn.changelog.publish.domain.model.AndroidMetadata
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Generates dynamic changelog output for CI/CD")
abstract class ChangelogPublishTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val changelogFile: RegularFileProperty

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    @get:Optional
    abstract val releaseNotes: Property<String>

    @get:Input
    @get:Optional
    abstract val flavorName: Property<String>

    @get:Input
    @get:Optional
    abstract val variantName: Property<String>

    @TaskAction
    fun execute() {
        val ciInfo = CiPublisher.getCiEnvironmentInfo()
        val metadata = AndroidMetadata(
            appName = appName.get(),
            versionName = versionName.get(),
            versionCode = versionCode.get(),
            flavorName = flavorName.orNull?.takeIf { it.isNotBlank() },
            variantName = variantName.orNull?.takeIf { it.isNotBlank() },
        )

        val notes = if (releaseNotes.isPresent && releaseNotes.get().isNotBlank()) {
            releaseNotes.get()
        } else {
            val file = changelogFile.orNull?.asFile
                ?: throw GradleException("Changelog file is not configured or does not exist.")
            ChangelogParser.parseUnreleasedNotes(file, flavorName = flavorName.orNull)
        }

        val payload = ChangelogManager.createPayload(
            metadata = metadata,
            releaseNotes = notes,
            commitHash = ciInfo["COMMIT_HASH"] ?: "",
            branchName = ciInfo["BRANCH"] ?: "",
            environment = ciInfo["CI_PLATFORM"] ?: "Local"
        )

        val jsonOutput = ChangelogManager.toJson(payload)
        logger.lifecycle("========================================")
        logger.lifecycle("CHANGELOG PAYLOAD FOR CI/CD GENERATED:")
        logger.lifecycle(jsonOutput)
        logger.lifecycle("========================================")
    }
}
