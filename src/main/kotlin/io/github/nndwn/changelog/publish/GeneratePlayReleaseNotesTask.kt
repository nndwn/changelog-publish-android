package io.github.nndwn.changelog.publish

import io.github.nndwn.changelog.publish.data.ChangelogParser
import io.github.nndwn.changelog.publish.data.PlayReleaseNotesGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Generates play store release notes text files from changelog")
abstract class GeneratePlayReleaseNotesTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val changelogFile: RegularFileProperty

    @get:Input
    abstract val defaultLocale: Property<String>

    @get:Input
    abstract val playTracks: ListProperty<String>

    @get:Input
    abstract val playVariants: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val playFlavors: ListProperty<String>

    @get:Input
    abstract val maxPlayNotesLength: Property<Int>

    @get:Internal
    abstract val playSourceSetsRoot: DirectoryProperty

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val file = changelogFile.get().asFile
        val defaultLoc = defaultLocale.getOrElse("en-US")
        val tracks = playTracks.getOrElse(emptyList())
        val variants = playVariants.getOrElse(emptyMap())
        val maxLength = maxPlayNotesLength.getOrElse(500)
        val knownFlavors = playFlavors.getOrElse(emptyList()).toSet()
        val sourceSetsRoot = playSourceSetsRoot.get().asFile
        val rootDirectory = rootDir.orNull?.asFile

        if (variants.isEmpty()) {
            logger.warn(
                "[changelog-publish] No release variants were resolved; skipping Google Play release notes " +
                    "generation. Configure 'changelogPublish.playVariants' to generate them explicitly."
            )
            return
        }

        if (tracks.isEmpty()) {
            logger.warn("[changelog-publish] 'changelogPublish.playTracks' is empty; nothing to generate.")
            return
        }

        val parsed = ChangelogParser.parsePlayReleaseNotes(
            file = file,
            knownFlavors = knownFlavors,
            defaultLocale = defaultLoc,
        )

        parsed.unmatchedHeadings.forEach { heading ->
            logger.warn("[changelog-publish] Section '### [$heading]' does not match any product flavor; treated as a category heading.")
        }

        val generatedFiles = mutableListOf<File>()

        for ((variant, flavor) in variants) {
            // `parsed.flavors` already contains the shared notes merged with that flavor's notes.
            val notes = parsed.flavors[flavor] ?: parsed.shared
            if (notes.isEmpty()) continue

            generatedFiles += PlayReleaseNotesGenerator.generate(
                localizedNotes = notes,
                targetDir = File(sourceSetsRoot, "$variant/play/release-notes"),
                playTracks = tracks,
                maxCharacterLimit = maxLength,
            )
        }

        logger.lifecycle("========================================")
        logger.lifecycle("GENERATED GOOGLE PLAY RELEASE NOTES:")
        generatedFiles.forEach { generatedFile ->
            val displayPath = if (rootDirectory != null) {
                generatedFile.relativeTo(rootDirectory).path
            } else {
                generatedFile.path
            }
            logger.lifecycle("  - $displayPath (${generatedFile.length()} chars)")
        }
        logger.lifecycle("========================================")
    }
}
