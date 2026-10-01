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
 * Integration test verifying that all tasks supplied by `changelog-publish` plugin
 * are fully compatible with Gradle's Configuration Cache.
 */
class ConfigurationCacheIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun generateChangelog_isConfigurationCacheCompatible() {
        val projectDir = tempFolder.newFolder("cc-generate")
        writeFixture(projectDir)

        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateChangelog", "--configuration-cache")
            .withPluginClasspath()

        val result1 = runner.build()
        assertEquals(TaskOutcome.SUCCESS, result1.task(":generateChangelog")?.outcome)

        val result2 = runner.build()
        val outcome2 = result2.task(":generateChangelog")?.outcome
        assertTrue(outcome2 == TaskOutcome.SUCCESS || outcome2 == TaskOutcome.UP_TO_DATE)
        assertTrue(
            result2.output.contains("Reusing configuration cache") ||
                result2.output.contains("Configuration cache entry reused")
        )
    }

    @Test
    fun releaseChangelog_isConfigurationCacheCompatible() {
        val projectDir = tempFolder.newFolder("cc-release")
        writeFixture(projectDir)

        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("releaseChangelog", "--configuration-cache")
            .withPluginClasspath()

        val result = runner.build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":releaseChangelog")?.outcome)
    }

    @Test
    fun generatePlayReleaseNotes_isConfigurationCacheCompatible() {
        val projectDir = tempFolder.newFolder("cc-play")
        writeFixture(projectDir)

        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generatePlayReleaseNotes", "--configuration-cache")
            .withPluginClasspath()

        val result = runner.build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePlayReleaseNotes")?.outcome)
    }

    private fun writeFixture(projectDir: File) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"cc-fixture\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.nndwn.changelog-publish")
            }

            changelogPublish {
                appName.set("TestApp")
                versionName.set("1.0.1")
                versionCode.set(2)
            }
            """.trimIndent()
        )
        projectDir.resolve("CHANGELOG.md").writeText(
            """
            # Changelog

            ## [Unreleased]
            - Fixed something
            - Added something else

            ## [1.0.0] - 2026-03-01
            - Initial release
            """.trimIndent()
        )
    }
}
