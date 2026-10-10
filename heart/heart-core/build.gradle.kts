import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

// heart-core: the protocol-neutral part of HEART (SPEC §6.0). No Spring,
// no HTTP and no CoAP types; the bindings live in their own modules.
dependencies {
    // RFC 8785 JSON Canonicalization Scheme over Jakarta JSON-P trees (SPEC §6.5).
    // Jakarta JSON Processing 2.1 with its reference implementation, Eclipse Parsson:
    // the pair Spring Boot 3.3 manages, so a server's BOM never splits them.
    implementation("com.apicatalog:titanium-jcs:1.0.0")
    implementation("jakarta.json:jakarta.json-api:2.1.3")
    runtimeOnly("org.eclipse.parsson:parsson:1.1.7")

    testImplementation(platform("io.cucumber:cucumber-bom:7.18.0"))
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
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

// bdd-gates: records "BDD_RUN PASS|FAIL" after every test run, as server-spring does.
// The log lives at the repo root, two levels up from this module.
val logBddRun by tasks.registering {
    group = "verification"
    description = "Appends a BDD_RUN fact to ../../.claude/tdd-events.log for the pre-commit TDD-ordering check."
    doLast {
        val resultFile = file("build/test-results/test/TEST-com.artie.heart.bdd.RunCucumberTest.xml")
        val logFile = file("../../.claude/tdd-events.log")
        val timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        logFile.parentFile.mkdirs()
        val compileFailed = listOf("compileKotlin", "compileTestKotlin")
            .any { tasks.named(it).get().state.failure != null }
        if (compileFailed) {
            logFile.appendText("$timestamp BDD_RUN FAIL module=heart-core reason=compile\n")
            return@doLast
        }
        // A server that includes this build compiles heart-core without testing it.
        // Then the result file is an older run's, so there is nothing new to log.
        if (!gradle.taskGraph.hasTask(tasks.test.get())) return@doLast
        if (!resultFile.exists()) {
            logFile.appendText("$timestamp BDD_RUN FAIL module=heart-core reason=no-results\n")
            return@doLast
        }
        val xml = resultFile.readText()
        fun attr(name: String) = Regex("""$name="(\d+)"""").find(xml)?.groupValues?.get(1) ?: "0"
        val status = if (attr("failures") == "0" && attr("errors") == "0") "PASS" else "FAIL"
        logFile.appendText(
            "$timestamp BDD_RUN $status module=heart-core total=${attr("tests")} failures=${attr("failures")} errors=${attr("errors")}\n",
        )
    }
}

tasks.test {
    finalizedBy(logBddRun)
}
tasks.named("compileKotlin") { finalizedBy(logBddRun) }
tasks.named("compileTestKotlin") { finalizedBy(logBddRun) }
