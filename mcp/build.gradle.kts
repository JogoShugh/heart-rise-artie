plugins {
    kotlin("jvm") version "2.3.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

subprojects {
    group = "heart"
    version = "0.1.0"
    repositories { mavenCentral() }
}
