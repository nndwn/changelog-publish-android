package com.nndwn.changelog.publish.domain.model

import kotlinx.serialization.Serializable

/**
 * Data class representing the payload required for CI/CD changelog publishing.
 */
@Serializable
data class ChangelogData(
    val metadata: AndroidMetadata,
    val releaseNotes: String,
    val commitHash: String = "",
    val branchName: String = "",
    val buildEnvironment: String = "CI/CD",
    val timestamp: Long = System.currentTimeMillis()
)
