// The client: a multi-project build (spec/PLAN-ARTIE-SCRIPTS.md). client/artie is ARTIE;
// the script DSL, its test add-on and the artie CLI join it as subprojects.
plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

// bdd-gates: the script modules record "BDD_RUN PASS|FAIL" after every test run, as client/artie does,
// in the repo root's log, one level up from this build. client/artie has its own.
configure(subprojects.filter { it.name != "artie" }) {
    afterEvaluate {
        val logBddRun = tasks.register("logBddRun") {
            group = "verification"
            doLast {
                val resultFile = file("build/test-results/test/TEST-io.cucumber.junit.platform.engine.CucumberTestEngine.xml")
                val logFile = rootProject.file("../.claude/tdd-events.log")
                val timestamp = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                logFile.parentFile.mkdirs()
                val compileFailed = listOf("compileKotlin", "compileTestKotlin")
                    .any { tasks.named(it).get().state.failure != null }
                if (compileFailed) {
                    logFile.appendText("$timestamp BDD_RUN FAIL module=client reason=compile\n")
                    return@doLast
                }
                if (!resultFile.exists()) {
                    logFile.appendText("$timestamp BDD_RUN FAIL module=client reason=no-results\n")
                    return@doLast
                }
                val xml = resultFile.readText()
                fun attr(n: String) = Regex("""$n="(\d+)"""").find(xml)?.groupValues?.get(1) ?: "0"
                val status = if (attr("failures") == "0" && attr("errors") == "0") "PASS" else "FAIL"
                logFile.appendText(
                    "$timestamp BDD_RUN $status module=client total=${attr("tests")} failures=${attr("failures")} errors=${attr("errors")}\n",
                )
            }
        }
        tasks.named("test") { finalizedBy(logBddRun) }
        tasks.named("compileKotlin") { finalizedBy(logBddRun) }
        tasks.named("compileTestKotlin") { finalizedBy(logBddRun) }
    }
}
