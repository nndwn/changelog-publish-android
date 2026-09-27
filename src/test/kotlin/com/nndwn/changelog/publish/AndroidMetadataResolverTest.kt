package com.nndwn.changelog.publish

import com.nndwn.changelog.publish.data.AndroidMetadataResolver
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidMetadataResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolve_extractsAppNameFromMainStringsXml() {
        val project = ProjectBuilder.builder().withProjectDir(tempFolder.root).withName("TestProject").build()

        tempFolder.newFolder("src", "main", "res", "values")

        val manifest = tempFolder.newFile("src/main/AndroidManifest.xml")
        manifest.writeText(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:label="@string/app_name" />
            </manifest>
            """.trimIndent()
        )

        val strings = tempFolder.newFile("src/main/res/values/strings.xml")
        strings.writeText(
            """
            <resources>
                <string name="app_name">My Main App</string>
            </resources>
            """.trimIndent()
        )

        val metadata = AndroidMetadataResolver.resolve(project)
        assertEquals("My Main App", metadata.appName)
    }

    @Test
    fun resolve_extractsAppNameFromFlavorStringsXmlOverride() {
        val project = ProjectBuilder.builder().withProjectDir(tempFolder.root).withName("TestProject").build()

        tempFolder.newFolder("src", "main", "res", "values")
        tempFolder.newFolder("src", "foss", "res", "values")

        val mainManifest = tempFolder.newFile("src/main/AndroidManifest.xml")
        mainManifest.writeText(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:label="@string/app_name" />
            </manifest>
            """.trimIndent()
        )

        val mainStrings = tempFolder.newFile("src/main/res/values/strings.xml")
        mainStrings.writeText(
            """
            <resources>
                <string name="app_name">My Main App</string>
            </resources>
            """.trimIndent()
        )

        val fossStrings = tempFolder.newFile("src/foss/res/values/strings.xml")
        fossStrings.writeText(
            """
            <resources>
                <string name="app_name">My FOSS App</string>
            </resources>
            """.trimIndent()
        )

        val metadata = AndroidMetadataResolver.resolve(project, "foss")
        assertEquals("My FOSS App", metadata.appName)
    }

    @Test
    fun resolve_fallsBackToProjectName_whenNoManifestExists() {
        val project = ProjectBuilder.builder().withName("FallbackApp").build()
        val metadata = AndroidMetadataResolver.resolve(project)
        assertEquals("FallbackApp", metadata.appName)
    }
}
