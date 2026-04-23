plugins {
    kotlin("jvm")
}

dependencies {
    // Test against the petstore application
    testImplementation(project(":samples:petstore:app"))

    // BerryCrush core with Kotlin DSL
    testImplementation(project(":berrycrush:core"))

    // BerryCrush JUnit extension
    testImplementation(project(":berrycrush:junit"))

    // BerryCrush Spring integration (for @ScenarioTest with @LocalServerPort)
    testImplementation(project(":berrycrush:spring"))

    // JUnit
    testImplementation(libs.bundles.junit)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.kotlin.test.junit5)

    // Spring Boot test for running the app
    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
