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

repositories {
    mavenCentral()
}

// Youth basketball scoring, a HEART domain server: its domain code and one
// declaration. The HEART library serves everything on the wire.
dependencies {
    implementation("com.artie.heart:heart-spring-boot-starter")
    implementation("com.artie.heart:heart-coap")
    implementation("com.artie.heart:heart-eventsourcing")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

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

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
}

// detekt 1.23.8 is built against Kotlin 2.0.21; keep Spring's BOM off its classpath.
configurations.matching { it.name == "detekt" }.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
    }
}

// bdd-gates: records "BDD_RUN PASS|FAIL" after the domain's own features run.
val logBddRun by tasks.registering {
    group = "verification"
    description = "Appends a BDD_RUN fact to ../../.claude/tdd-events.log for the pre-commit TDD-ordering check."
    doLast {
        val logFile = file("../../.claude/tdd-events.log")
        val timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val compileFailed = listOf("compileKotlin", "compileTestKotlin")
            .any { tasks.named(it).get().state.failure != null }
        if (compileFailed) {
            logFile.appendText("$timestamp BDD_RUN FAIL module=basketball reason=compile\n")
            return@doLast
        }
        if (!tasks.test.get().state.executed) return@doLast
        val resultFile = file("build/test-results/test/TEST-com.artie.basketball.bdd.RunCucumberTest.xml")
        if (!resultFile.exists()) {
            logFile.appendText("$timestamp BDD_RUN FAIL module=basketball reason=no-results\n")
            return@doLast
        }
        val xml = resultFile.readText()
        fun attr(name: String) = Regex("""$name="(\d+)"""").find(xml)?.groupValues?.get(1) ?: "0"
        val status = if (attr("failures") == "0" && attr("errors") == "0") "PASS" else "FAIL"
        logFile.appendText(
            "$timestamp BDD_RUN $status module=basketball total=${attr("tests")} " +
                "failures=${attr("failures")} errors=${attr("errors")}\n",
        )
    }
}

tasks.test { finalizedBy(logBddRun) }
tasks.named("compileKotlin") { finalizedBy(logBddRun) }
tasks.named("compileTestKotlin") { finalizedBy(logBddRun) }
