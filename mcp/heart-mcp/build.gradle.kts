import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    kotlin("jvm")
    `java-library`
    id("io.gitlab.arturbosch.detekt")
}

val cucumberVersion = "7.18.0"
val mcpSdkVersion = "0.8.1"

dependencies {
    // ARTIE, the generic HEART client: Sync and Act.
    api("heart:artie:0.1.0")
    // The official MCP Kotlin SDK: the host's model is the Reason step.
    api("io.modelcontextprotocol:kotlin-sdk-server:$mcpSdkVersion")

    testImplementation("io.modelcontextprotocol:kotlin-sdk-client:$mcpSdkVersion")
    testImplementation(kotlin("test"))
    testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-picocontainer:$cucumberVersion")
    testImplementation("org.junit.platform:junit-platform-suite:1.11.0")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.16")
}

kotlin {
    jvmToolchain(22)
}

detekt {
    buildUponDefaultConfig = true
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(logBddRun)
}

// Red includes "does not compile": finalize the compile tasks too, so that
// failure is logged. Gradle runs logBddRun once, after whichever ran.
tasks.named("compileKotlin") { finalizedBy(logBddRun) }
tasks.named("compileTestKotlin") { finalizedBy(logBddRun) }

// Records "BDD_RUN PASS|FAIL" after every test run, including failed ones, for the
// pre-commit TDD-ordering check. The log lives at the repo root, two levels up.
val logBddRun by tasks.registering {
    group = "verification"
    description = "Appends a BDD_RUN fact to ../../.claude/tdd-events.log for the pre-commit TDD-ordering check."
    doLast {
        val ran = listOf("liveTest", "test").firstOrNull { tasks.named(it).get().state.executed } ?: "test"
        val resultFile = file("build/test-results/$ran/TEST-io.cucumber.junit.platform.engine.CucumberTestEngine.xml")
        val logFile = file("../../.claude/tdd-events.log")
        val timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        logFile.parentFile.mkdirs()
        val compileFailed = listOf("compileKotlin", "compileTestKotlin")
            .any { tasks.named(it).get().state.failure != null }
        if (compileFailed) {
            logFile.appendText("$timestamp BDD_RUN FAIL module=heart-mcp reason=compile\n")
            return@doLast
        }
        if (!resultFile.exists()) {
            logFile.appendText("$timestamp BDD_RUN FAIL module=heart-mcp reason=no-results\n")
            return@doLast
        }
        val xml = resultFile.readText()
        fun attr(name: String) = Regex("""$name="(\d+)"""").find(xml)?.groupValues?.get(1) ?: "0"
        val status = if (attr("failures") == "0" && attr("errors") == "0") "PASS" else "FAIL"
        logFile.appendText(
            "$timestamp BDD_RUN $status module=heart-mcp task=$ran total=${attr("tests")} " +
                "failures=${attr("failures")} errors=${attr("errors")}\n",
        )
    }
}

// Runs every scenario, the @live ones too, against a running garden server
// (ARTIE_BASE_URL, default http://localhost:8080). scripts/mcp-check.sh starts one.
val liveTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs every heart-mcp scenario, @live included, against a running garden server."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    systemProperty("cucumber.filter.tags", "not @wip")
    environment("ARTIE_BASE_URL", System.getenv("ARTIE_BASE_URL") ?: "http://localhost:8080")
    environment(
        "ARTIE_RESET",
        System.getenv("ARTIE_RESET") ?: rootProject.file("../server-spring/reset-db.sh").absolutePath,
    )
    outputs.upToDateWhen { false }
    finalizedBy(logBddRun)
}
