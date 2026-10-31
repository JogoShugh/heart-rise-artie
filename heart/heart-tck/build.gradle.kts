plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.gitlab.arturbosch.detekt")
}

// heart-tck: HEART's conformance features, written once and run by every
// domain server against itself (spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, A5.9).
// A domain adds this as a test dependency, registers a TckFixture bean, and
// runs the features under classpath:heart/tck with the glue com.artie.heart.tck.
// An HTTP binding also runs the features under classpath:heart/binding, with the
// glue com.artie.heart.binding, against the lamp server its tests declare.
dependencies {
    api(project(":heart-core"))
    api(platform("org.springframework.boot:spring-boot-dependencies:3.3.4"))
    api(platform("io.cucumber:cucumber-bom:7.18.0"))
    api("io.cucumber:cucumber-java")
    api("io.cucumber:cucumber-spring")
    api("org.springframework:spring-context")
    api("org.springframework:spring-test")
    api("org.springframework:spring-webflux")
    api("org.junit.jupiter:junit-jupiter-api")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // The binding features (heart/binding) run against the lamp, declared in heart-core's fixtures.
    api(testFixtures(project(":heart-core")))
    // A plain response is checked against the schema the OpenAPI description gives it.
    implementation("com.networknt:json-schema-validator:1.5.9")
    // The HTML a binding serves is read back as a browser would (html.feature).
    implementation("org.jsoup:jsoup:1.23.2")

    // The lamp runs the TCK here: a server that declares only the lamp.
    testImplementation(project(":heart-spring"))
    testImplementation(testFixtures(project(":heart-core")))
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
}

kotlin {
    jvmToolchain(21)
    compilerOptions { javaParameters = true }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
}
