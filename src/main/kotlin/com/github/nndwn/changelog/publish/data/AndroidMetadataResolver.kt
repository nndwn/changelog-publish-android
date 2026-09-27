package com.github.nndwn.changelog.publish.data

import com.github.nndwn.changelog.publish.domain.model.AndroidMetadata
import org.gradle.api.Project
import java.io.File
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

object AndroidMetadataResolver {

    fun resolve(project: Project, flavorName: String? = null): AndroidMetadata {
        var appName = project.name
        var versionName = "1.0.0"
        var versionCode = 1

        val androidExt = project.extensions.findByName("android")
        if (androidExt != null) {
            try {
                val defaultConfig = androidExt.javaClass.getMethod("getDefaultConfig").invoke(androidExt)
                if (defaultConfig != null) {
                    val vName = defaultConfig.javaClass.getMethod("getVersionName").invoke(defaultConfig) as? String
                    if (!vName.isNullOrEmpty()) {
                        versionName = vName
                    }

                    val vCode = defaultConfig.javaClass.getMethod("getVersionCode").invoke(defaultConfig) as? Int
                    if (vCode != null && vCode > 0) {
                        versionCode = vCode
                    }
                }
            } catch (ignored: Exception) {
            }
        }

        // 1. Try flavor-specific manifest if flavorName is provided
        if (!flavorName.isNullOrBlank()) {
            val flavorManifest = project.file("src/$flavorName/AndroidManifest.xml")
            if (flavorManifest.exists()) {
                val extractedName = parseAppNameFromManifest(flavorManifest, project, flavorName)
                if (!extractedName.isNullOrEmpty()) {
                    appName = extractedName
                }
            }
        }

        // 2. Fallback to main manifest if appName was not resolved from flavor
        if (appName == project.name) {
            val mainManifest = project.file("src/main/AndroidManifest.xml")
            if (mainManifest.exists()) {
                val extractedName = parseAppNameFromManifest(mainManifest, project, flavorName)
                if (!extractedName.isNullOrEmpty()) {
                    appName = extractedName
                }
            }
        }

        return AndroidMetadata(appName = appName, versionName = versionName, versionCode = versionCode)
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
                    if (labelAttr.startsWith("@string/")) {
                        val stringName = labelAttr.removePrefix("@string/")
                        return resolveStringResource(project, stringName, flavorName)
                    } else {
                        return labelAttr
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveStringResource(project: Project, stringName: String, flavorName: String? = null): String? {
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        // 1. Try flavor strings.xml first
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
        } catch (e: Exception) {
            null
        }
    }
}
