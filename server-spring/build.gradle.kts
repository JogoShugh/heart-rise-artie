import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
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

    // bdd-gates: Gherkin scenarios drive the SPEC conformance work. Cucumber 7
    // fails undefined steps by default; keep it that way so a new scenario goes red.
    testImplementation(platform("io.cucumber:cucumber-bom:7.18.0"))
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-spring")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
    // EventStore against real Postgres: R2DBC insert/update rules and the
    // UNIQUE constraint only show up on a real database. Skipped without Docker.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:r2dbc")

    // bdd-gates: the domain/web boundary as a failing test.
    testImplementation("com.lemonappdev:konsist:0.17.3")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
    // Existing code predates the gate; the baseline freezes its findings so
    // only new violations fail. Shrink it as code is touched; never grow it.
    baseline = file("detekt-baseline.xml")
}

// detekt 1.23.8 is built against Kotlin 2.0.21. Spring's dependency-management
// plugin would otherwise force the project's Kotlin version onto detekt's
// own classpath and break it.
configurations.matching { it.name == "detekt" }.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
    }
}

// bdd-gates: records "BDD_RUN PASS|FAIL" after every test run, including failed ones.
// The log lives at the repo root, one level up from this build.
val logBddRun by tasks.registering {
    group = "verification"
    description = "Appends a BDD_RUN fact to ../.claude/tdd-events.log for the pre-commit TDD-ordering check."
    doLast {
        val resultFile = file("build/test-results/test/TEST-com.artie.gardenbed.bdd.RunCucumberTest.xml")
        val logFile = file("../.claude/tdd-events.log")
        val timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        logFile.parentFile.mkdirs()
        // A compile failure stops the build before tests run, which leaves the
        // previous run's XML in place. Log it as red instead of reading stale results.
        val compileFailed = listOf("compileKotlin", "compileTestKotlin")
            .any { tasks.named(it).get().state.failure != null }
        if (compileFailed) {
            logFile.appendText("$timestamp BDD_RUN FAIL module=server-spring reason=compile\n")
            return@doLast
        }
        if (!resultFile.exists()) {
            // No result file means compilation or discovery failed: still red.
            logFile.appendText("$timestamp BDD_RUN FAIL module=server-spring reason=no-results\n")
            return@doLast
        }
        val xml = resultFile.readText()
        fun attr(name: String) = Regex("""$name="(\d+)"""").find(xml)?.groupValues?.get(1) ?: "0"
        val status = if (attr("failures") == "0" && attr("errors") == "0") "PASS" else "FAIL"
        logFile.appendText(
            "$timestamp BDD_RUN $status module=server-spring total=${attr("tests")} failures=${attr("failures")} errors=${attr("errors")}\n",
        )
    }
}

tasks.test {
    finalizedBy(logBddRun)
}

// Red includes "does not compile": finalize the compile tasks too, so that
// failure is logged. Gradle runs logBddRun once, after whichever ran.
tasks.named("compileKotlin") { finalizedBy(logBddRun) }
tasks.named("compileTestKotlin") { finalizedBy(logBddRun) }
