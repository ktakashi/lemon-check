plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<Test> {
    useJUnitPlatform {
        includeEngines("lemoncheck")
    }
    testLogging {
        showStandardStreams = true
    }
}

dependencies {
    // Spring Boot
    implementation(libs.bundles.spring.boot)
    runtimeOnly(libs.h2.database)

    // Testing - Spring Boot starter-test includes JUnit 5
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":lemon-check:core"))
    testImplementation(project(":lemon-check:junit"))
    testImplementation(project(":lemon-check:spring"))
    // JUnit Platform suite API for @Suite annotations
    testImplementation(libs.junit.platform.suite.api)
    testImplementation(libs.junit.platform.launcher)
}
