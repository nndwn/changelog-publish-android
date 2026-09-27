package io.github.nndwn.changelog.publish

import io.github.nndwn.changelog.publish.data.CiPublisher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CiPublisherTest {

    @Test
    fun getCiEnvironmentInfo_returnsNonNullValues() {
        val info = CiPublisher.getCiEnvironmentInfo()

        assertNotNull(info)
        assertTrue(info.containsKey("COMMIT_HASH"))
        assertTrue(info.containsKey("BRANCH"))
        assertTrue(info.containsKey("BUILD_NUMBER"))
        assertTrue(info.containsKey("CI_PLATFORM"))
    }

    @Test
    fun getCiEnvironmentInfo_resolvesGitHubActionsMetadata() {
        val env = mapOf(
            "GITHUB_ACTIONS" to "true",
            "GITHUB_REF_TYPE" to "branch",
            "GITHUB_REF_NAME" to "main",
            "GITHUB_SHA" to "abc123",
            "GITHUB_RUN_NUMBER" to "42",
        )

        val info = CiPublisher.getCiEnvironmentInfo(env)
        assertEquals("GitHub Actions", info["CI_PLATFORM"])
        assertEquals("main", info["BRANCH"])
        assertEquals("abc123", info["COMMIT_HASH"])
        assertEquals("42", info["BUILD_NUMBER"])
    }

    @Test
    fun resolveBranchName_leavesBranchEmpty_onGitHubTagBuild() {
        val env = mapOf(
            "GITHUB_ACTIONS" to "true",
            "GITHUB_REF_TYPE" to "tag",
            "GITHUB_REF_NAME" to "v1.0.0",
        )

        // A tag is not a branch, so it must not be reported as the branch name.
        assertEquals("", CiPublisher.resolveBranchName(env))
    }

    @Test
    fun resolveBranchName_defaultsToMain_whenNothingProvided() {
        assertEquals("main", CiPublisher.resolveBranchName(emptyMap()))
    }
}
