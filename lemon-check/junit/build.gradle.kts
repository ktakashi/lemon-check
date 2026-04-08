plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // Core library dependency
    implementation(project(":lemon-check:core"))

    // JUnit 5
    implementation(libs.bundles.junit)

    // JUnit Platform Engine API
    api(libs.bundles.junit.platform)
    api(libs.junit.platform.suite.api)

    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
}
