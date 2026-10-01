import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "2.4.20"
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.2.1"
}

group = "io.github.nndwn"
version = "0.2.4"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

gradlePlugin {
    website = "https://github.com/nndwn/changelog-publish-android"
    vcsUrl = "https://github.com/nndwn/changelog-publish-android.git"

    plugins {
        register("changelogPublishPlugin") {
            id = "io.github.nndwn.changelog-publish"
            implementationClass = "io.github.nndwn.changelog.publish.ChangelogPublishPlugin"
            displayName = "Changelog Publish Android"
            description = "Extract Android app metadata, manage CHANGELOG.md, rename release APKs, and generate CI/CD changelog JSON payloads."
            tags = listOf("android", "changelog", "release", "ci-cd", "publishing")
        }
    }
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
}
