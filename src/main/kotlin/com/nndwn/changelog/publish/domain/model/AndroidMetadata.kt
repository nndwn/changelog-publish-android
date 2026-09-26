package com.nndwn.changelog.publish.domain.model

import kotlinx.serialization.Serializable

/**
 * Data class representing metadata extracted from Android project / AndroidManifest.
 */
@Serializable
data class AndroidMetadata(
    val appName: String,
    val versionName: String,
    val versionCode: Int
)
