plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.gitlab.arturbosch.detekt")
}

// heart-coap: the CoAP binding of HEART (SPEC §6.0.1), on Eclipse Californium.
// Its routes are built from the same declarations as every other binding.
dependencies {
    api(project(":heart-core"))
    api(platform("org.springframework.boot:spring-boot-dependencies:3.3.4"))
    api("org.eclipse.californium:californium-core:3.14.0")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    testImplementation(testFixtures(project(":heart-core")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("io.cucumber:cucumber-bom:7.18.0"))
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-spring")
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
