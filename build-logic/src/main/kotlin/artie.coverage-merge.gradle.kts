// artie.coverage-merge: a build's root project, merging its modules' Kover coverage into one report
// (build/reports/kover/report.xml): a line counts as covered whichever module's tests ran it.
// The root lists its modules with dependencies { kover(project(":name")) }.
plugins {
    id("org.jetbrains.kotlinx.kover")
}
