// The client: a multi-project build (spec/PLAN-ARTIE-SCRIPTS.md). client/artie is ARTIE;
// the script DSL, its test add-on and the artie CLI join it as subprojects.
plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}
