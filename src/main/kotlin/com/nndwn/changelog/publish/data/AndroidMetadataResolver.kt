package com.nndwn.changelog.publish.data

import com.nndwn.changelog.publish.domain.model.AndroidMetadata
import org.gradle.api.Project
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

object AndroidMetadataResolver {

    fun resolve(project: Project): AndroidMetadata {
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

        val manifestFile = project.file("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val extractedName = parseAppNameFromManifest(manifestFile, project)
            if (!extractedName.isNullOrEmpty()) {
                appName = extractedName
            }
        }

        return AndroidMetadata(appName = appName, versionName = versionName, versionCode = versionCode)
    }

    private fun parseAppNameFromManifest(manifestFile: File, project: Project): String? {
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
                        val stringsFile = project.file("src/main/res/values/strings.xml")
                        if (stringsFile.exists()) {
                            val stringsDoc = docBuilder.parse(stringsFile)
                            val stringNodes = stringsDoc.getElementsByTagName("string")
                            for (i in 0 until stringNodes.length) {
                                val node = stringNodes.item(i)
                                if (node.attributes?.getNamedItem("name")?.nodeValue == stringName) {
                                    return node.textContent
                                }
                            }
                        }
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
}
