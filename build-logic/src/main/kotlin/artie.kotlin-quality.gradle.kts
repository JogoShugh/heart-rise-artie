// artie.kotlin-quality: what every Kotlin module in this repo is checked with, in one place.
//   detekt  complexity and style, at its defaults (buildUponDefaultConfig); a module may add a baseline.
//   Kover   coverage of the plain test task: build/reports/kover/report.xml, JaCoCo's XML, read by SonarQube.
//   CPD     PMD's copy-paste detector over the module's Kotlin: cpdCheck, run by check.
plugins {
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
    id("de.aaschmid.cpd")
}

val pmdVersion = "7.29.0"

dependencies {
    "cpd"("net.sourceforge.pmd:pmd-kotlin:$pmdVersion")
}

detekt {
    buildUponDefaultConfig = true
}

// Only the plain test task: Kover would otherwise run every Test task for its report and check,
// the live ones too (authLiveTest needs scripts/auth-check.sh's services).
afterEvaluate {
    kover {
        currentProject {
            instrumentation {
                disabledForTestTasks.addAll(tasks.withType<Test>().names.filter { it != "test" })
            }
        }
    }
}

cpd {
    language = "kotlin"
    toolVersion = pmdVersion
    minimumTokenCount = 100
    // Reported for now; the build fails on a duplicate once the existing ones are gone.
    isIgnoreFailures = true
}

tasks.named<de.aaschmid.gradle.plugins.cpd.Cpd>("cpdCheck") {
    source = fileTree("src/main/kotlin")
    reports {
        xml.required.set(true)
        text.required.set(true)
    }
}
