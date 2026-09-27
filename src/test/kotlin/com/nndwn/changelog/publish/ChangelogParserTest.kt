package com.nndwn.changelog.publish

import com.nndwn.changelog.publish.data.ChangelogParser
import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChangelogParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parseUnreleasedNotes_extractsNotesCorrectly() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            - Fixed navigation bug
            - Improved performance
            
            ## [1.0.0] - 2026-03-01
            - Initial release
            """.trimIndent()
        )

        val notes = ChangelogParser.parseUnreleasedNotes(file)
        assertEquals("- Fixed navigation bug\n- Improved performance", notes)
    }

    @Test
    fun parseUnreleasedNotes_usesFirstMatchWhenDuplicateUnreleasedExist() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [1.0.0] - 2026-03-01
            - Initial release
            
            ## [Unreleased]
            - First unreleased note
            
            ## [Unreleased]
            - Duplicate unreleased note
            """.trimIndent()
        )

        val notes = ChangelogParser.parseUnreleasedNotes(file)
        assertEquals("- First unreleased note", notes)
    }

    @Test
    fun parseUnreleasedNotes_filtersByFlavorCorrectly() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            - General bug fix
            
            ### [FOSS]
            - Added JSON exporter
            
            ### [Playstore]
            - Integrated Billing API
            """.trimIndent()
        )

        val fossNotes = ChangelogParser.parseUnreleasedNotes(file, "foss")
        assertTrue(fossNotes.contains("- General bug fix"))
        assertTrue(fossNotes.contains("- Added JSON exporter"))
        assertFalse(fossNotes.contains("Integrated Billing API"))

        val playstoreNotes = ChangelogParser.parseUnreleasedNotes(file, "playstore")
        assertTrue(playstoreNotes.contains("- General bug fix"))
        assertTrue(playstoreNotes.contains("- Integrated Billing API"))
        assertFalse(playstoreNotes.contains("Added JSON exporter"))
    }

    @Test(expected = GradleException::class)
    fun parseUnreleasedNotes_throwsException_whenFileDoesNotExist() {
        val nonExistentFile = File(tempFolder.root, "MISSING_CHANGELOG.md")
        ChangelogParser.parseUnreleasedNotes(nonExistentFile)
    }

    @Test(expected = GradleException::class)
    fun parseUnreleasedNotes_throwsException_whenUnreleasedMissing() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [1.0.0] - 2026-03-01
            - Initial release
            """.trimIndent()
        )
        ChangelogParser.parseUnreleasedNotes(file)
    }

    @Test(expected = GradleException::class)
    fun parseUnreleasedNotes_throwsException_whenUnreleasedIsEmpty() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            
            ## [1.0.0] - 2026-03-01
            - Initial release
            """.trimIndent()
        )
        ChangelogParser.parseUnreleasedNotes(file)
    }

    @Test
    fun hasVersion_returnsTrue_whenVersionExists() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [1.2.0] - 2026-03-27
            - Updated feature
            """.trimIndent()
        )

        assertTrue(ChangelogParser.hasVersion(file, "1.2.0"))
        assertFalse(ChangelogParser.hasVersion(file, "1.3.0"))
    }

    @Test
    fun promoteUnreleased_updatesChangelogCorrectly() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            - Feature A
            - Feature B
            
            ## [1.0.0] - 2026-03-01
            - Initial release
            """.trimIndent()
        )

        ChangelogParser.promoteUnreleased(file, "1.1.0", "2026-03-27")

        val content = file.readText()
        assertTrue(content.contains("## [Unreleased]"))
        assertTrue(content.contains("## [1.1.0] - 2026-03-27"))
        assertTrue(content.contains("- Feature A"))
    }

    @Test(expected = GradleException::class)
    fun promoteUnreleased_throwsException_whenVersionAlreadyExists() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            - New bug fixes
            
            ## [1.1.0] - 2026-03-01
            - Previous release
            """.trimIndent()
        )

        ChangelogParser.promoteUnreleased(file, "1.1.0", "2026-03-27")
    }
}
