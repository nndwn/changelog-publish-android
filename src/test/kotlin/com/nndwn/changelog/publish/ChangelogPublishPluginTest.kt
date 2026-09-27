package com.nndwn.changelog.publish

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogPublishPluginTest {

    @Test
    fun applyPlugin_registersTasksAndExtension() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.nndwn.changelog-publish")

        val generateTask = project.tasks.findByName("generateChangelog")
        assertNotNull("generateChangelog task should be registered", generateTask)
        assertTrue("generateTask should be instance of ChangelogPublishTask", generateTask is ChangelogPublishTask)

        val releaseTask = project.tasks.findByName("releaseChangelog")
        assertNotNull("releaseChangelog task should be registered", releaseTask)
        assertTrue("releaseTask should be instance of ChangelogReleaseTask", releaseTask is ChangelogReleaseTask)

        val extension = project.extensions.findByName("changelogPublish")
        assertNotNull("changelogPublish extension should be registered", extension)
        assertTrue("extension should be instance of ChangelogPublishExtension", extension is ChangelogPublishExtension)
    }

    @Test
    fun applyPlugin_autoResolvesMetadataDefaults() {
        val project = ProjectBuilder.builder().withName("TestApp").build()
        project.plugins.apply("com.nndwn.changelog-publish")

        val task = project.tasks.findByName("generateChangelog") as ChangelogPublishTask
        assertEquals("TestApp", task.appName.get())
        assertEquals("1.0.0", task.versionName.get())
        assertEquals(1, task.versionCode.get())

        val releaseTask = project.tasks.findByName("releaseChangelog") as ChangelogReleaseTask
        assertEquals("1.0.0", releaseTask.versionName.get())
    }
}
