
plugins {
    kotlin("jvm")
    `java-library`
    id("artie.kotlin-quality")
    id("bddgates.bdd-log")
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

tasks.test {
    useJUnitPlatform()
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
}
