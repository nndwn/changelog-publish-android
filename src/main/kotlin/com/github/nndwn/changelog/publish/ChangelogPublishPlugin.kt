package com.github.nndwn.changelog.publish

import com.github.nndwn.changelog.publish.data.AndroidMetadataResolver
import com.github.nndwn.changelog.publish.data.ArtifactNaming
import com.github.nndwn.changelog.publish.domain.model.AndroidMetadata
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project

class ChangelogPublishPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("changelogPublish", ChangelogPublishExtension::class.java)

        val resolvedMetadata = target.provider { AndroidMetadataResolver.resolve(target) }

        target.tasks.register("generateChangelog", ChangelogPublishTask::class.java) {
            group = "publishing"
            description = "Generates and outputs release changelog payload for CI/CD"

            appName.convention(extension.appName.orElse(resolvedMetadata.map { it.appName }))
            versionName.convention(extension.versionName.orElse(resolvedMetadata.map { it.versionName }))
            versionCode.convention(extension.versionCode.orElse(resolvedMetadata.map { it.versionCode }))
            if (extension.releaseNotes.isPresent) {
                releaseNotes.convention(extension.releaseNotes)
            }
        }

        target.tasks.register("releaseChangelog", ChangelogReleaseTask::class.java) {
            group = "publishing"
            description = "Promotes ## [Unreleased] section in CHANGELOG.md to a new release version"

            versionName.convention(extension.versionName.orElse(resolvedMetadata.map { it.versionName }))
        }

        configureArtifactRenaming(target, extension)
    }

    private fun configureArtifactRenaming(project: Project, extension: ChangelogPublishExtension) {
        project.plugins.withId("com.android.application") {
            // Prefer the modern AGP API; fall back to the legacy variant API for older AGP.
            if (configureWithAndroidComponents(project, extension)) return@withId
            configureWithLegacyVariants(project, extension)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Modern AGP API: androidComponents.onVariants (AGP 7+)
    // ---------------------------------------------------------------------------------------------

    private fun configureWithAndroidComponents(project: Project, extension: ChangelogPublishExtension): Boolean {
        val androidComponents = project.extensions.findByName("androidComponents") ?: return false
        return try {
            val selector = invokeMethod(androidComponents, "selector") ?: return false
            val allSelector = invokeMethod(selector, "all") ?: return false
            val onVariants = androidComponents.javaClass.methods.firstOrNull {
                it.name == "onVariants" && it.parameterTypes.size == 2
            } ?: return false

            onVariants.isAccessible = true
            onVariants.invoke(
                androidComponents,
                allSelector,
                object : Action<Any> {
                    override fun execute(variant: Any) = configureModernVariant(project, extension, variant)
                },
            )
            project.logger.info("[changelog-publish] Artifact renaming is using the AGP androidComponents.onVariants API.")
            true
        } catch (e: Exception) {
            project.logger.warn("[changelog-publish] androidComponents.onVariants is unavailable, falling back to applicationVariants: ${e.message}")
            false
        }
    }

    private fun configureModernVariant(project: Project, extension: ChangelogPublishExtension, variant: Any) {
        try {
            val buildType = invokeString(variant, "getBuildType") ?: "release"
            if (!buildType.equals("release", ignoreCase = true)) return

            val flavorName = invokeString(variant, "getFlavorName")?.takeIf { it.isNotBlank() }
            val mergedFlavor = invokeMethod(variant, "getMergedFlavor")
            val versionNameSuffix = mergedFlavor?.let { invokeString(it, "getVersionNameSuffix") }
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
            val variantVersionName = providerString(invokeMethod(variant, "getVersionName"))?.takeIf { it.isNotBlank() }
            val variantVersionCode = providerInt(invokeMethod(variant, "getVersionCode"))

            val outputs = invokeMethod(variant, "getOutputs") as? Iterable<*> ?: return
            val resolvedMetadata = AndroidMetadataResolver.resolve(project, flavorName)

            outputs.forEach { output ->
                if (output != null) {
                    renameOutput(
                        project = project,
                        extension = extension,
                        output = output,
                        resolvedMetadata = resolvedMetadata,
                        flavorName = flavorName,
                        buildType = buildType,
                        versionNameSuffix = versionNameSuffix,
                        variantVersionName = variantVersionName,
                        variantVersionCode = variantVersionCode,
                    )
                }
            }
        } catch (e: Exception) {
            project.logger.warn("[changelog-publish] Failed to configure artifact renaming for a variant, skipping: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Legacy AGP API: applicationVariants
    // ---------------------------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun configureWithLegacyVariants(project: Project, extension: ChangelogPublishExtension) {
        val androidExt = project.extensions.findByName("android") ?: return
        val variants = try {
            androidExt.javaClass.getMethod("getApplicationVariants").invoke(androidExt)
                as? DomainObjectCollection<Any>
        } catch (e: Exception) {
            project.logger.warn("[changelog-publish] Unable to access android.applicationVariants, artifact renaming skipped: ${e.message}")
            null
        } ?: return

        variants.all(object : Action<Any> {
            override fun execute(variant: Any) = configureLegacyVariant(project, extension, variant)
        })
    }

    private fun configureLegacyVariant(project: Project, extension: ChangelogPublishExtension, variant: Any) {
        try {
            val buildTypeObj = invokeMethod(variant, "getBuildType")
            val buildType = buildTypeObj?.let { invokeString(it, "getName") } ?: "release"
            if (!buildType.equals("release", ignoreCase = true)) return

            val flavorName = invokeString(variant, "getFlavorName")?.takeIf { it.isNotBlank() }
            val mergedFlavor = invokeMethod(variant, "getMergedFlavor")
            val versionNameSuffix = mergedFlavor?.let { invokeString(it, "getVersionNameSuffix") }
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
            val variantVersionName = mergedFlavor?.let { invokeString(it, "getVersionName") }?.takeIf { it.isNotBlank() }
            val variantVersionCode = mergedFlavor?.let { invokeInt(it, "getVersionCode") }

            val outputs = invokeMethod(variant, "getOutputs") as? DomainObjectCollection<Any> ?: return
            val resolvedMetadata = AndroidMetadataResolver.resolve(project, flavorName)

            outputs.all(object : Action<Any> {
                override fun execute(output: Any) {
                    renameOutput(
                        project = project,
                        extension = extension,
                        output = output,
                        resolvedMetadata = resolvedMetadata,
                        flavorName = flavorName,
                        buildType = buildType,
                        versionNameSuffix = versionNameSuffix,
                        variantVersionName = variantVersionName,
                        variantVersionCode = variantVersionCode,
                    )
                }
            })
        } catch (e: Exception) {
            project.logger.warn("[changelog-publish] Failed to configure APK renaming for a variant, skipping: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------------------------

    private fun renameOutput(
        project: Project,
        extension: ChangelogPublishExtension,
        output: Any,
        resolvedMetadata: AndroidMetadata,
        flavorName: String?,
        buildType: String,
        versionNameSuffix: String,
        variantVersionName: String?,
        variantVersionCode: Int?,
    ) {
        try {
            val fileName = buildArtifactName(
                project = project,
                extension = extension,
                resolvedMetadata = resolvedMetadata,
                flavorName = flavorName,
                buildType = buildType,
                versionNameSuffix = versionNameSuffix,
                variantVersionName = variantVersionName,
                variantVersionCode = variantVersionCode,
                outputSuffix = extractOutputSuffix(output),
            )

            // Modern API exposes `outputFileName` as a Property; legacy API exposes a setter.
            val outputFileName = invokeMethod(output, "getOutputFileName")
            if (outputFileName != null) {
                outputFileName.javaClass.getMethod("set", Any::class.java).invoke(outputFileName, fileName)
            } else {
                output.javaClass.getMethod("setOutputFileName", String::class.java).invoke(output, fileName)
            }

            project.logger.info("[changelog-publish] Renamed artifact output to '$fileName'")
        } catch (e: Exception) {
            project.logger.warn("[changelog-publish] Failed to rename an artifact output, keeping default name: ${e.message}")
        }
    }

    private fun buildArtifactName(
        project: Project,
        extension: ChangelogPublishExtension,
        resolvedMetadata: AndroidMetadata,
        flavorName: String?,
        buildType: String,
        versionNameSuffix: String,
        variantVersionName: String?,
        variantVersionCode: Int?,
        outputSuffix: String?,
    ): String {
        val explicitAppName = extension.appName.orNull?.takeIf { it.isNotBlank() }
        val explicitVersionName = extension.versionName.orNull?.takeIf { it.isNotBlank() }
        val explicitVersionCode = extension.versionCode.orNull

        val appName = explicitAppName ?: resolvedMetadata.appName
        val baseVersionName = explicitVersionName ?: variantVersionName ?: resolvedMetadata.versionName
        val versionName = if (explicitVersionName != null) {
            baseVersionName
        } else {
            "$baseVersionName$versionNameSuffix"
        }
        val versionCode = explicitVersionCode ?: variantVersionCode ?: resolvedMetadata.versionCode

        return ArtifactNaming.buildFileName(
            appName = appName,
            versionName = versionName,
            versionCode = versionCode,
            flavorName = flavorName,
            buildType = buildType,
            outputSuffix = outputSuffix,
            fallbackAppName = project.name,
        )
    }

    /**
     * Extracts a unique suffix from the output filters (e.g. ABI `arm64-v8a` or density `xhdpi`)
     * so that split outputs do not overwrite each other.
     */
    private fun extractOutputSuffix(output: Any): String? {
        return try {
            val filters = invokeMethod(output, "getFilters") as? Iterable<*> ?: return null
            val identifiers = filters.mapNotNull { filter -> filter?.let { invokeString(it, "getIdentifier") } }
                .filter { it.isNotBlank() }
            identifiers.joinToString("-").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun providerString(provider: Any?): String? {
        provider ?: return null
        return try {
            provider.javaClass.getMethod("get").invoke(provider) as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun providerInt(provider: Any?): Int? {
        provider ?: return null
        return try {
            provider.javaClass.getMethod("get").invoke(provider) as? Int
        } catch (_: Exception) {
            null
        }
    }

    private fun invokeMethod(target: Any, methodName: String): Any? =
        try {
            target.javaClass.getMethod(methodName).invoke(target)
        } catch (_: Exception) {
            null
        }

    private fun invokeString(target: Any, methodName: String): String? = invokeMethod(target, methodName) as? String

    private fun invokeInt(target: Any, methodName: String): Int? = invokeMethod(target, methodName) as? Int
}
