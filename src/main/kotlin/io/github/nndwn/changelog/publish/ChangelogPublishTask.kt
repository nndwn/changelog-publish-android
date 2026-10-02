package io.github.nndwn.changelog.publish

import io.github.nndwn.changelog.publish.data.ChangelogManager
import io.github.nndwn.changelog.publish.data.ChangelogParser
import io.github.nndwn.changelog.publish.data.CiPublisher
import io.github.nndwn.changelog.publish.domain.model.AndroidMetadata
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
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

    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty

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
            // This task only *reports* the changelog, it does not validate it. A missing file, a
            // missing `## [Unreleased]` section or an empty section is a valid state (typical right
            // after `releaseChangelog` emptied it), so the payload is still emitted - with an empty
            // `releaseNotes` field - instead of failing the build. Strictness belongs to the release
            // tasks (`releaseChangelog`, `generatePlayReleaseNotes`).
            val file = changelogFile.orNull?.asFile
            if (file == null) {
                logger.warn("[changelog-publish] No changelog file is configured; emitting an empty 'releaseNotes' field.")
                ""
            } else {
                ChangelogParser.unreleasedNotesOrEmpty(file, flavorName = flavorName.orNull).also { parsed ->
                    if (parsed.isBlank()) {
                        logger.warn(
                            "[changelog-publish] '## [Unreleased]' in ${file.name} has no release notes; " +
                                "emitting an empty 'releaseNotes' field."
                        )
                    }
                }
            }
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

        if (outputFile.isPresent) {
            val outFile = outputFile.get().asFile
            outFile.parentFile?.mkdirs()
            outFile.writeText(jsonOutput)
            logger.lifecycle("Changelog JSON payload written to: ${outFile.path}")
        }
    }
}
