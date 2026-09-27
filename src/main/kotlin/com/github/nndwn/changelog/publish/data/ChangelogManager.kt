package com.github.nndwn.changelog.publish.data

import com.github.nndwn.changelog.publish.domain.model.AndroidMetadata
import com.github.nndwn.changelog.publish.domain.model.ChangelogData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manager for generating, formatting, and exporting CI/CD changelog payloads.
 */
object ChangelogManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Creates a new ChangelogData instance with the given release information.
     */
    fun createPayload(
        metadata: AndroidMetadata,
        releaseNotes: String,
        commitHash: String = "",
        branchName: String = "",
        environment: String = "CI/CD"
    ): ChangelogData {
        return ChangelogData(
            metadata = metadata,
            releaseNotes = releaseNotes,
            commitHash = commitHash,
            branchName = branchName,
            buildEnvironment = environment
        )
    }

    /**
     * Formats ChangelogData into a JSON string suitable for API / Webhook payloads in CI/CD pipelines.
     */
    fun toJson(changelogData: ChangelogData): String {
        return json.encodeToString(changelogData)
    }

    /**
     * Parses a JSON string back into a ChangelogData object.
     */
    fun fromJson(jsonString: String): ChangelogData {
        return json.decodeFromString(jsonString)
    }
}
