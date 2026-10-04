package io.github.nndwn.changelog.publish.data

import io.github.nndwn.changelog.publish.domain.model.AndroidMetadata
import org.gradle.api.Project
import java.io.File
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

object AndroidMetadataResolver {

    fun resolve(project: Project, flavorName: String? = null): AndroidMetadata {
        val (versionName, versionCode) = resolveVersionInfo(project)
        val appName = resolveAppName(project, flavorName)

        return AndroidMetadata(appName = appName, versionName = versionName, versionCode = versionCode)
    }

    private fun resolveVersionInfo(project: Project): Pair<String, Int> {
        val (androidVersionName, versionCode) = extractAndroidVersion(project)
        val versionName = androidVersionName
            .ifBlank { extractProjectVersion(project) }
            .ifBlank { "1.0.0" }

        return versionName to versionCode
    }

    private fun extractAndroidVersion(project: Project): Pair<String, Int> {
        val androidExt = project.extensions.findByName("android") ?: return "" to 1
        return try {
            val defaultConfig = androidExt.javaClass.getMethod("getDefaultConfig").invoke(androidExt)
                ?: return "" to 1

            val vName = (defaultConfig.javaClass.getMethod("getVersionName").invoke(defaultConfig) as? String).orEmpty()
            val vCode = (defaultConfig.javaClass.getMethod("getVersionCode").invoke(defaultConfig) as? Int) ?: 1

            vName to if (vCode > 0) vCode else 1
        } catch (_: Exception) {
            "" to 1
        }
    }

    private fun extractProjectVersion(project: Project): String {
        val projVersion = project.version.toString().trim()
        return if (projVersion != "unspecified") projVersion else ""
    }

    private fun resolveAppName(project: Project, flavorName: String?): String {
        if (!flavorName.isNullOrBlank()) {
            val nameFromFlavor = extractAppNameFromSourceSet(project, flavorName, flavorName)
            if (!nameFromFlavor.isNullOrEmpty()) {
                return nameFromFlavor
            }
        }

        val nameFromMain = extractAppNameFromSourceSet(project, "main", flavorName)
        if (!nameFromMain.isNullOrEmpty()) {
            return nameFromMain
        }

        return project.name
    }

    private fun extractAppNameFromSourceSet(project: Project, sourceSet: String, flavorName: String?): String? {
        val manifest = project.file("src/$sourceSet/AndroidManifest.xml")
        if (!manifest.exists()) return null
        return parseAppNameFromManifest(manifest, project, flavorName)
    }

    private fun parseAppNameFromManifest(manifestFile: File, project: Project, flavorName: String? = null): String? {
        return try {
            val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = docBuilder.parse(manifestFile)
            val appNodes = doc.getElementsByTagName("application")
            if (appNodes.length > 0) {
                val appNode = appNodes.item(0)
                val labelAttr = appNode.attributes?.getNamedItem("android:label")?.nodeValue
                if (!labelAttr.isNullOrEmpty()) {
                    return if (labelAttr.startsWith("@string/")) {
                        val stringName = labelAttr.removePrefix("@string/")
                        resolveStringResource(project, stringName, flavorName)
                    } else {
                        labelAttr
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveStringResource(project: Project, stringName: String, flavorName: String? = null): String? {
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        if (!flavorName.isNullOrBlank()) {
            val flavorStringsFile = project.file("src/$flavorName/res/values/strings.xml")
            if (flavorStringsFile.exists()) {
                val value = extractStringFromXml(docBuilder, flavorStringsFile, stringName)
                if (!value.isNullOrEmpty()) {
                    return value
                }
            }
        }

        // 2. Fallback to main strings.xml
        val mainStringsFile = project.file("src/main/res/values/strings.xml")
        if (mainStringsFile.exists()) {
            return extractStringFromXml(docBuilder, mainStringsFile, stringName)
        }

        return null
    }

    private fun extractStringFromXml(docBuilder: DocumentBuilder, xmlFile: File, stringName: String): String? {
        return try {
            val stringsDoc = docBuilder.parse(xmlFile)
            val stringNodes = stringsDoc.getElementsByTagName("string")
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                if (node.attributes?.getNamedItem("name")?.nodeValue == stringName) {
                    return node.textContent
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
