plugins {
    kotlin("jvm") version "2.0.20" apply false
    kotlin("plugin.spring") version "2.0.20" apply false
    id("artie.coverage-merge")
    id("bddgates.bdd-log") apply false
}

// One merged coverage report for the whole build (build/reports/kover/report.xml here): a line counts as
// covered whichever module's tests ran it, so code other modules exercise (the TCK, the sims) is credited.
repositories { mavenCentral() }
dependencies {
    listOf(
        "heart-core", "heart-http4k", "heart-gateway", "heart-spring", "heart-tck",
        "heart-eventsourcing", "heart-sims", "heart-coap",
    ).forEach { kover(project(":$it")) }
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

    // bdd-gates: every Kotlin module records each test run, scenario by scenario (build-logic: bddgates.bdd-log).
    plugins.withId("org.jetbrains.kotlin.jvm") { apply(plugin = "bddgates.bdd-log") }
}
