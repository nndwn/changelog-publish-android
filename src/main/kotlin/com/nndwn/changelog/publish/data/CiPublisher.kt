package com.nndwn.changelog.publish.data

/**
 * Helper utility to interact with CI/CD environment variables and prepare release metadata.
 */
object CiPublisher {

    /**
     * Reads common CI/CD environment variables (GitHub Actions, GitLab CI, Bitrise, etc.)
     */
    fun getCiEnvironmentInfo(): Map<String, String> {
        val env = System.getenv()
        return mapOf(
            "COMMIT_HASH" to (env["GITHUB_SHA"] ?: env["GIT_COMMIT"] ?: env["BITRISE_GIT_COMMIT"] ?: "local"),
            "BRANCH" to (env["GITHUB_REF_NAME"] ?: env["GIT_BRANCH"] ?: env["BITRISE_GIT_BRANCH"] ?: "main"),
            "BUILD_NUMBER" to (env["GITHUB_RUN_NUMBER"] ?: env["BUILD_NUMBER"] ?: "1"),
            "CI_PLATFORM" to when {
                env["GITHUB_ACTIONS"] == "true" -> "GitHub Actions"
                env["GITLAB_CI"] != null -> "GitLab CI"
                env["BITRISE_IO"] != null -> "Bitrise"
                else -> "Local / Unknown"
            }
        )
    }
}
