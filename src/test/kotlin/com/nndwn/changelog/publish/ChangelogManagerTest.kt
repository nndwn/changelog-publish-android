package com.nndwn.changelog.publish

import com.nndwn.changelog.publish.data.ChangelogManager
import com.nndwn.changelog.publish.domain.model.AndroidMetadata
import com.nndwn.changelog.publish.domain.model.ChangelogData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogManagerTest {

    @Test
    fun createPayload_returnsCorrectData() {
        val metadata = AndroidMetadata(
            appName = "RunText",
            versionName = "1.4.1",
            versionCode = 11
        )
        val payload = ChangelogManager.createPayload(
            metadata = metadata,
            releaseNotes = "Fixed UI bugs and updated exporter",
            commitHash = "abc1234",
            branchName = "main",
            environment = "GitHub Actions"
        )

        assertEquals("RunText", payload.metadata.appName)
        assertEquals("1.4.1", payload.metadata.versionName)
        assertEquals(11, payload.metadata.versionCode)
        assertEquals("Fixed UI bugs and updated exporter", payload.releaseNotes)
        assertEquals("abc1234", payload.commitHash)
        assertEquals("main", payload.branchName)
        assertEquals("GitHub Actions", payload.buildEnvironment)
        assertTrue(payload.timestamp > 0)
    }

    @Test
    fun toJsonAndFromJson_serializesAndDeserializesCorrectly() {
        val metadata = AndroidMetadata(
            appName = "SampleApp",
            versionName = "2.0.0",
            versionCode = 20
        )
        val original = ChangelogData(
            metadata = metadata,
            releaseNotes = "Major update",
            commitHash = "def5678",
            branchName = "release/2.0.0",
            buildEnvironment = "CI/CD",
            timestamp = 1700000000000L
        )

        val jsonString = ChangelogManager.toJson(original)
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("SampleApp"))
        assertTrue(jsonString.contains("2.0.0"))

        val parsed = ChangelogManager.fromJson(jsonString)
        assertEquals(original, parsed)
    }
}
