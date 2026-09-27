package io.github.nndwn.changelog.publish

import io.github.nndwn.changelog.publish.data.ChangelogParser
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Modifies CHANGELOG.md during release finalization")
abstract class ChangelogReleaseTask : DefaultTask() {

    @get:Input
    abstract val versionName: Property<String>

    @TaskAction
    fun execute() {
        val changelogFile = ChangelogParser.findChangelogFile(project)
        val currentVersion = versionName.get()

        logger.lifecycle("========================================")
        logger.lifecycle("PROMOTING CHANGELOG FOR RELEASE: $currentVersion")
        ChangelogParser.promoteUnreleased(changelogFile, currentVersion)
        logger.lifecycle("SUCCESSFULLY PROMOTED ## [Unreleased] TO ## [$currentVersion] IN CHANGELOG.md")
        logger.lifecycle("========================================")
    }
}
