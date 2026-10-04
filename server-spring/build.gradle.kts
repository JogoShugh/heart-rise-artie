plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
}

group = "com.artie"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Reactive web stack -- required for suspend fun controller support
    // and for Spring Data R2DBC's coroutine-native repositories.
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // CORRECTED: the artifact was relocated from io.r2dbc:r2dbc-postgresql
    // to org.postgresql:r2dbc-postgresql -- confirmed via live search of
    // Maven Central, not a guess this time. The old io.r2dbc group only
    // has releases through 0.8.13.RELEASE (2022) and hasn't published
    // anything since; that's why my first guessed version didn't exist
    // there, and why dropping the version left it empty -- Spring Boot's
    // BOM manages the CURRENT (org.postgresql) coordinates, not the old
    // relocated ones. No explicit version here either, same reasoning
    // as before: let Spring Boot's dependency-management plugin supply
    // a known-compatible one now that the coordinates are correct.
    implementation("org.postgresql:r2dbc-postgresql")

    // Kotlin <-> Spring reactive bridge. This is what lets a WebFlux
    // controller method be declared `suspend fun` and have Spring
    // transparently adapt it to/from Mono/Flux under the hood, and
    // what CoroutineCrudRepository is built on for R2DBC.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    runtimeOnly("org.postgresql:postgresql") // JDBC driver, only used by Flyway below
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // RFC 8785 JSON Canonicalization Scheme, used by JsonSchemaDialectSupport
    // to compute Layer 2/3 content addresses per SPEC.md §6.5 -- previously
    // this used plain Jackson serialization relying on Map insertion order,
    // which is deterministic in practice here but not actually canonical
    // (no recursive key-sorting, no JCS number normalization). titanium-jcs
    // operates on Jakarta JSON-P's JsonValue tree, not plain Kotlin objects
    // or Jackson's, so we bring both the API (titanium-jcs depends on it as
    // `provided`, which Maven does not propagate) and a runtime
    // implementation to build/parse JsonValue trees.
    implementation("com.apicatalog:titanium-jcs:1.0.0")
    implementation("jakarta.json:jakarta.json-api:2.0.1")
    runtimeOnly("org.glassfish:jakarta.json:2.0.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
