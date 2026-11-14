plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
    application
}

// The merge watch release with ARTIE inside its parties (release-desk.feature): the release desk, which releases a
// pull request by running its kept script with ARTIE, and keeps the replies that run asks with; the offer watch and
// the CI wrapper, which answer those replies through ARTIE. ARTIE serves nothing: the parties do.
group = "heart"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":artie"))
    // A goal file read as artie run reads one, for the release goal's one capture.
    implementation(project(":artie-cli"))
    // The parties: GitHub through its gateway, the offer watch, the CI wrapper, Dev, and heart-core's replies.
    implementation(testFixtures("com.artie.heart:heart-sims"))
    // A kept script, read as artie run reads one.
    implementation("org.yaml:snakeyaml:2.2")
    runtimeOnly("org.slf4j:slf4j-nop:1.7.36")
}

kotlin {
    jvmToolchain(22)
}

detekt {
    buildUponDefaultConfig = true
}

application {
    mainClass.set("heart.release.MainKt")
}
