package io.github.nndwn.changelog.publish

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * End-to-end integration tests that run the real `generatePlayReleaseNotes` task through Gradle
 * TestKit. These cover the task wiring, parser, generator, and directory layout together.
 */
class GeneratePlayReleaseNotesTaskIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun generatesSharedAndFlavorReleaseNotes() {
        val projectDir = tempFolder.newFolder("fixture")
        writeFixture(
            projectDir = projectDir,
            changelog = """
                # Changelog

                ## [Unreleased]
                - Global improvement
                ### [Playstore]
                - Play store specific
                ### [FOSS]
                - Foss specific
                ## [1.0.0] - 2026-03-01
                - Initial
            """.trimIndent(),
        )

        val result = runTask(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePlayReleaseNotes")?.outcome)

        val mainFile = projectDir.resolve("src/main/play/release-notes/en-US/production.txt")
        assertTrue(mainFile.exists())
        assertEquals("- Global improvement", mainFile.readText())

        val playstoreFile = projectDir.resolve("src/playstore/play/release-notes/en-US/production.txt")
        assertTrue(playstoreFile.exists())
        assertEquals("- Global improvement\n- Play store specific", playstoreFile.readText())

        val fossFile = projectDir.resolve("src/foss/play/release-notes/en-US/production.txt")
        assertTrue(fossFile.exists())
        assertEquals("- Global improvement\n- Foss specific", fossFile.readText())
    }

    @Test
    fun rendersCategoryHeadingAndWarnsOnUnmatchedFlavor() {
        val projectDir = tempFolder.newFolder("fixture")
        writeFixture(
            projectDir = projectDir,
            changelog = """
                # Changelog

                ## [Unreleased]
                ### [Added]
                - sample1
                - sample2
                ### [Playstore]
                - Play store specific
                ## [1.0.0] - 2026-03-01
                - Initial
            """.trimIndent(),
        )

        val result = runTask(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePlayReleaseNotes")?.outcome)

        val mainFile = projectDir.resolve("src/main/play/release-notes/en-US/production.txt")
        assertTrue(mainFile.exists())
        assertEquals("Added\n  - sample1\n  - sample2", mainFile.readText())

        val playstoreFile = projectDir.resolve("src/playstore/play/release-notes/en-US/production.txt")
        assertTrue(playstoreFile.exists())
        assertEquals("Added\n  - sample1\n  - sample2\n- Play store specific", playstoreFile.readText())

        assertTrue(result.output.contains("does not match any product flavor"))
    }

    private fun writeFixture(projectDir: File, changelog: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.nndwn.changelog-publish")
            }

            configure<io.github.nndwn.changelog.publish.ChangelogPublishExtension> {
                playTrack.set("production")
                playFlavors.set(listOf("playstore", "foss"))
            }
            """.trimIndent()
        )
        projectDir.resolve("CHANGELOG.md").writeText(changelog)
    }

    private fun runTask(projectDir: File) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments("generatePlayReleaseNotes")
        .withPluginClasspath()
        .build()
}
