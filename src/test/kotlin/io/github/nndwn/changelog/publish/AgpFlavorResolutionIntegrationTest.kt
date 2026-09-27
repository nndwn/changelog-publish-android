package io.github.nndwn.changelog.publish

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * End-to-end test that verifies flavor auto-resolution against a real Android (AGP) project.
 * Skips automatically when no Android SDK is available.
 */
class AgpFlavorResolutionIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun autoResolvesFlavorsFromRealAgp() {
        val sdkDir = findAndroidSdk()
        assumeTrue("Android SDK not found; skipping AGP integration test", sdkDir != null)

        val projectDir = tempFolder.newFolder("agp-fixture")
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            rootProject.name = "agp-fixture"
            """.trimIndent()
        )
        projectDir.resolve("local.properties").writeText("sdk.dir=${sdkDir!!.absolutePath}\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "9.4.1"
                id("io.github.nndwn.changelog-publish")
            }

            android {
                namespace = "com.example.fixture"
                compileSdk = 37

                defaultConfig {
                    applicationId = "com.example.fixture"
                    minSdk = 24
                    targetSdk = 37
                    versionCode = 1
                    versionName = "1.0.0"
                }

                flavorDimensions.add("store")
                productFlavors {
                    create("playstore") { dimension = "store" }
                    create("foss") { dimension = "store" }
                }
            }
            """.trimIndent()
        )
        projectDir.resolve("CHANGELOG.md").writeText(
            """
            # Changelog

            ## [Unreleased]
            - Global improvement
            ### [Playstore]
            - Play store specific
            ### [FOSS]
            - Foss specific
            ## [1.0.0] - 2026-03-01
            - Initial
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generatePlayReleaseNotes")
            .withPluginClasspath()
            .build()

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

    private fun findAndroidSdk(): File? {
        val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (!env.isNullOrBlank()) return File(env)
        val home = System.getProperty("user.home")
        return listOf(
            File(home, "Android/Sdk"),
            File(home, "Library/Android/sdk"),
        ).firstOrNull { it.isDirectory }
    }
}
