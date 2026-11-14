plugins {
    `java-library`
    `java-test-fixtures`
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

// heart-core: the protocol-neutral part of HEART (SPEC §6.0). No Spring,
// no HTTP and no CoAP types; the bindings live in their own modules.
// The test fixtures hold the lamp, a resource type every module tests against.
dependencies {
    // RFC 8785 JSON Canonicalization Scheme over Jakarta JSON-P trees (SPEC §6.5).
    // Jakarta JSON Processing 2.1 with its reference implementation, Eclipse Parsson:
    // the pair Spring Boot 3.3 manages, so a server's BOM never splits them.
    implementation("com.apicatalog:titanium-jcs:1.0.0")
    implementation("jakarta.json:jakarta.json-api:2.1.3")
    runtimeOnly("org.eclipse.parsson:parsson:1.1.7")

    // An act's schema is generated from its input class (Jakarta Validation for the
    // constraints, OpenAPI @Schema for the words), and bodies are checked against it.
    // Jackson 2 lines, as Spring Boot 3.3 manages.
    api("jakarta.validation:jakarta.validation-api:3.0.2")
    api("io.swagger.core.v3:swagger-annotations:2.2.22")
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.github.victools:jsonschema-generator:4.38.0")
    implementation("com.github.victools:jsonschema-module-jakarta-validation:4.38.0")
    implementation("com.github.victools:jsonschema-module-swagger-2:4.38.0")
    implementation("com.networknt:json-schema-validator:1.5.9")

    testImplementation(platform("io.cucumber:cucumber-bom:7.18.0"))
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-picocontainer")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    // The HTML a resource renders is read back as a browser would (html.feature).
    testImplementation("org.jsoup:jsoup:1.23.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
}
