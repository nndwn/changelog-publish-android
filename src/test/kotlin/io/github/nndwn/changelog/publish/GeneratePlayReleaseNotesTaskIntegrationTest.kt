package io.github.nndwn.changelog.publish

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * End-to-end integration tests that run the real `generatePlayReleaseNotes` task through Gradle
 * TestKit. These cover the task wiring, parser, generator, and directory layout together.
 *
 * The fixture project has no Android plugin, so `playVariants` is configured explicitly.
 */
class GeneratePlayReleaseNotesTaskIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun generatesPerVariantReleaseNotesForEveryConfiguredTrack() {
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

        // Notes are written to the highest priority GPP source set: src/<variantName>/play/...
        for (track in listOf("default.txt", "internal.txt", "production.txt")) {
            val playstoreFile = projectDir.resolve("src/playstoreRelease/play/release-notes/en-US/$track")
            assertTrue("Missing $playstoreFile", playstoreFile.exists())
            assertEquals("- Global improvement\n- Play store specific", playstoreFile.readText())

            val fossFile = projectDir.resolve("src/fossRelease/play/release-notes/en-US/$track")
            assertTrue("Missing $fossFile", fossFile.exists())
            assertEquals("- Global improvement\n- Foss specific", fossFile.readText())
        }

        // Only `default.txt` is refreshed at flavor level, and only for flavors that already ship
        // Triple-T metadata, so no GPP track file may appear there.
        assertFalse(projectDir.resolve("src/main/play/release-notes/en-US/production.txt").exists())
        assertFalse(projectDir.resolve("src/playstore/play/release-notes/en-US/production.txt").exists())
        assertFalse(projectDir.resolve("src/foss/play/release-notes/en-US/production.txt").exists())
    }

    @Test
    fun refreshesTripleTDefaultChangelogOfFlavorSourceSets() {
        val projectDir = tempFolder.newFolder("fixture")
        writeFixture(
            projectDir = projectDir,
            changelog = """
                # Changelog

                ## [Unreleased]
                - Global improvement
                ### [FOSS]
                - Foss specific
                ## [1.0.0] - 2026-03-01
                - Initial
            """.trimIndent(),
        )

        // The project already ships Triple-T metadata at flavor level, which is exactly what F-Droid
        // reads from `<module>/src/<buildFlavor>/play/`.
        val flavorNotesDir = projectDir.resolve("src/foss/play/release-notes/en-US").apply { mkdirs() }
        flavorNotesDir.resolve("production.txt").writeText("- previous release")
        flavorNotesDir.resolve("hand-written.txt").writeText("- hand written")

        val result = runTask(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePlayReleaseNotes")?.outcome)

        val defaultNotes = flavorNotesDir.resolve("default.txt")
        assertTrue("Missing $defaultNotes", defaultNotes.exists())
        assertEquals("- Global improvement\n- Foss specific", defaultNotes.readText())

        // Non-destructive: hand-maintained files and unrelated tracks must survive untouched.
        assertEquals("- previous release", flavorNotesDir.resolve("production.txt").readText())
        assertEquals("- hand written", flavorNotesDir.resolve("hand-written.txt").readText())
    }

    @Test
    fun doesNotCreateTripleTMetadataForFlavorsWithoutIt() {
        val projectDir = tempFolder.newFolder("fixture")
        writeFixture(
            projectDir = projectDir,
            changelog = """
                # Changelog

                ## [Unreleased]
                - Global improvement
                ## [1.0.0] - 2026-03-01
                - Initial
            """.trimIndent(),
        )

        val result = runTask(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePlayReleaseNotes")?.outcome)

        assertFalse(projectDir.resolve("src/foss/play").exists())
        assertFalse(projectDir.resolve("src/playstore/play").exists())
        assertFalse(projectDir.resolve("src/main/play").exists())
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

        val playstoreFile = projectDir.resolve("src/playstoreRelease/play/release-notes/en-US/default.txt")
        assertTrue(playstoreFile.exists())
        assertEquals("Added\n  - sample1\n  - sample2\n- Play store specific", playstoreFile.readText())

        val fossFile = projectDir.resolve("src/fossRelease/play/release-notes/en-US/default.txt")
        assertTrue(fossFile.exists())
        assertEquals("Added\n  - sample1\n  - sample2", fossFile.readText())

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
                playFlavors.set(listOf("playstore", "foss"))
                playVariants.set(
                    mapOf(
                        "playstoreRelease" to "playstore",
                        "fossRelease" to "foss",
                    )
                )
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
