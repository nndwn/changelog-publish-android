package io.github.nndwn.changelog.publish.data

import org.gradle.api.GradleException
import java.io.File

/**
 * Generates release notes text files structured for Triple-T Gradle Play Publisher (GPP).
 *
 * Output path: `<targetDir>/<locale>/<track>.txt`
 */
object PlayReleaseNotesGenerator {

    private const val PLAY_STORE_LIMIT_LINK = "https://support.google.com/googleplay/android-developer/answer/9866151"

    private const val TRACK_FILE_EXTENSION = ".txt"

    /**
     * Writes localized release notes to directory structure `<targetDir>/<locale>/<track>.txt`.
     *
     * One file is written per entry of [playTracks] so that GPP finds the notes whichever track it
     * publishes to (`<track>.txt` first, `default.txt` as the universal fallback). Any file inside a
     * locale directory that does not belong to a configured track is removed, so a track that is no
     * longer configured can never be picked up.
     *
     * Validates that each locale text does not exceed [maxCharacterLimit] (default: 500).
     *
     * @param playTracks GPP track names, e.g. `["default", "internal", "production"]`.
     * @return every file that was written.
     * @throws GradleException if any locale release notes exceed [maxCharacterLimit].
     */
    fun generate(
        localizedNotes: Map<String, String>,
        targetDir: File,
        playTracks: List<String>,
        maxCharacterLimit: Int = 500,
    ): List<File> {
        if (localizedNotes.isEmpty() || playTracks.isEmpty()) return emptyList()

        val trackFileNames = playTracks.map { track ->
            if (track.endsWith(TRACK_FILE_EXTENSION)) track else "$track$TRACK_FILE_EXTENSION"
        }

        validateNoteLengths(localizedNotes, maxCharacterLimit)
        cleanupStaleLocales(targetDir, localizedNotes.keys)

        val generatedFiles = mutableListOf<File>()
        for ((locale, notes) in localizedNotes) {
            val localeDir = File(targetDir, locale).apply { if (!exists()) mkdirs() }
            cleanupStaleTrackFiles(localeDir, trackFileNames)

            for (trackFileName in trackFileNames) {
                val outputFile = File(localeDir, trackFileName)
                outputFile.writeText(notes)
                generatedFiles.add(outputFile)
            }
        }

        return generatedFiles
    }

    private fun validateNoteLengths(localizedNotes: Map<String, String>, maxCharacterLimit: Int) {
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
    }

    private fun cleanupStaleLocales(targetDir: File, activeLocales: Set<String>) {
        targetDir.listFiles()?.forEach { existing ->
            if (existing.isDirectory && existing.name !in activeLocales) {
                val deleted = existing.deleteRecursively()
                check(deleted || !existing.exists())
            }
        }
    }

    private fun cleanupStaleTrackFiles(localeDir: File, trackFileNames: List<String>) {
        localeDir.listFiles()
            ?.filter { it.isFile && it.name !in trackFileNames }
            ?.forEach { file ->
                val deleted = file.delete()
                check(deleted || !file.exists())
            }
    }
}
