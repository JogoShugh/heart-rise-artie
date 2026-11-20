plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    application
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.12"

dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.slf4j:slf4j-simple:2.0.13")

    // RFC 8785 JSON Canonicalization Scheme, used by GenerateFixtures.kt to
    // compute Layer 2/3 content addresses per SPEC.md §6.5. titanium-jcs
    // operates on Jakarta JSON-P's JsonValue tree, not plain Kotlin objects,
    // so we bring both the API (titanium-jcs depends on it as `provided`,
    // which Maven does not propagate) and a runtime implementation to build
    // JsonValue trees with Json.createObjectBuilder()/createArrayBuilder().
    implementation("com.apicatalog:titanium-jcs:1.0.0")
    implementation("jakarta.json:jakarta.json-api:2.0.1")
    runtimeOnly("org.glassfish:jakarta.json:2.0.1")
}

application {
    mainClass.set("ArtieCacheDemoKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("generateFixtures") {
    group = "application"
    description = "Builds ../fixtures with sha256-over-JCS-canonical-JSON content addressing"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("GenerateFixturesKt")
    // Mirrors generate_fixtures.sh's behavior of resolving paths relative to
    // its own (repo-root) location, regardless of the invoking shell's cwd.
    workingDir = rootDir.parentFile
}
