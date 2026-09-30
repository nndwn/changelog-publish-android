package io.github.nndwn.changelog.publish

import io.github.nndwn.changelog.publish.data.ChangelogParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Modifies CHANGELOG.md during release finalization")
abstract class ChangelogReleaseTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val changelogFile: RegularFileProperty

    @get:Input
    abstract val versionName: Property<String>

    @TaskAction
    fun execute() {
        val file = changelogFile.get().asFile
        val currentVersion = versionName.get()

        logger.lifecycle("========================================")
        logger.lifecycle("PROMOTING CHANGELOG FOR RELEASE: $currentVersion")
        ChangelogParser.promoteUnreleased(file, currentVersion)
        logger.lifecycle("SUCCESSFULLY PROMOTED ## [Unreleased] TO ## [$currentVersion] IN CHANGELOG.md")
        logger.lifecycle("========================================")
    }
}
