plugins {
    kotlin("jvm") version "2.3.20" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jetbrains.dokka-javadoc") version "2.2.0"
    id("org.owasp.dependencycheck") version "12.1.1"
    kotlin("plugin.spring") version "2.3.20"
}

// OWASP Dependency Check configuration
dependencyCheck {
    // Scan all subprojects
    scanConfigurations = listOf("runtimeClasspath", "compileClasspath")
    // Output formats
    formats = listOf("HTML", "JSON")
    // Output directory
    outputDirectory = "build/reports/dependency-check"
    // Skip if NVD API key is not configured (optional for local development)
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: ""
    }
}

allprojects {
    group = "org.berrycrush"
    version = version

    repositories {
        mavenCentral()
    }
}

subprojects {
    // Skip BOM module - it uses java-platform which conflicts with java plugins
    if (name == "bom") return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}
dokka {
    dokkaPublications.html {
        outputDirectory.set(file("berrycrush/doc/build/dokka"))
    }
}