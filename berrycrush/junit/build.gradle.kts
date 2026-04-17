plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
    `maven-publish`
    signing
    id("berrycrush.maven-publish")
}

dependencies {
    // Core library dependency
    implementation(project(":berrycrush:core"))

    // JUnit 5
    implementation(libs.bundles.junit)

    // JUnit Platform Engine API
    api(libs.bundles.junit.platform)
    api(libs.junit.platform.suite.api)

    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
    dokkaPublications.javadoc {
        outputDirectory.set(layout.buildDirectory.dir("dokka/javadoc"))
    }
}

// Create source and javadoc jars
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    dependsOn(tasks.dokkaGeneratePublicationJavadoc)
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
}

// Maven publishing configuration
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("BerryCrush JUnit")
                description.set("JUnit 5 integration for BerryCrush API testing library")
                url.set("https://github.com/ktakashi/berrycrush")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("ktakashi")
                        name.set("Takashi Kato")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/ktakashi/berrycrush.git")
                    developerConnection.set("scm:git:ssh://github.com:ktakashi/berrycrush.git")
                    url.set("https://github.com/ktakashi/berrycrush/tree/main")
                }
            }
        }
    }
}
