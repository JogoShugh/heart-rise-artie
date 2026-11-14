plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

// artie-minecraft: a HEART resource as a Minecraft dialog (docs/reports/minecraft-plan). ARTIE reads the resource;
// a Paper server plugin shows each dialog to the player and sends what the player presses.
group = "heart"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val cucumberVersion = "7.18.0"

dependencies {
    implementation(project(":artie"))
    // The server provides Paper's API; the plugin jar carries the rest.
    compileOnly("io.papermc.paper:paper-api:26.3.build.244-stable")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-picocontainer:$cucumberVersion")
    // The simulated deploy service, in memory.
    testImplementation(testFixtures("com.artie.heart:heart-sims"))
    // The text a dialog draws is Adventure's, which needs no server: the scenarios read its colours.
    testImplementation("io.papermc.paper:paper-api:26.3.build.244-stable")
}

tasks.test {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
}

// Detekt 1.23 knows Java up to 22; it reads the source, so the newest it knows will do.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "22"
}

// Paper 26.3 runs on Java 25, and its API is built for it.
kotlin {
    jvmToolchain(25)
}

// The plugin as one jar for the server's plugins folder: this module, ARTIE and what they need, Kotlin's own
// library included, since Paper doesn't carry it.
val pluginJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the Paper plugin jar, with ARTIE and its libraries inside."
    archiveClassifier = "plugin"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.map { it.output })
    from(configurations.runtimeClasspath.map { classpath -> classpath.map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
}
