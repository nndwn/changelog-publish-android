package io.github.nndwn.changelog.publish

import io.github.nndwn.changelog.publish.data.PlayReleaseNotesGenerator
import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlayReleaseNotesGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun generate_createsFilesForLocalesCorrectly() {
        val targetDir = tempFolder.newFolder("play", "release-notes")
        val localizedNotes = mapOf(
            "en-US" to "- English notes",
            "id-ID" to "- Catatan Indonesia"
        )

        val files = PlayReleaseNotesGenerator.generate(
            localizedNotes = localizedNotes,
            targetDir = targetDir,
            maxCharacterLimit = 500
        )

        assertEquals(2, files.size)

        val enFile = File(targetDir, "en-US/production.txt")
        assertTrue(enFile.exists())
        assertEquals("- English notes", enFile.readText())

        val idFile = File(targetDir, "id-ID/production.txt")
        assertTrue(idFile.exists())
        assertEquals("- Catatan Indonesia", idFile.readText())
    }

    @Test
    fun generate_usesCustomTrackFileName() {
        val targetDir = tempFolder.newFolder("play", "release-notes")
        val localizedNotes = mapOf("en-US" to "- Beta notes")

        val files = PlayReleaseNotesGenerator.generate(
            localizedNotes = localizedNotes,
            targetDir = targetDir,
            trackFileName = "beta.txt",
            maxCharacterLimit = 500
        )

        assertEquals(1, files.size)
        val betaFile = File(targetDir, "en-US/beta.txt")
        assertTrue(betaFile.exists())
        assertEquals("- Beta notes", betaFile.readText())
    }

    @Test
    fun generate_removesStaleLocaleDirectories() {
        val targetDir = tempFolder.newFolder("play", "release-notes")
        val staleDir = File(targetDir, "fr-FR")
        staleDir.mkdirs()
        File(staleDir, "production.txt").writeText("stale")

        PlayReleaseNotesGenerator.generate(
            localizedNotes = mapOf("en-US" to "- English"),
            targetDir = targetDir,
            maxCharacterLimit = 500
        )

        assertFalse(staleDir.exists())
        assertTrue(File(targetDir, "en-US/production.txt").exists())
    }

    @Test(expected = GradleException::class)
    fun generate_throwsExceptionWhenExceedingCharacterLimit() {
        val targetDir = tempFolder.newFolder("play", "release-notes")
        val longNotes = "a".repeat(501)
        val localizedNotes = mapOf("en-US" to longNotes)

        PlayReleaseNotesGenerator.generate(
            localizedNotes = localizedNotes,
            targetDir = targetDir,
            maxCharacterLimit = 500
        )
    }
}
