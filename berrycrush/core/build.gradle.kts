plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
    `maven-publish`
    signing
    id("berrycrush.maven-publish")
}

dependencies {
    // OpenAPI parsing
    implementation(libs.swagger.parser)

    // JSON processing
    implementation(libs.json.path)
    implementation(libs.json.schema.validator)
    implementation(libs.jackson.kotlin)

    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.bundles.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
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
                name.set("BerryCrush Core")
                description.set("Core library for OpenAPI-driven BDD-style API testing")
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
