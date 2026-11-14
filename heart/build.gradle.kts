import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    kotlin("jvm") version "2.0.20" apply false
    kotlin("plugin.spring") version "2.0.20" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

subprojects {
    group = "com.artie.heart"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }

    // Every module publishes as com.artie.heart:<module>, so a server outside
    // this repo can depend on it from mavenLocal (./gradlew publishToMavenLocal).
    apply(plugin = "maven-publish")
    plugins.withId("java") {
        configure<JavaPluginExtension> { withSourcesJar() }
        configure<PublishingExtension> {
            publications { create<MavenPublication>("heart") { from(components["java"]) } }
        }
    }

    // bdd-gates: every module records "BDD_RUN PASS|FAIL" after its tests, as
    // server-spring does. The log lives at the repo root, two levels up from a module.
    plugins.withId("org.jetbrains.kotlin.jvm") {
        val module = name
        val logBddRun = tasks.register("logBddRun") {
            group = "verification"
            description = "Appends a BDD_RUN fact to ../../.claude/tdd-events.log for the pre-commit TDD-ordering check."
            doLast {
                val logFile = file("../../.claude/tdd-events.log")
                val timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
                logFile.parentFile.mkdirs()
                val compileFailed = listOf("compileKotlin", "compileTestKotlin")
                    .any { tasks.named(it).get().state.failure != null }
                if (compileFailed) {
                    logFile.appendText("$timestamp BDD_RUN FAIL module=$module reason=compile\n")
                    return@doLast
                }
                // When the tests did not run (a server compiling this module, or a build
                // that stopped before them), the result file is an older run's: nothing to log.
                if (!tasks.named("test").get().state.executed) return@doLast
                // Every runner's results, summed: a module may run its features in more than one runner
                // (the gateway's own features, its GitHub TCK, the lamp TCK), and a failure in any is a failure.
                val resultFiles = fileTree("build/test-results/test") { include("TEST-*.xml") }.files
                if (resultFiles.isEmpty()) {
                    logFile.appendText("$timestamp BDD_RUN FAIL module=$module reason=no-results\n")
                    return@doLast
                }
                val xmls = resultFiles.map { it.readText() }
                fun attr(name: String) = xmls.sumOf { xml ->
                    Regex("""$name="(\d+)"""").find(xml)?.groupValues?.get(1)?.toInt() ?: 0
                }.toString()
                val status = if (attr("failures") == "0" && attr("errors") == "0") "PASS" else "FAIL"
                logFile.appendText(
                    "$timestamp BDD_RUN $status module=$module total=${attr("tests")} " +
                        "failures=${attr("failures")} errors=${attr("errors")}\n",
                )
            }
        }
        tasks.named("test") { finalizedBy(logBddRun) }
        tasks.named("compileKotlin") { finalizedBy(logBddRun) }
        tasks.named("compileTestKotlin") { finalizedBy(logBddRun) }
    }
}
