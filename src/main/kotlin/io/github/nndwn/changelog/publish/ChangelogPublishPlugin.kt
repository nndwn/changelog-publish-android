package io.github.nndwn.changelog.publish

import io.github.nndwn.changelog.publish.data.AndroidMetadataResolver
import io.github.nndwn.changelog.publish.data.ArtifactNaming
import io.github.nndwn.changelog.publish.data.ChangelogParser
import io.github.nndwn.changelog.publish.domain.model.AndroidMetadata
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import java.lang.reflect.Proxy

class ChangelogPublishPlugin : Plugin<Project> {

    companion object {
        private const val TYPE_KOTLIN = "kotlin.jvm.functions.Function1"

        /** Tracks to emit release notes for when `changelogPublish.playTracks` is not configured. */
        private val DEFAULT_PLAY_TRACKS = listOf("default", "internal", "production")
    }
    override fun apply(target: Project) {
        val extension = target.extensions.create("changelogPublish", ChangelogPublishExtension::class.java)

        val changelogFileProvider = target.layout.projectDirectory.file(
            target.provider {
                ChangelogParser.findChangelogFile(target.rootDir).absolutePath
            }
        )

        val resolvedMetadata = target.provider { AndroidMetadataResolver.resolve(target) }
        val resolvedFlavors = target.provider { resolveProductFlavorNames(target) }

        // Release variants (variantName -> flavorName) discovered from AGP. Populated lazily while
        // variants are configured below and consumed by `generatePlayReleaseNotes`.
        val resolvedReleaseVariants = target.objects.mapProperty(String::class.java, String::class.java)

        target.tasks.register("generateChangelog", ChangelogPublishTask::class.java) {
            group = "publishing"
            description = "Generates and outputs release changelog payload for CI/CD"

            changelogFile.convention(changelogFileProvider)
            outputFile.convention(target.layout.buildDirectory.file("reports/changelog/changelog.json"))
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

            changelogFile.convention(changelogFileProvider)
            versionName.convention(extension.versionName.orElse(resolvedMetadata.map { it.versionName }))
        }

        target.tasks.register("generatePlayReleaseNotes", GeneratePlayReleaseNotesTask::class.java) {
            group = "publishing"
            description = "Generates Triple-T Play Publisher release notes text files per locale from CHANGELOG.md"

            changelogFile.convention(changelogFileProvider)
            defaultLocale.convention(extension.defaultLocale.orElse("en-US"))
            // Collection properties are initialised to an empty collection (never "absent"), so
            // `orElse` cannot act as a default - map the empty value instead.
            playTracks.convention(extension.playTracks.map { it.ifEmpty { DEFAULT_PLAY_TRACKS } })
            playVariants.convention(
                extension.playVariants.zip(resolvedReleaseVariants) { explicit, resolved ->
                    explicit.ifEmpty { resolved }
                }
            )
            playFlavors.convention(
                extension.playFlavors.zip(resolvedFlavors) { explicit, resolved ->
                    explicit.ifEmpty { resolved }
                }
            )
            maxPlayNotesLength.convention(extension.maxPlayNotesLength.orElse(500))
            playSourceSetsRoot.convention(
                extension.playSourceSetsRoot.orElse(target.layout.projectDirectory.dir("src"))
            )
            rootDir.convention(target.layout.projectDirectory)
        }

        configureArtifactRenaming(target, extension, resolvedReleaseVariants)
    }

    private fun configureArtifactRenaming(
        project: Project,
        extension: ChangelogPublishExtension,
        releaseVariants: MapProperty<String, String>,
    ) {
        project.plugins.withId("com.android.application") {
            // Prefer the modern AGP API; fall back to the legacy variant API for older AGP.
            if (configureWithAndroidComponents(project, extension, releaseVariants)) return@withId
            configureWithLegacyVariants(project, extension, releaseVariants)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Modern AGP API: androidComponents.onVariants (AGP 7+)
    // ---------------------------------------------------------------------------------------------

    private fun configureWithAndroidComponents(
        project: Project,
        extension: ChangelogPublishExtension,
        releaseVariants: MapProperty<String, String>,
    ): Boolean {
        val androidComponents = project.extensions.findByName("androidComponents") ?: return false
        return try {
            val selector = invokeMethod(androidComponents, "selector") ?: return false
            val allSelector = invokeMethod(selector, "all") ?: return false

            val methods = androidComponents.javaClass.methods.filter { it.name == "onVariants" }
            if (methods.isEmpty()) return false

            val method = methods.firstOrNull {
                it.parameterTypes.size == 2 && (
                    Action::class.java.isAssignableFrom(it.parameterTypes[1]) ||
                    it.parameterTypes[1].name == TYPE_KOTLIN
                )
            } ?: methods.firstOrNull {
                it.parameterTypes.size == 1 && (
                    Action::class.java.isAssignableFrom(it.parameterTypes[0]) ||
                    it.parameterTypes[0].name == TYPE_KOTLIN
                )
            } ?: methods.firstOrNull { it.parameterTypes.size == 2 }
              ?: methods.firstOrNull { it.parameterTypes.size == 1 }
              ?: return false

            method.isAccessible = true

            val callbackParamType = if (method.parameterTypes.size == 2) {
                method.parameterTypes[1]
            } else {
                method.parameterTypes[0]
            }

            val callback = createCallback(callbackParamType) { variant ->
                configureModernVariant(project, extension, releaseVariants, variant)
            }

            if (method.parameterTypes.size == 2) {
                method.invoke(androidComponents, allSelector, callback)
            } else {
                method.invoke(androidComponents, callback)
            }

            project.logger.info("[changelog-publish] Artifact renaming is using the AGP androidComponents.onVariants API.")
            true
        } catch (e: Exception) {
            project.logger.warn("[changelog-publish] androidComponents.onVariants is unavailable, falling back to applicationVariants: ${e.message}")
            false
        }
    }

    private fun createCallback(paramType: Class<*>, block: (Any) -> Unit): Any {
        if (Action::class.java.isAssignableFrom(paramType)) {
            return Action<Any> { block(this) }
        }
        if (paramType.name == TYPE_KOTLIN) {
            return { arg: Any -> block(arg) }
        }
        if (paramType.isInterface) {
            return Proxy.newProxyInstance(paramType.classLoader, arrayOf(paramType)) { _, _, args ->
                if (args != null && args.isNotEmpty()) {
                    block(args[0])
                }
                null
            }
        }
        return Action<Any> { block(this) }
    }

    private fun configureModernVariant(
        project: Project,
        extension: ChangelogPublishExtension,
        releaseVariants: MapProperty<String, String>,
        variant: Any,
    ) {
        try {
            val buildType = invokeString(variant, "getBuildType") ?: "release"
            if (!buildType.equals("release", ignoreCase = true)) return

            val rawVariantName = invokeString(variant, "getName") ?: buildType
            val flavorName = invokeString(variant, "getFlavorName")?.takeIf { it.isNotBlank() }
            releaseVariants.put(rawVariantName, flavorName.orEmpty())
            val mergedFlavor = invokeMethod(variant, "getMergedFlavor")
            val versionNameSuffix = mergedFlavor?.let { invokeString(it, "getVersionNameSuffix") }
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
            val variantVersionName = providerString(invokeMethod(variant, "getVersionName"))?.takeIf { it.isNotBlank() }
            val variantVersionCode = providerInt(invokeMethod(variant, "getVersionCode"))

            registerVariantChangelogTask(
                project = project,
                extension = extension,
                rawVariantName = rawVariantName,
                flavorName = flavorName,
                versionNameSuffix = versionNameSuffix,
                variantVersionName = variantVersionName,
                variantVersionCode = variantVersionCode,
            )

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
    private fun configureWithLegacyVariants(
        project: Project,
        extension: ChangelogPublishExtension,
        releaseVariants: MapProperty<String, String>,
    ) {
        val androidExt = project.extensions.findByName("android") ?: return
        val variants = try {
            androidExt.javaClass.getMethod("getApplicationVariants").invoke(androidExt)
                as? DomainObjectCollection<Any>
        } catch (e: Exception) {
            project.logger.warn("[changelog-publish] Unable to access android.applicationVariants, artifact renaming skipped: ${e.message}")
            null
        } ?: return

        variants.all { configureLegacyVariant(project, extension, releaseVariants, this) }
    }

    private fun configureLegacyVariant(
        project: Project,
        extension: ChangelogPublishExtension,
        releaseVariants: MapProperty<String, String>,
        variant: Any,
    ) {
        try {
            val buildTypeObj = invokeMethod(variant, "getBuildType")
            val buildType = buildTypeObj?.let { invokeString(it, "getName") } ?: "release"
            if (!buildType.equals("release", ignoreCase = true)) return

            val rawVariantName = invokeString(variant, "getName") ?: buildType
            val flavorName = invokeString(variant, "getFlavorName")?.takeIf { it.isNotBlank() }
            releaseVariants.put(rawVariantName, flavorName.orEmpty())
            val mergedFlavor = invokeMethod(variant, "getMergedFlavor")
            val versionNameSuffix = mergedFlavor?.let { invokeString(it, "getVersionNameSuffix") }
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
            val variantVersionName = mergedFlavor?.let { invokeString(it, "getVersionName") }?.takeIf { it.isNotBlank() }
            val variantVersionCode = mergedFlavor?.let { invokeInt(it, "getVersionCode") }

            registerVariantChangelogTask(
                project = project,
                extension = extension,
                rawVariantName = rawVariantName,
                flavorName = flavorName,
                versionNameSuffix = versionNameSuffix,
                variantVersionName = variantVersionName,
                variantVersionCode = variantVersionCode,
            )

            val outputs = invokeMethod(variant, "getOutputs") as? DomainObjectCollection<*> ?: return
            val resolvedMetadata = AndroidMetadataResolver.resolve(project, flavorName)

            outputs.all {
                renameOutput(
                    project = project,
                    extension = extension,
                    output = this,
                    resolvedMetadata = resolvedMetadata,
                    flavorName = flavorName,
                    buildType = buildType,
                    versionNameSuffix = versionNameSuffix,
                    variantVersionName = variantVersionName,
                    variantVersionCode = variantVersionCode,
                )
            }
        } catch (e: Exception) {
            project.logger.warn("[changelog-publish] Failed to configure APK renaming for a variant, skipping: ${e.message}")
        }
    }

    private fun registerVariantChangelogTask(
        project: Project,
        extension: ChangelogPublishExtension,
        rawVariantName: String,
        flavorName: String?,
        versionNameSuffix: String,
        variantVersionName: String?,
        variantVersionCode: Int?,
    ) {
        val changelogFileProvider = project.layout.projectDirectory.file(
            project.provider {
                ChangelogParser.findChangelogFile(project.rootDir).absolutePath
            }
        )

        val taskNames = mutableListOf("generateChangelog${rawVariantName.replaceFirstChar { it.uppercase() }}")
        if (!flavorName.isNullOrBlank()) {
            val flavorTaskName = "generateChangelog${flavorName.replaceFirstChar { it.uppercase() }}"
            if (!taskNames.contains(flavorTaskName)) {
                taskNames.add(flavorTaskName)
            }
        }

        for (taskName in taskNames) {
            if (project.tasks.findByName(taskName) != null) continue

            project.tasks.register(taskName, ChangelogPublishTask::class.java) {
                group = "publishing"
                description = "Generates and outputs release changelog payload for variant '$rawVariantName'"

                changelogFile.convention(changelogFileProvider)
                outputFile.convention(project.layout.buildDirectory.file("reports/changelog/changelog.json"))
                val resolvedMetadata = project.provider { AndroidMetadataResolver.resolve(project, flavorName) }

                appName.convention(extension.appName.orElse(resolvedMetadata.map { it.appName }))
                versionName.convention(
                    extension.versionName.orElse(
                        project.provider {
                            val baseVName = variantVersionName ?: resolvedMetadata.get().versionName
                            if (extension.versionName.isPresent) baseVName else "$baseVName$versionNameSuffix"
                        }
                    )
                )
                versionCode.convention(
                    extension.versionCode.orElse(
                        project.provider {
                            variantVersionCode ?: resolvedMetadata.get().versionCode
                        }
                    )
                )
                if (!flavorName.isNullOrBlank()) {
                    this.flavorName.convention(flavorName)
                }
                this.variantName.convention(rawVariantName)

                if (extension.releaseNotes.isPresent) {
                    releaseNotes.convention(extension.releaseNotes)
                }
            }
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

    private fun resolveProductFlavorNames(project: Project): List<String> {
        return try {
            val android = project.extensions.findByName("android") ?: return emptyList()
            val productFlavors = invokeMethod(android, "getProductFlavors") as? Iterable<*> ?: return emptyList()
            productFlavors.mapNotNull { flavor -> flavor?.let { invokeString(it, "getName") } }
        } catch (e: Exception) {
            project.logger.warn("[changelog-publish] Failed to resolve product flavors: ${e.message}")
            emptyList()
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
