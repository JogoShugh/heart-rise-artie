import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "heart"
version = "0.1.0"

repositories {
    mavenCentral()
}

val cucumberVersion = "7.18.0"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("org.junit.platform:junit-platform-suite:1.11.0")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0")
    testImplementation(kotlin("reflect"))
    testImplementation("io.cucumber:cucumber-picocontainer:$cucumberVersion")
    // bdd-gates: the client's architecture boundary as a failing test.
    testImplementation("com.lemonappdev:konsist:0.17.3")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(logBddRun)
}

// Red includes "does not compile": finalize the compile tasks too, so that
// failure is logged. Gradle runs logBddRun once, after whichever ran.
tasks.named("compileKotlin") { finalizedBy(logBddRun) }
tasks.named("compileTestKotlin") { finalizedBy(logBddRun) }

detekt {
    buildUponDefaultConfig = true
    // Existing code predates the gate; the baseline freezes its findings so
    // only new violations fail. Shrink it as code is touched; never grow it.
    baseline = file("detekt-baseline.xml")
}

// bdd-gates: records "BDD_RUN PASS|FAIL" after every test run, including failed ones.
// The log lives at the repo root, one level up from this build. The Cucumber engine
// is discovered through junit-platform.properties, so its XML carries the engine's name.
val logBddRun by tasks.registering {
    group = "verification"
    description = "Appends a BDD_RUN fact to ../.claude/tdd-events.log for the pre-commit TDD-ordering check."
    doLast {
        val resultFile = file("build/test-results/test/TEST-io.cucumber.junit.platform.engine.CucumberTestEngine.xml")
        val logFile = file("../.claude/tdd-events.log")
        val timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        logFile.parentFile.mkdirs()
        // A compile failure stops the build before tests run, which leaves the
        // previous run's XML in place. Log it as red instead of reading stale results.
        val compileFailed = listOf("compileKotlin", "compileTestKotlin")
            .any { tasks.named(it).get().state.failure != null }
        if (compileFailed) {
            logFile.appendText("$timestamp BDD_RUN FAIL module=client reason=compile\n")
            return@doLast
        }
        if (!resultFile.exists()) {
            // No result file means compilation or discovery failed: still red.
            logFile.appendText("$timestamp BDD_RUN FAIL module=client reason=no-results\n")
            return@doLast
        }
        val xml = resultFile.readText()
        fun attr(name: String) = Regex("""$name="(\d+)"""").find(xml)?.groupValues?.get(1) ?: "0"
        val status = if (attr("failures") == "0" && attr("errors") == "0") "PASS" else "FAIL"
        logFile.appendText(
            "$timestamp BDD_RUN $status module=client total=${attr("tests")} failures=${attr("failures")} errors=${attr("errors")}\n",
        )
    }
}

kotlin {
    jvmToolchain(22)
}

// Runs only the @live scenarios, against a running server-spring
// (ARTIE_BASE_URL, default http://localhost:8080). Not part of check:
// the gates' green-to-stop check must not need a server.
val liveTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the @live Cucumber scenarios against a running server-spring."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    // HEART_MAX_RUNG names the rung the server was started at (SPEC §6.11.1).
    // Scenarios that test L2 caching (@needs-L2) are skipped below it.
    val servedRung = System.getenv("HEART_MAX_RUNG") ?: "L2"
    systemProperty("cucumber.filter.tags", if (servedRung == "L2") "@live" else "@live and not @needs-L2")
    environment("ARTIE_BASE_URL", System.getenv("ARTIE_BASE_URL") ?: "http://localhost:8080")
    environment("HEART_MAX_RUNG", servedRung)
    // ARTIE's event log (hack log, 2026-10-10): a JSON Lines file, a URL to POST to, or both.
    listOf("ARTIE_EVENTS", "ARTIE_EVENTS_URL").forEach { name -> System.getenv(name)?.let { environment(name, it) } }
    outputs.upToDateWhen { false }
}


// Runs only the @llm scenarios: a real model behind the Reason step. Needs
// ANTHROPIC_API_KEY in the environment and costs a few API calls; not part of check.
val llmTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the @llm Cucumber scenarios against the Claude API."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    systemProperty("cucumber.filter.tags", "@llm")
    outputs.upToDateWhen { false }
}
