package io.github.nndwn.changelog.publish.data

import org.gradle.api.GradleException
import java.io.File

/**
 * Generates release notes text files structured for Triple-T Gradle Play Publisher (GPP).
 * Output path: `<targetDir>/<locale>/production.txt`
 */
object PlayReleaseNotesGenerator {

    private const val PLAY_STORE_LIMIT_LINK = "https://support.google.com/googleplay/android-developer/answer/9866151"

    /**
     * Writes localized release notes to directory structure `<targetDir>/<locale>/<trackFileName>`.
     * Validates that each locale text does not exceed [maxCharacterLimit] (default: 500).
     *
     * @throws GradleException if any locale release notes exceed [maxCharacterLimit].
     */
    fun generate(
        localizedNotes: Map<String, String>,
        targetDir: File,
        trackFileName: String = "production.txt",
        maxCharacterLimit: Int = 500,
    ): List<File> {
        val generatedFiles = mutableListOf<File>()

        // First pass: Validate length for all locales
        for ((locale, notes) in localizedNotes) {
            val length = notes.length
            if (length > maxCharacterLimit) {
                val snippet = notes.take(100).replace("\n", " ")
                throw GradleException(
                    "Release notes for locale '$locale' exceeded the $maxCharacterLimit character limit ($length chars).\n" +
                    "Google Play Store restricts release notes to a maximum of $maxCharacterLimit characters per language.\n" +
                    "Snippet: \"$snippet...\"\n" +
                    "Official Reference: $PLAY_STORE_LIMIT_LINK"
                )
            }
        }

        // Clean up stale locale directories from previous runs.
        val activeLocales = localizedNotes.keys
        targetDir.listFiles()?.forEach { existing ->
            if (existing.isDirectory && existing.name !in activeLocales) {
                existing.deleteRecursively()
            }
        }

        // Second pass: Write files
        for ((locale, notes) in localizedNotes) {
            val localeDir = File(targetDir, locale)
            if (!localeDir.exists()) {
                localeDir.mkdirs()
            }

            val outputFile = File(localeDir, trackFileName)
            outputFile.writeText(notes)
            generatedFiles.add(outputFile)
        }

        return generatedFiles
    }
}
