import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
}

// ARTIE: the HEART client (SPEC §6.3, §6.8), its features and its tests.

group = "heart"
version = "0.1.0"

repositories {
    mavenCentral()
}

val cucumberVersion = "7.18.0"

dependencies {
    // The script DSL's contract, which ARTIE implements (heart.script.Navigator).
    api(project(":heart-script"))
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // SPEC §6.0.1: the CoAP binding (RFC 7252), for coap: URIs.
    api("org.eclipse.californium:californium-core:3.14.0")
    // coaps: DTLS with a pre-shared key.
    api("org.eclipse.californium:scandium:3.14.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("org.junit.platform:junit-platform-suite:1.11.0")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0")
    testImplementation(kotlin("reflect"))
    testImplementation("io.cucumber:cucumber-picocontainer:$cucumberVersion")
    // The GitHub gateway and a small GitHub, in process (github-gateway.feature).
    testImplementation(testFixtures("com.artie.heart:heart-gateway"))
    // script-github.feature runs real scripts, through Kotlin's script engine.
    testImplementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:2.3.0")
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
    description = "Appends a BDD_RUN fact to ../../.claude/tdd-events.log for the pre-commit TDD-ordering check."
    doLast {
        val resultFile = file("build/test-results/test/TEST-io.cucumber.junit.platform.engine.CucumberTestEngine.xml")
        val logFile = file("../../.claude/tdd-events.log")
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
    // @llm scenarios call a real model; only llmTest runs them.
    val servedRung = System.getenv("HEART_MAX_RUNG") ?: "L2"
    systemProperty(
        "cucumber.filter.tags",
        if (servedRung == "L2") "@live and not @llm" else "@live and not @llm and not @needs-L2",
    )
    liveEnvironment(servedRung)
    outputs.upToDateWhen { false }
}

// Runs only the @auth-live scenarios: each credential source alone, against the
// garden, the basketball service and a Vault dev server that scripts/auth-check.sh
// starts with credentials made for the run (passed in ARTIE_CHECK_* variables).
val authLiveTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the @auth-live Cucumber scenarios against the services scripts/auth-check.sh starts."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    systemProperty("cucumber.filter.tags", "@auth-live")
    liveEnvironment(System.getenv("HEART_MAX_RUNG") ?: "L2")
    System.getenv().filterKeys { it.startsWith("ARTIE_CHECK_") }.forEach { (name, value) -> environment(name, value) }
    outputs.upToDateWhen { false }
}

// Runs only the @explore scenarios: ARTIE explores the running HEART server at
// ARTIE_BASE_URL from its bookmark, with the domain's samples (ARTIE_SAMPLES) and
// reset command (ARTIE_RESET). The same task explores any domain; Buster
// campaigns use it as their client (scripts/buster-campaign.sh).
val exploreTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Explores a running HEART server from its bookmark, with a domain's samples."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    systemProperty("cucumber.filter.tags", "@explore")
    liveEnvironment(System.getenv("HEART_MAX_RUNG") ?: "L2")
    System.getenv("ARTIE_SAMPLES")?.let { environment("ARTIE_SAMPLES", it) }
    outputs.upToDateWhen { false }
}

/** What a scenario against the running server needs: where it is, how to reset it, where events go. */
fun Test.liveEnvironment(servedRung: String) {
    environment("ARTIE_BASE_URL", System.getenv("ARTIE_BASE_URL") ?: "http://localhost:8080")
    environment("HEART_MAX_RUNG", servedRung)
    // Each @live scenario starts from the seed data (live-server.feature, Background).
    environment("ARTIE_RESET", System.getenv("ARTIE_RESET") ?: file("../../server-spring/reset-db.sh").absolutePath)
    // ARTIE's event log (hack log, 2026-10-10): a JSON Lines file, a URL to POST to, or both.
    listOf("ARTIE_EVENTS", "ARTIE_EVENTS_URL", "ARTIE_COAP_PORT").forEach { name ->
        System.getenv(name)?.let { environment(name, it) }
    }
}

// Runs only the @llm scenarios: a real model behind the Reason step, so it
// costs a few API calls and is never part of check. The @llm @live ones need
// a running server, as liveTest does. The key comes from ANTHROPIC_API_KEY, or
// else from ~/.anthropic/api_key; it is passed to the tests, never printed.
val llmTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the @llm Cucumber scenarios against the Claude API."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    systemProperty("cucumber.filter.tags", "@llm")
    liveEnvironment(System.getenv("HEART_MAX_RUNG") ?: "L2")
    val keyFile = File(System.getProperty("user.home"), ".anthropic/api_key")
    (System.getenv("ANTHROPIC_API_KEY") ?: keyFile.takeIf { it.isFile }?.readText()?.trim())
        ?.let { environment("ANTHROPIC_API_KEY", it) }
    outputs.upToDateWhen { false }
}
