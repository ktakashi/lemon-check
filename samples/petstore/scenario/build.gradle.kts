plugins {
    java
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Test against the petstore application
    testImplementation(project(":samples:petstore:app"))

    // BerryCrush JUnit integration
    testImplementation(project(":berrycrush:core"))
    testImplementation(project(":berrycrush:junit"))
    testImplementation(project(":berrycrush:spring"))

    // JUnit
    testImplementation(libs.bundles.junit)
    testImplementation(libs.junit.platform.suite.api)
    testImplementation(libs.junit.platform.launcher)

    // Spring Boot test
    testImplementation(libs.spring.boot.starter.test)

    // Swagger parser for custom test providers
    testImplementation(libs.swagger.parser)
}

tasks.test {
    useJUnitPlatform {
        includeEngines("berrycrush", "junit-jupiter")
    }
}
