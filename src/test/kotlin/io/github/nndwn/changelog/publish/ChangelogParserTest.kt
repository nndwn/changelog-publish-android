package io.github.nndwn.changelog.publish

import io.github.nndwn.changelog.publish.data.ChangelogParser
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
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

    @Test
    fun parseLocalizedNotes_fallbackToDefaultLocaleWhenNoLocaleHeaders() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            - Fixed navigation bug
            """.trimIndent()
        )

        val map = ChangelogParser.parseLocalizedNotes(file, defaultLocale = "en-US")
        assertEquals(1, map.size)
        assertEquals("- Fixed navigation bug", map["en-US"])
    }

    @Test
    fun parseLocalizedNotes_parsesMultipleLocalesCorrectly() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            - Common improvement
            
            #### [en-US]
            - English feature
            
            #### [id-ID]
            - Indonesian feature
            """.trimIndent()
        )

        val map = ChangelogParser.parseLocalizedNotes(file, defaultLocale = "en-US")
        assertEquals(2, map.size)
        assertTrue(map["en-US"]!!.contains("- Common improvement"))
        assertTrue(map["en-US"]!!.contains("- English feature"))
        assertTrue(map["id-ID"]!!.contains("- Common improvement"))
        assertTrue(map["id-ID"]!!.contains("- Indonesian feature"))
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
    fun hasVersion_matchesVersionWithOptionalVPrefix() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [v1.2.0] - 2026-03-27
            - Updated feature
            """.trimIndent()
        )

        assertTrue(ChangelogParser.hasVersion(file, "1.2.0"))
        assertTrue(ChangelogParser.hasVersion(file, "v1.2.0"))
        assertFalse(ChangelogParser.hasVersion(file, "1.2.1"))
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

    @Test
    fun findChangelogFile_returnsUppercaseByDefaultWhenNoFileExists() {
        val project = ProjectBuilder.builder().withProjectDir(tempFolder.root).build()
        val file = ChangelogParser.findChangelogFile(project)
        assertEquals("CHANGELOG.md", file.name)
    }

    @Test
    fun findChangelogFile_findsLowercaseChangelogFile() {
        val createdFile = tempFolder.newFile("changelog.md")
        val project = ProjectBuilder.builder().withProjectDir(tempFolder.root).build()

        val file = ChangelogParser.findChangelogFile(project)
        assertEquals("changelog.md", file.name)
        assertEquals(createdFile.canonicalPath, file.canonicalPath)
    }

    @Test
    fun findChangelogFile_findsTitlecaseChangelogFile() {
        val createdFile = tempFolder.newFile("Changelog.md")
        val project = ProjectBuilder.builder().withProjectDir(tempFolder.root).build()

        val file = ChangelogParser.findChangelogFile(project)
        assertEquals("Changelog.md", file.name)
        assertEquals(createdFile.canonicalPath, file.canonicalPath)
    }

    @Test
    fun parsePlayReleaseNotes_sharedNotesWithNoFlavors() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            - Global note
            """.trimIndent()
        )

        val result = ChangelogParser.parsePlayReleaseNotes(file, knownFlavors = emptySet(), defaultLocale = "en-US")
        assertEquals(1, result.shared.size)
        assertEquals("- Global note", result.shared["en-US"])
        assertTrue(result.flavors.isEmpty())
    }

    @Test
    fun parsePlayReleaseNotes_treatsUnmatchedBracketHeadingAsCategory() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            ### [Added]
            - sample1
            - sample2
            """.trimIndent()
        )

        val result = ChangelogParser.parsePlayReleaseNotes(file, knownFlavors = emptySet(), defaultLocale = "en-US")
        assertEquals("Added\n  - sample1\n  - sample2", result.shared["en-US"])
        assertEquals(listOf("Added"), result.unmatchedHeadings)
    }

    @Test
    fun parsePlayReleaseNotes_separatesKnownFlavorsIntoFlavorNotes() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            - sample
            ### [Playstore]
            - khusus untuk playstore
            ### [FOSS]
            - khusus untuk foss
            """.trimIndent()
        )

        val result = ChangelogParser.parsePlayReleaseNotes(file, knownFlavors = setOf("playstore", "foss"), defaultLocale = "en-US")

        assertEquals("- sample", result.shared["en-US"])
        assertEquals("- sample\n- khusus untuk playstore", result.flavors["playstore"]?.get("en-US"))
        assertEquals("- sample\n- khusus untuk foss", result.flavors["foss"]?.get("en-US"))
    }

    @Test
    fun parsePlayReleaseNotes_onlyVariantNotes() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            ### [Playstore]
            - khusus untuk playstore
            """.trimIndent()
        )

        val result = ChangelogParser.parsePlayReleaseNotes(file, knownFlavors = setOf("playstore"), defaultLocale = "en-US")

        assertTrue(result.shared.isEmpty())
        assertEquals("- khusus untuk playstore", result.flavors["playstore"]?.get("en-US"))
    }

    @Test
    fun parsePlayReleaseNotes_flavorWithLocales() {
        val file = tempFolder.newFile("CHANGELOG.md")
        file.writeText(
            """
            # Changelog
            
            ## [Unreleased]
            - Common
            ### [Playstore]
            #### [en-US]
            - English
            #### [id-ID]
            - Indonesian
            """.trimIndent()
        )

        val result = ChangelogParser.parsePlayReleaseNotes(file, knownFlavors = setOf("playstore"), defaultLocale = "en-US")

        assertEquals("- Common", result.shared["en-US"])
        assertEquals("- Common\n- English", result.flavors["playstore"]?.get("en-US"))
        assertEquals("- Common\n- Indonesian", result.flavors["playstore"]?.get("id-ID"))
    }
}
