plugins {
    kotlin("jvm")
    application
    id("artie.kotlin-quality")
}

dependencies {
    implementation(project(":heart-mcp"))
    // The SDK logs through SLF4J; stdout is the protocol, so nothing may log there.
    runtimeOnly("org.slf4j:slf4j-nop:2.0.16")
}

kotlin {
    jvmToolchain(22)
}

application {
    mainClass.set("heart.mcp.server.MainKt")
    applicationName = "heart-mcp"
}
