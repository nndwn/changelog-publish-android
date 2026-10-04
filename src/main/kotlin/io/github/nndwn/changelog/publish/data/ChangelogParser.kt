package io.github.nndwn.changelog.publish.data

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Structured result of parsing `CHANGELOG.md` for Play release notes.
 *
 * @property shared locale-keyed notes that apply to all variants (written under `src/main`).
 * @property flavors flavor-name-keyed maps of locale-keyed notes (written under `src/<flavor>`).
 * @property unmatchedHeadings `### [X]` headings that did not match any known flavor and were
 *   therefore treated as category headings.
 */
data class PlayReleaseNotes(
    val shared: Map<String, String>,
    val flavors: Map<String, Map<String, String>>,
    val unmatchedHeadings: List<String> = emptyList(),
)

/**
 * Utility for parsing and manipulating CHANGELOG.md files.
 */
object ChangelogParser {

    private val UNRELEASED_HEADER_REGEX = Regex("^##\\s*\\[Unreleased]", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val FLAVOR_HEADER_REGEX = Regex("^###\\s*\\[(.*)\\]", RegexOption.IGNORE_CASE)
    private val LOCALE_HEADER_REGEX = Regex("^####\\s*\\[(.*)]", RegexOption.IGNORE_CASE)

    /**
     * Resolves the changelog file from the root project directory.
     * Searches for candidate filenames in order of preference (`CHANGELOG.md`, `changelog.md`, `Changelog.md`, `ChangeLog.md`)
     * and returns the first existing file. If none exist, returns the default file location (`CHANGELOG.md`).
     */
    fun findChangelogFile(rootDir: File): File {
        val candidateNames = listOf("CHANGELOG.md", "changelog.md", "Changelog.md", "ChangeLog.md")

        val existingFile = candidateNames
            .map { File(rootDir, it) }
            .firstOrNull { it.exists() }

        return existingFile ?: File(rootDir, "CHANGELOG.md")
    }

    @Deprecated("Use findChangelogFile(rootDir: File) instead to remain configuration cache compatible.",
        ReplaceWith(
            "findChangelogFile(project.rootProject.projectDir)",
            "io.github.nndwn.changelog.publish.data.ChangelogParser.findChangelogFile"
        )
    )
    fun findChangelogFile(project: Project): File {
        return findChangelogFile(project.rootProject.projectDir)
    }

    /**
     * Parses the release notes from the first `## [Unreleased]` section in CHANGELOG.md.
     * Throws [GradleException] if the file does not exist, or if `## [Unreleased]` is missing or empty.
     *
     * This is the *strict* variant, used by the release tasks. For a non-throwing read (payload
     * tasks, where "no pending notes" is a valid state) use [unreleasedNotesOrEmpty].
     */
    fun parseUnreleasedNotes(file: File, flavorName: String? = null): String {
        val unreleasedLines = readUnreleasedLines(file)
        val parsedNotes = filterNotesByFlavor(unreleasedLines, flavorName)

        if (parsedNotes.isBlank()) {
            throw GradleException("Section '## [Unreleased]' in ${file.name} is empty. Please add release notes under '## [Unreleased]'.")
        }

        return parsedNotes
    }

    /**
     * Reads the release notes from the first `## [Unreleased]` section **without ever failing**.
     *
     * Returns an empty string when the changelog file does not exist, when `## [Unreleased]` is
     * missing, or when the section holds no notes for [flavorName]. "No pending notes" is a valid
     * state for the payload tasks (`generateChangelog`), which must keep producing a JSON payload in
     * CI even right after a release has been promoted. Use [parseUnreleasedNotes] instead when an
     * empty section should fail the build (release finalization).
     */
    fun unreleasedNotesOrEmpty(file: File, flavorName: String? = null): String {
        val unreleasedLines = readUnreleasedLinesOrNull(file) ?: return ""
        return filterNotesByFlavor(unreleasedLines, flavorName)
    }

    /**
     * Whether `## [Unreleased]` currently holds any release notes for [flavorName].
     * Never throws: a missing file or a missing section is reported as `false`.
     */
    fun hasUnreleasedNotes(file: File, flavorName: String? = null): Boolean =
        unreleasedNotesOrEmpty(file, flavorName).isNotBlank()

    /**
     * Parses release notes into a map of locale to release notes text.
     * Looks for `#### [<locale>]` headers under `## [Unreleased]`.
     * If no locale headers exist, maps all release notes to [defaultLocale].
     */
    fun parseLocalizedNotes(
        file: File,
        flavorName: String? = null,
        defaultLocale: String = "en-US",
    ): Map<String, String> {
        val fullNotes = parseUnreleasedNotes(file, flavorName)
        val lines = fullNotes.lines()

        val commonLines = mutableListOf<String>()
        val localeMap = mutableMapOf<String, MutableList<String>>()
        var currentLocale: String? = null

        for (line in lines) {
            val trimmed = line.trim()

            if (FLAVOR_HEADER_REGEX.containsMatchIn(trimmed)) {
                continue
            }

            val match = LOCALE_HEADER_REGEX.find(trimmed)
            if (match != null) {
                currentLocale = match.groupValues[1].trim()
                localeMap.getOrPut(currentLocale) { mutableListOf() }
                continue
            }

            if (currentLocale != null) {
                localeMap[currentLocale]?.add(line)
            } else {
                commonLines.add(line)
            }
        }

        if (localeMap.isEmpty()) {
            val notes = fullNotes.trim()
            return if (notes.isNotBlank()) mapOf(defaultLocale to notes) else emptyMap()
        }

        val result = mutableMapOf<String, String>()
        for ((loc, locLines) in localeMap) {
            val combined = (commonLines + locLines).joinToString("\n").trim()
            if (combined.isNotBlank()) {
                result[loc] = combined
            }
        }

        if (!result.containsKey(defaultLocale) && commonLines.joinToString("\n").trim().isNotBlank()) {
            result[defaultLocale] = commonLines.joinToString("\n").trim()
        }

        return result
    }

    /**
     * Parses `## [Unreleased]` into per-source-set Play release notes.
     *
     * A `### [X]` subsection is treated as a **flavor** only when `X` matches (case-insensitive)
     * an entry in [knownFlavors]. Otherwise it is treated as a category heading whose bullets are
     * rendered as indented sub-bullets under the heading text.
     */
    fun parsePlayReleaseNotes(
        file: File,
        knownFlavors: Set<String> = emptySet(),
        defaultLocale: String = "en-US",
    ): PlayReleaseNotes {
        val unreleasedLines = readUnreleasedLines(file)
        if (unreleasedLines.all { it.isBlank() }) {
            throw GradleException("Section '## [Unreleased]' in ${file.name} is empty. Please add release notes under '## [Unreleased]'.")
        }

        val canonicalFlavors = knownFlavors.associateBy { it.lowercase() }

        val commonLines = mutableListOf<String>()
        val flavorLines = linkedMapOf<String, MutableList<String>>()
        val unmatchedHeadings = mutableListOf<String>()

        var currentFlavor: String? = null

        for (raw in unreleasedLines) {
            val trimmed = raw.trim()
            val flavorMatch = FLAVOR_HEADER_REGEX.find(trimmed)
            if (flavorMatch != null) {
                val name = flavorMatch.groupValues[1].trim()
                val canonical = canonicalFlavors[name.lowercase()]
                if (canonical != null) {
                    currentFlavor = canonical
                    flavorLines.getOrPut(canonical) { mutableListOf() }
                    continue
                } else {
                    unmatchedHeadings.add(name)
                }
            }

            if (currentFlavor != null) {
                flavorLines[currentFlavor]?.add(raw)
            } else {
                commonLines.add(raw)
            }
        }

        val shared = renderLocalized(commonLines, defaultLocale)

        val flavors = linkedMapOf<String, Map<String, String>>()
        for ((flavor, lines) in flavorLines) {
            val flavorLocales = renderLocalized(lines, defaultLocale)
            val merged = mergeLocaleMaps(shared, flavorLocales, defaultLocale)
            if (merged.isNotEmpty()) {
                flavors[flavor] = merged
            }
        }

        return PlayReleaseNotes(shared = shared, flavors = flavors, unmatchedHeadings = unmatchedHeadings)
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
            throw GradleException("Changelog file not found at ${file.absolutePath}.")
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

    /**
     * Strict variant of [readUnreleasedLinesOrNull]: fails the build when the file or the
     * `## [Unreleased]` section is missing.
     */
    private fun readUnreleasedLines(file: File): List<String> {
        if (!file.exists()) {
            throw GradleException("Changelog file not found at ${file.absolutePath}. Please create a CHANGELOG.md file at the root of your project.")
        }

        return readUnreleasedLinesOrNull(file)
            ?: throw GradleException("Section '## [Unreleased]' was not found in ${file.name}. Please add a '## [Unreleased]' section at the top of your changelog.")
    }

    /**
     * Non-throwing variant: returns `null` when the file does not exist or `## [Unreleased]` is
     * absent, so callers that treat "no notes" as a valid state stay non-fatal.
     */
    private fun readUnreleasedLinesOrNull(file: File): List<String>? {
        if (!file.exists()) return null

        val lines = file.readLines()
        val start = lines.indexOfFirst { UNRELEASED_HEADER_REGEX.containsMatchIn(it.trim()) }
        if (start == -1) return null

        val result = mutableListOf<String>()
        for (i in (start + 1) until lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("## ") || trimmed.startsWith("##\t")) {
                break
            }
            result.add(lines[i])
        }

        return result
    }

    /**
     * Merges shared (main) locale notes with a flavor's locale notes, prepending the shared
     * content so each flavor file stays self-contained.
     */
    private fun mergeLocaleMaps(
        shared: Map<String, String>,
        flavor: Map<String, String>,
        defaultLocale: String,
    ): Map<String, String> {
        if (shared.isEmpty()) return flavor
        if (flavor.isEmpty()) return shared

        val result = linkedMapOf<String, String>()
        val locales = (shared.keys + flavor.keys).distinct()

        for (locale in locales) {
            val sharedPart = shared[locale] ?: shared[defaultLocale]
            val flavorPart = flavor[locale]
            val combined = listOfNotNull(sharedPart, flavorPart).joinToString("\n")
            if (combined.isNotBlank()) {
                result[locale] = combined
            }
        }

        return result
    }

    /**
     * Splits a list of release-note lines by `#### [locale]` headers and renders category headings.
     * Lines before the first locale header are treated as shared content for [defaultLocale].
     */
    private fun renderLocalized(lines: List<String>, defaultLocale: String): Map<String, String> {
        val commonLines = mutableListOf<String>()
        val localeLines = linkedMapOf<String, MutableList<String>>()
        var currentLocale: String? = null

        for (raw in lines) {
            val trimmed = raw.trim()
            val localeMatch = LOCALE_HEADER_REGEX.find(trimmed)
            if (localeMatch != null) {
                currentLocale = localeMatch.groupValues[1].trim()
                localeLines.getOrPut(currentLocale) { mutableListOf() }
                continue
            }

            if (currentLocale != null) {
                localeLines[currentLocale]?.add(raw)
            } else {
                commonLines.add(raw)
            }
        }

        val renderedCommon = renderCategoryLines(commonLines)

        if (localeLines.isEmpty()) {
            return if (renderedCommon.isNotBlank()) mapOf(defaultLocale to renderedCommon) else emptyMap()
        }

        val result = linkedMapOf<String, String>()
        for ((locale, locLines) in localeLines) {
            val rendered = renderCategoryLines(commonLines + locLines)
            if (rendered.isNotBlank()) {
                result[locale] = rendered
            }
        }

        if (!result.containsKey(defaultLocale) && renderedCommon.isNotBlank()) {
            result[defaultLocale] = renderedCommon
        }

        return result
    }

    /**
     * Renders category headings (`### X` / `### [X]`) as a heading line with their bullets indented.
     */
    private fun renderCategoryLines(lines: List<String>): String {
        val out = mutableListOf<String>()
        var indent = false

        for (raw in lines) {
            val trimmed = raw.trim()
            when {
                trimmed.isEmpty() -> {
                    out.add("")
                    indent = false
                }
                isCategoryHeader(trimmed) -> {
                    out.add(categoryHeaderText(trimmed))
                    indent = true
                }
                else -> {
                    out.add(if (indent) "  " + raw.trimEnd() else raw.trimEnd())
                }
            }
        }

        return out.joinToString("\n").trim()
    }

    private fun isCategoryHeader(trimmed: String): Boolean =
        trimmed.startsWith("###") && !trimmed.startsWith("####")

    private fun categoryHeaderText(trimmed: String): String =
        trimmed.removePrefix("###").trim().removeSurrounding("[", "]").trim()

    private fun filterNotesByFlavor(lines: List<String>, flavorName: String?): String {
        if (flavorName.isNullOrBlank()) {
            return lines.joinToString("\n").trim()
        }

        val commonLines = mutableListOf<String>()
        val flavorLines = mutableListOf<String>()

        var currentSection: String? = null // null means common / top-level

        for (line in lines) {
            val trimmed = line.trim()
            val match = FLAVOR_HEADER_REGEX.find(trimmed)
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
