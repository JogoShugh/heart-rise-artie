plugins {
    `java-library`
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

// heart-http4k: the HTTP binding of HEART on http4k, a second binding beside
// heart-spring. A server is a function from request to response, built from the
// resource types a server declares; it needs no Spring context or web stack.
dependencies {
    api(project(":heart-core"))
    api(platform("org.http4k:http4k-bom:5.47.0.0"))
    api("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-jetty")
    // BCrypt only, for Basic credentials. The jar needs no Spring at run time.
    implementation("org.springframework.security:spring-security-crypto:6.3.3")
    // Swagger UI for /docs, served from this jar. Keep in step with SwaggerUi.VERSION.
    implementation("org.webjars:swagger-ui:5.17.14")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // The conformance features run against this binding, over a real port.
    testImplementation(project(":heart-tck"))
    testImplementation(testFixtures(project(":heart-core")))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.4"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("io.projectreactor.netty:reactor-netty-http")
    testImplementation(platform("io.cucumber:cucumber-bom:7.18.0"))
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
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
