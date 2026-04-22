pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("com.gradle.develocity") version "4.0.2"
}

develocity {
	buildScan {
		termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
		termsOfUseAgree = "yes"
		publishing.onlyIf { System.getenv("CI") != null }
	}
}

rootProject.name = "berrycrush"

include("berrycrush:bom")
include("berrycrush:core")
include("berrycrush:junit")
include("berrycrush:spring")
include("berrycrush:doc")
include("samples:petstore:app")
include("samples:petstore:scenario")
include("samples:petstore:kotlin-dsl")
