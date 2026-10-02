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
            playTracks = listOf("default"),
            maxCharacterLimit = 500
        )

        assertEquals(2, files.size)

        val enFile = File(targetDir, "en-US/default.txt")
        assertTrue(enFile.exists())
        assertEquals("- English notes", enFile.readText())

        val idFile = File(targetDir, "id-ID/default.txt")
        assertTrue(idFile.exists())
        assertEquals("- Catatan Indonesia", idFile.readText())
    }

    @Test
    fun generate_writesOneFilePerConfiguredTrack() {
        val targetDir = tempFolder.newFolder("play", "release-notes")
        val localizedNotes = mapOf("en-US" to "- Notes")
        val tracks = listOf("default", "internal", "production")

        val files = PlayReleaseNotesGenerator.generate(
            localizedNotes = localizedNotes,
            targetDir = targetDir,
            playTracks = tracks,
            maxCharacterLimit = 500
        )

        assertEquals(3, files.size)
        for (track in tracks) {
            val trackFile = File(targetDir, "en-US/$track.txt")
            assertTrue(trackFile.exists())
            assertEquals("- Notes", trackFile.readText())
        }
    }

    @Test
    fun generate_removesStaleLocaleDirectories() {
        val targetDir = tempFolder.newFolder("play", "release-notes")
        val staleDir = File(targetDir, "fr-FR")
        staleDir.mkdirs()
        File(staleDir, "default.txt").writeText("stale")

        PlayReleaseNotesGenerator.generate(
            localizedNotes = mapOf("en-US" to "- English"),
            targetDir = targetDir,
            playTracks = listOf("default"),
            maxCharacterLimit = 500
        )

        assertFalse(staleDir.exists())
        assertTrue(File(targetDir, "en-US/default.txt").exists())
    }

    @Test
    fun generate_removesStaleTrackFiles() {
        val targetDir = tempFolder.newFolder("play", "release-notes")
        val localeDir = File(targetDir, "en-US")
        localeDir.mkdirs()
        File(localeDir, "beta.txt").writeText("stale track")

        PlayReleaseNotesGenerator.generate(
            localizedNotes = mapOf("en-US" to "- English"),
            targetDir = targetDir,
            playTracks = listOf("default", "production"),
            maxCharacterLimit = 500
        )

        assertFalse(File(localeDir, "beta.txt").exists())
        assertTrue(File(localeDir, "default.txt").exists())
        assertTrue(File(localeDir, "production.txt").exists())
    }

    @Test
    fun generate_writesNothingWhenNoTracksConfigured() {
        val targetDir = tempFolder.newFolder("play", "release-notes")

        val files = PlayReleaseNotesGenerator.generate(
            localizedNotes = mapOf("en-US" to "- English"),
            targetDir = targetDir,
            playTracks = emptyList(),
            maxCharacterLimit = 500
        )

        assertTrue(files.isEmpty())
        assertFalse(File(targetDir, "en-US").exists())
    }

    @Test(expected = GradleException::class)
    fun generate_throwsExceptionWhenExceedingCharacterLimit() {
        val targetDir = tempFolder.newFolder("play", "release-notes")
        val longNotes = "a".repeat(501)
        val localizedNotes = mapOf("en-US" to longNotes)

        PlayReleaseNotesGenerator.generate(
            localizedNotes = localizedNotes,
            targetDir = targetDir,
            playTracks = listOf("default"),
            maxCharacterLimit = 500
        )
    }
}
