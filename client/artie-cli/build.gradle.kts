plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
    application
}

// artie-cli: `artie run`, a HEART script run from the command line (spec/PLAN-ARTIE-SCRIPTS.md,
// "Running a script"). Installed with installDist; no Gradle at run time.
group = "heart"
version = "0.1.0"

repositories {
    mavenCentral()
}

val cucumberVersion = "7.18.0"

dependencies {
    implementation(project(":heart-script-test"))
    // The client a script finds, by ServiceLoader.
    runtimeOnly(project(":artie"))
    implementation(project(":heart-script-host"))
    implementation("org.yaml:snakeyaml:2.2")
    // Californium logs through SLF4J 1.7; with no binding it warns on every run. A run's output is its steps.
    runtimeOnly("org.slf4j:slf4j-nop:1.7.36")

    testImplementation(testFixtures(project(":heart-script")))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-picocontainer:$cucumberVersion")
}

application {
    applicationName = "artie"
    mainClass.set("heart.cli.MainKt")
}

// --report runs heart-run-report's builder: its one copy, in docs/reports/assets, installed as report/.
distributions {
    main {
        contents {
            from("../../docs/reports/assets") { into("report") }
        }
    }
}

// The classes need Java 22 or newer. Without this, an older java on the PATH fails with
// "UnsupportedClassVersionError"; with it, the launcher says what it needs and exits 2.
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        val check = """
            |JAVA_MAJOR=${'$'}("${'$'}JAVACMD" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')
            |if [ "${'$'}{JAVA_MAJOR:-0}" -lt 22 ] 2>/dev/null; then
            |    echo "artie needs Java 22 or newer; ${'$'}JAVACMD is Java ${'$'}{JAVA_MAJOR:-unknown}. Set JAVA_HOME to a newer JDK." >&2
            |    exit 2
            |fi
            |# The script engine's Kotlin compiler uses sun.misc.Unsafe; Java 24 and newer warn of it on every run.
            |if [ "${'$'}JAVA_MAJOR" -ge 24 ] 2>/dev/null; then
            |    JAVA_OPTS="--sun-misc-unsafe-memory-access=allow ${'$'}JAVA_OPTS"
            |fi
            |
        """.trimMargin()
        val script = unixScript.readText()
        val anchor = "# Collect all arguments for the java command"
        check(anchor in script) { "the start script changed: no \"$anchor\"" }
        unixScript.writeText(script.replace(anchor, check + "\n" + anchor))
    }
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
