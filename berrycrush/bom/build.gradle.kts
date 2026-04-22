plugins {
    `java-platform`
    `maven-publish`
    signing
    id("berrycrush.maven-publish")
}

// Allow dependencies to be declared with specific versions
javaPlatform {
    allowDependencies()
}

// Read versions from version catalog
val berrycrushVersion = project.version.toString()

// Access version catalog
val catalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
val swaggerParserVersion = catalog.findVersion("swagger-parser").get().requiredVersion
val jsonPathVersion = catalog.findVersion("json-path").get().requiredVersion
val jsonSchemaValidatorVersion = catalog.findVersion("json-schema-validator").get().requiredVersion
val jacksonVersion = catalog.findVersion("jackson").get().requiredVersion
val junitVersion = catalog.findVersion("junit").get().requiredVersion
val junitPlatformVersion = catalog.findVersion("junit-platform").get().requiredVersion
val springBootVersion = catalog.findVersion("spring-boot").get().requiredVersion

dependencies {
    constraints {
        // BerryCrush modules
        api("org.berrycrush:core:$berrycrushVersion")
        api("org.berrycrush:junit:$berrycrushVersion")
        api("org.berrycrush:spring:$berrycrushVersion")
        
        // Core dependencies - versions can be overridden
        api("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion")
        api("com.jayway.jsonpath:json-path:$jsonPathVersion")
        api("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
        api("tools.jackson.module:jackson-module-kotlin:$jacksonVersion")
        
        // JUnit dependencies
        api("org.junit.jupiter:junit-jupiter-api:$junitVersion")
        api("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
        api("org.junit.jupiter:junit-jupiter-params:$junitVersion")
        api("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
        api("org.junit.platform:junit-platform-engine:$junitPlatformVersion")
        api("org.junit.platform:junit-platform-commons:$junitPlatformVersion")
        api("org.junit.platform:junit-platform-suite-api:$junitPlatformVersion")
        
        // Spring Boot dependencies
        api("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
        api("org.springframework.boot:spring-boot-starter-data-jpa:$springBootVersion")
        api("org.springframework.boot:spring-boot-starter-validation:$springBootVersion")
        api("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    }
}

// Maven publication configuration
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["javaPlatform"])

            pom {
                name.set("BerryCrush BOM")
                description.set("Bill of Materials for BerryCrush - OpenAPI-driven BDD testing framework")
                url.set("https://github.com/ktakashi/berrycrush")

                // Add properties section for version overriding
                withXml {
                    val root = asNode()
                    val propertiesNode = root.appendNode("properties")
                    propertiesNode.appendNode("berrycrush.version", berrycrushVersion)
                    propertiesNode.appendNode("swagger-parser.version", swaggerParserVersion)
                    propertiesNode.appendNode("json-path.version", jsonPathVersion)
                    propertiesNode.appendNode("json-schema-validator.version", jsonSchemaValidatorVersion)
                    propertiesNode.appendNode("jackson.version", jacksonVersion)
                    propertiesNode.appendNode("junit-jupiter.version", junitVersion)
                    propertiesNode.appendNode("junit-platform.version", junitPlatformVersion)
                    propertiesNode.appendNode("spring-boot.version", springBootVersion)
                    
                    // Update dependencyManagement versions to use property references
                    val depMgmt = root.get("dependencyManagement") as? groovy.util.NodeList
                    depMgmt?.let {
                        val deps = (it.firstOrNull() as? groovy.util.Node)?.get("dependencies") as? groovy.util.NodeList
                        deps?.let { depsList ->
                            (depsList.firstOrNull() as? groovy.util.Node)?.children()?.forEach { depNode ->
                                if (depNode is groovy.util.Node) {
                                    val groupId = (depNode.get("groupId") as? groovy.util.NodeList)?.text()
                                    val artifactId = (depNode.get("artifactId") as? groovy.util.NodeList)?.text()
                                    val versionNode = depNode.get("version") as? groovy.util.NodeList
                                    
                                    versionNode?.let { vn ->
                                        val node = vn.firstOrNull() as? groovy.util.Node
                                        node?.setValue(when {
                                            groupId == "org.berrycrush" -> "\${berrycrush.version}"
                                            groupId == "io.swagger.parser.v3" -> "\${swagger-parser.version}"
                                            artifactId == "json-path" -> "\${json-path.version}"
                                            artifactId == "json-schema-validator" -> "\${json-schema-validator.version}"
                                            groupId?.startsWith("tools.jackson") == true -> "\${jackson.version}"
                                            groupId == "org.junit.jupiter" -> "\${junit-jupiter.version}"
                                            groupId == "org.junit.platform" -> "\${junit-platform.version}"
                                            groupId == "org.springframework.boot" -> "\${spring-boot.version}"
                                            else -> node.text()
                                        })
                                    }
                                }
                            }
                        }
                    }
                }

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
