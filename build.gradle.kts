plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

group = "com.nndwn"
version = "0.1.0"

gradlePlugin {
    plugins {
        register("changelogPublishPlugin") {
            id = "com.nndwn.changelog-publish"
            implementationClass = "com.nndwn.changelog.publish.ChangelogPublishPlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.nndwn"
            artifactId = "changelog-publish-android"
            version = "0.1.0"
        }
    }
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
}
