import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
    id("artie.kotlin-quality")
    id("bddgates.bdd-log")
}

group = "com.artie"
version = "0.1.0"

repositories {
    mavenCentral()
}

// Youth baseball scoring (8U coach pitch), a HEART domain server: its domain code and one
// declaration. The HEART library serves everything on the wire.
dependencies {
    implementation("com.artie.heart:heart-spring-boot-starter")
    implementation("com.artie.heart:heart-coap")
    implementation("com.artie.heart:heart-eventsourcing")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("com.artie.heart:heart-tck")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("io.cucumber:cucumber-bom:7.18.0"))
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-spring")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:r2dbc")
}

// Buster, the chaos filter: only under -Pbuster=on (settings.gradle.kts), and only
// at bootRun. developmentOnly keeps it out of the boot jar; it switches on with
// the buster profile. Buster compiles plugin scripts at runtime, so the Kotlin
// it runs on must be this build's Kotlin.
if (gradle.includedBuilds.any { it.name == "buster" }) {
    dependencies { developmentOnly("com.artie:buster") }
}
configurations.matching { it.name in setOf("developmentOnly", "runtimeClasspath", "productionRuntimeClasspath") }.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion(getKotlinPluginVersion())
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// detekt 1.23.8 is built against Kotlin 2.0.21; keep Spring's BOM off its classpath.
configurations.matching { it.name == "detekt" }.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
    }
}

