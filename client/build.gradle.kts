// The client: a multi-project build (spec/PLAN-ARTIE-SCRIPTS.md). client/artie is ARTIE;
// the script DSL, its test add-on and the artie CLI join it as subprojects.
plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    id("artie.coverage-merge")
    id("bddgates.bdd-log") apply false
}

// One merged coverage report for the client (build/reports/kover/report.xml here): a line counts as
// covered whichever module's tests ran it, so heart-script is credited for what artie-cli's scenarios run.
repositories { mavenCentral() }
dependencies {
    listOf(
        "artie", "artie-cli", "artie-release", "artie-minecraft",
        "heart-script", "heart-script-host", "heart-script-test",
    ).forEach { kover(project(":$it")) }
}

// bdd-gates: every Kotlin module records each test run, scenario by scenario (build-logic: bddgates.bdd-log).
subprojects { plugins.withId("org.jetbrains.kotlin.jvm") { apply(plugin = "bddgates.bdd-log") } }
