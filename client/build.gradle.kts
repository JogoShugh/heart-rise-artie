plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

group = "heart"
version = "0.1.0"

repositories {
    mavenCentral()
}

val cucumberVersion = "7.18.0"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("org.junit.platform:junit-platform-suite:1.11.0")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0")
    testImplementation(kotlin("reflect"))
    testImplementation("io.cucumber:cucumber-picocontainer:$cucumberVersion")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(22)
}
