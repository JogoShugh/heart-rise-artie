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
