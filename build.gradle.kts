plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "2.0.21"
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.2.1"
}

group = "com.github.nndwn"
version = "0.1.0"

gradlePlugin {
    website = "https://github.com/nndwn/changelog-publish-android"
    vcsUrl = "https://github.com/nndwn/changelog-publish-android.git"

    plugins {
        register("changelogPublishPlugin") {
            id = "com.github.nndwn.changelog-publish"
            implementationClass = "com.github.nndwn.changelog.publish.ChangelogPublishPlugin"
            displayName = "Changelog Publish Android"
            description = "Extract Android app metadata, manage CHANGELOG.md, rename release APKs, and generate CI/CD changelog JSON payloads."
            tags = listOf("android", "changelog", "release", "ci-cd", "publishing")
        }
    }
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
}
