package com.github.nndwn.changelog.publish.data

import java.text.Normalizer

/**
 * Pure helper for building safe, standardized build artifact (APK/AAB) file names.
 *
 * The generated name follows the pattern:
 * `{AppName}_v{VersionName}({VersionCode})_{Flavor}_{BuildType}[_{OutputVariant}].{extension}`
 */
object ArtifactNaming {

    private val INVALID_APP_CHARS = Regex("[^A-Za-z0-9_-]+")
    private val INVALID_VERSION_CHARS = Regex("[^A-Za-z0-9._-]+")
    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val REPEATED_SEPARATORS = Regex("[_-]+")

    /**
     * Normalizes an application name into a safe file name token.
     * Diacritics are stripped, and any remaining unsupported characters are replaced with `_`.
     * If nothing meaningful remains (e.g. the name is only emoji or non-Latin script), [fallback] is used.
     */
    fun sanitizeAppName(rawName: String, fallback: String = "app"): String {
        val decomposed = Normalizer.normalize(rawName, Normalizer.Form.NFKD).replace(COMBINING_MARKS, "")
        val cleaned = decomposed
            .replace(INVALID_APP_CHARS, "_")
            .replace(REPEATED_SEPARATORS, "_")
            .trim('_', '-')
        return cleaned.ifBlank { fallback.ifBlank { "app" } }
    }

    /**
     * Normalizes a version name into a safe file name token, keeping dots, dashes and underscores.
     */
    fun sanitizeVersionName(rawVersion: String): String {
        return rawVersion
            .replace(INVALID_VERSION_CHARS, "_")
            .trim('_')
            .ifBlank { "0" }
    }

    /**
     * Builds the standardized artifact file name.
     *
     * @param outputSuffix optional identifier appended after the build type to disambiguate
     *   split outputs (e.g. `arm64-v8a`, `xhdpi`). Prevents file name collisions.
     */
    fun buildFileName(
        appName: String,
        versionName: String,
        versionCode: Int,
        flavorName: String?,
        buildType: String,
        outputSuffix: String? = null,
        fallbackAppName: String = "app",
        extension: String = "apk",
    ): String {
        val safeApp = sanitizeAppName(appName, fallbackAppName)
        val safeVersion = sanitizeVersionName(versionName)
        val flavorPart = flavorName
            ?.takeIf { it.isNotBlank() }
            ?.let { "_${it.replace(INVALID_APP_CHARS, "_").trim('_')}" }
            .orEmpty()
        val suffixPart = outputSuffix
            ?.takeIf { it.isNotBlank() }
            ?.let { "_${it.replace(INVALID_APP_CHARS, "_").trim('_')}" }
            .orEmpty()

        return "${safeApp}_v${safeVersion}(${versionCode})${flavorPart}_${buildType}${suffixPart}.${extension}"
    }
}
