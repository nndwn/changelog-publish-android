package io.github.nndwn.changelog.publish.data

/**
 * Helper utility to interact with CI/CD environment variables and prepare release metadata.
 */
object CiPublisher {

    /**
     * Reads common CI/CD environment variables (GitHub Actions, GitLab CI, Bitrise, etc.)
     */
    fun getCiEnvironmentInfo(): Map<String, String> = getCiEnvironmentInfo(System.getenv())

    internal fun getCiEnvironmentInfo(env: Map<String, String>): Map<String, String> {
        return mapOf(
            "COMMIT_HASH" to (env["GITHUB_SHA"] ?: env["GIT_COMMIT"] ?: env["BITRISE_GIT_COMMIT"] ?: "local"),
            "BRANCH" to resolveBranchName(env),
            "BUILD_NUMBER" to (env["GITHUB_RUN_NUMBER"] ?: env["BUILD_NUMBER"] ?: "1"),
            "CI_PLATFORM" to resolveCiPlatform(env),
        )
    }

    /**
     * Resolves the current branch name.
     *
     * On GitHub Actions tag builds, `GITHUB_REF_NAME` holds the *tag* name rather than a branch,
     * so the branch is left empty to avoid reporting a misleading value.
     */
    internal fun resolveBranchName(env: Map<String, String>): String {
        if (env["GITHUB_REF_TYPE"].equals("tag", ignoreCase = true)) {
            return env["GITHUB_BASE_REF"]?.takeIf { it.isNotBlank() }
                ?: env["GITHUB_HEAD_REF"]?.takeIf { it.isNotBlank() }
                ?: ""
        }
        return env["GITHUB_REF_NAME"]?.takeIf { it.isNotBlank() }
            ?: env["GITHUB_HEAD_REF"]?.takeIf { it.isNotBlank() }
            ?: env["GIT_BRANCH"]?.takeIf { it.isNotBlank() }
            ?: env["BITRISE_GIT_BRANCH"]?.takeIf { it.isNotBlank() }
            ?: "main"
    }

    private fun resolveCiPlatform(env: Map<String, String>): String = when {
        env["GITHUB_ACTIONS"] == "true" -> "GitHub Actions"
        env["GITLAB_CI"] != null -> "GitLab CI"
        env["BITRISE_IO"] != null -> "Bitrise"
        else -> "Local / Unknown"
    }
}
