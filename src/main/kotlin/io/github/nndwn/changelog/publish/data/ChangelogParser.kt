package io.github.nndwn.changelog.publish.data

import org.gradle.api.GradleException
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Utility for parsing and manipulating CHANGELOG.md files.
 */
object ChangelogParser {

    private val UNRELEASED_HEADER_REGEX = Regex("^##\\s*\\[Unreleased]", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    /**
     * Parses the release notes from the first `## [Unreleased]` section in CHANGELOG.md.
     * Throws [GradleException] if the file does not exist, or if `## [Unreleased]` is missing or empty.
     */
    fun parseUnreleasedNotes(file: File, flavorName: String? = null): String {
        if (!file.exists()) {
            throw GradleException("CHANGELOG.md file not found at ${file.absolutePath}. Please create a CHANGELOG.md file at the root of your project.")
        }

        val lines = file.readLines()
        val unreleasedStartLine = lines.indexOfFirst { UNRELEASED_HEADER_REGEX.containsMatchIn(it.trim()) }

        if (unreleasedStartLine == -1) {
            throw GradleException("Section '## [Unreleased]' was not found in ${file.name}. Please add a '## [Unreleased]' section at the top of your changelog.")
        }

        val unreleasedLines = mutableListOf<String>()
        for (i in (unreleasedStartLine + 1) until lines.size) {
            val line = lines[i]
            val trimmedLine = line.trim()
            // Stop parsing when encountering the next '## ' section header
            if (trimmedLine.startsWith("## ") || trimmedLine.startsWith("##\t")) {
                break
            }
            unreleasedLines.add(line)
        }

        val parsedNotes = filterNotesByFlavor(unreleasedLines, flavorName)

        if (parsedNotes.isBlank()) {
            throw GradleException("Section '## [Unreleased]' in ${file.name} is empty. Please add release notes under '## [Unreleased]'.")
        }

        return parsedNotes
    }

    /**
     * Checks whether the given [versionName] already exists as a header (e.g., `## [1.2.0]`) in CHANGELOG.md.
     */
    fun hasVersion(file: File, versionName: String): Boolean {
        if (!file.exists()) return false
        // Ignore an optional leading 'v' so that '## [v1.0.0]' matches versionName '1.0.0'.
        val normalized = versionName.trim().removePrefix("v").removePrefix("V")
        val regex = Regex("^##\\s*\\[\\s*[vV]?\\s*${Regex.escape(normalized)}\\s*\\].*", RegexOption.IGNORE_CASE)
        return file.useLines { lines ->
            lines.any { regex.matches(it.trim()) }
        }
    }

    /**
     * Promotes the current `## [Unreleased]` section into `## [versionName] - YYYY-MM-DD`
     * and prepends a fresh empty `## [Unreleased]` section above it.
     */
    fun promoteUnreleased(
        file: File,
        versionName: String,
        currentDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    ) {
        if (!file.exists()) {
            throw GradleException("CHANGELOG.md file not found at ${file.absolutePath}.")
        }

        // Verify that unreleased section exists and is not empty before promoting
        parseUnreleasedNotes(file)

        // Verify that this version is not already recorded
        if (hasVersion(file, versionName)) {
            throw GradleException("Version '$versionName' is already present in ${file.name}! Please bump versionName in build.gradle.kts before running releaseChangelog.")
        }

        val content = file.readText()
        val match = UNRELEASED_HEADER_REGEX.find(content)
            ?: throw GradleException("Section '## [Unreleased]' was not found in ${file.name}.")

        val newVersionHeader = "## [Unreleased]\n\n## [$versionName] - $currentDate"
        val updatedContent = content.substring(0, match.range.first) +
                newVersionHeader +
                content.substring(match.range.last + 1)

        file.writeText(updatedContent)
    }

    private fun filterNotesByFlavor(lines: List<String>, flavorName: String?): String {
        if (flavorName.isNullOrBlank()) {
            return lines.joinToString("\n").trim()
        }

        val commonLines = mutableListOf<String>()
        val flavorLines = mutableListOf<String>()

        var currentSection: String? = null // null means common / top-level

        val flavorHeaderRegex = Regex("^###\\s*\\[(.*)\\]", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val trimmed = line.trim()
            val match = flavorHeaderRegex.find(trimmed)
            if (match != null) {
                val sectionName = match.groupValues[1].trim()
                currentSection = sectionName
                continue
            }

            if (currentSection == null || currentSection.equals("Common", ignoreCase = true)) {
                commonLines.add(line)
            } else if (currentSection.equals(flavorName, ignoreCase = true)) {
                flavorLines.add(line)
            }
        }

        val combined = (commonLines + flavorLines).joinToString("\n").trim()
        return combined.ifBlank { lines.joinToString("\n").trim() }
    }
}
