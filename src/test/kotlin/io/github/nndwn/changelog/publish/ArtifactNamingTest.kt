package io.github.nndwn.changelog.publish

import io.github.nndwn.changelog.publish.data.ArtifactNaming
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtifactNamingTest {

    @Test
    fun sanitizeAppName_replacesSpacesAndSpecialCharacters() {
        assertEquals("My_App", ArtifactNaming.sanitizeAppName("My App", "app"))
        assertEquals("Run_Text", ArtifactNaming.sanitizeAppName("Run@Text!", "app"))
    }

    @Test
    fun sanitizeAppName_stripsDiacritics() {
        assertEquals("Cafe_App", ArtifactNaming.sanitizeAppName("Café App", "app"))
    }

    @Test
    fun sanitizeAppName_collapsesRepeatedSeparators() {
        assertEquals("My_App", ArtifactNaming.sanitizeAppName("My   App", "app"))
    }

    @Test
    fun sanitizeAppName_fallsBack_whenNameIsNonAsciiOnly() {
        assertEquals("my-project", ArtifactNaming.sanitizeAppName("我的应用 🚀", "my-project"))
    }

    @Test
    fun sanitizeVersionName_replacesInvalidCharacters() {
        assertEquals("1.0.0_rc1", ArtifactNaming.sanitizeVersionName("1.0.0/rc1"))
        assertEquals("1.0.0_beta", ArtifactNaming.sanitizeVersionName("1.0.0:beta"))
    }

    @Test
    fun buildFileName_withoutFlavor() {
        val fileName = ArtifactNaming.buildFileName(
            appName = "My App",
            versionName = "1.2.0",
            versionCode = 120,
            flavorName = null,
            buildType = "release",
        )
        assertEquals("My_App_v1.2.0(120)_release.apk", fileName)
    }

    @Test
    fun buildFileName_withFlavorAndVersionSuffix() {
        val fileName = ArtifactNaming.buildFileName(
            appName = "My App FOSS",
            versionName = "1.2.0-foss",
            versionCode = 120,
            flavorName = "foss",
            buildType = "release",
        )
        assertEquals("My_App_FOSS_v1.2.0-foss(120)_foss_release.apk", fileName)
    }

    @Test
    fun buildFileName_withOutputSuffixForAbiSplit() {
        val fileName = ArtifactNaming.buildFileName(
            appName = "My App",
            versionName = "1.2.0",
            versionCode = 120,
            flavorName = "foss",
            buildType = "release",
            outputSuffix = "arm64-v8a",
        )
        assertEquals("My_App_v1.2.0(120)_foss_release_arm64-v8a.apk", fileName)
    }

    @Test
    fun buildFileName_usesFallbackAppName_whenNameIsNonAscii() {
        val fileName = ArtifactNaming.buildFileName(
            appName = "我的应用",
            versionName = "1.0.0",
            versionCode = 1,
            flavorName = null,
            buildType = "release",
            fallbackAppName = "io.github.nndwn.app",
        )
        assertEquals("io.github.nndwn.app_v1.0.0(1)_release.apk", fileName)
    }
}
