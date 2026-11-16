plugins {
    kotlin("jvm")
    id("artie.kotlin-quality")
}

// heart-script-test: expect, for scripts that provoke outcomes and refusals on purpose (conformance and
// demonstration). An add-on to heart-script; everyday scripts never need it.
group = "heart"
version = "0.1.0"

repositories {
    mavenCentral()
}

val cucumberVersion = "7.18.0"

dependencies {
    api(project(":heart-script"))

    testImplementation(testFixtures(project(":heart-script")))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-picocontainer:$cucumberVersion")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(22)
}
