plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.gitlab.arturbosch.detekt")
}

// heart-spring: the HTTP binding of HEART on Spring WebFlux. Its routes are
// built from the resource types a server declares; a server adds no controllers.
dependencies {
    api(project(":heart-core"))
    api(platform("org.springframework.boot:spring-boot-dependencies:3.3.4"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-webflux")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    // BCrypt only, for Basic credentials; not Spring Security's filter chain.
    implementation("org.springframework.security:spring-security-crypto")
    // Swagger UI for /docs, served from this jar (Spring Boot maps /webjars/**). Keep the
    // version in step with SwaggerUiPage.VERSION.
    implementation("org.webjars:swagger-ui:5.17.14")

    testImplementation(testFixtures(project(":heart-core")))
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("io.cucumber:cucumber-bom:7.18.0"))
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-spring")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
    // A plain response is checked against the schema the OpenAPI description gives it.
    testImplementation("com.networknt:json-schema-validator:1.5.9")
}

kotlin {
    jvmToolchain(21)
    // Spring binds @PathVariable and @RequestParam by parameter name; the Spring
    // Boot plugin sets this for applications, so a library sets it itself.
    compilerOptions { javaParameters = true }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
}
