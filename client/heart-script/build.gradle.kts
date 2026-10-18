plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-test-fixtures`
    id("io.gitlab.arturbosch.detekt")
}

// heart-script: the HEART script DSL (spec/PLAN-ARTIE-SCRIPTS.md, "The DSL") and the contract any
// HEART client implements to run it. It knows HEART's core concepts only: no protocol, no client.
group = "heart"
version = "0.1.0"

repositories {
    mavenCentral()
}

val cucumberVersion = "7.18.0"

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // The features' steps and scripted service, shared with add-ons' features (heart-script-test).
    testFixturesApi(kotlin("test"))
    testFixturesApi("io.cucumber:cucumber-java:$cucumberVersion")
    // The features run real scripts, through Kotlin's script engine.
    testFixturesImplementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:2.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-picocontainer:$cucumberVersion")
}

tasks.test {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
}

kotlin {
    jvmToolchain(22)
}
