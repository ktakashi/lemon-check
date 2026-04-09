plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // Core and JUnit module dependencies
    implementation(project(":lemon-check:core"))
    implementation(project(":lemon-check:junit"))

    // Spring Boot Test for TestContextManager
    implementation(libs.spring.boot.starter.test)

    // JUnit Platform for BindingsProvider SPI
    implementation(libs.bundles.junit.platform)

    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.bundles.spring.boot)
}
