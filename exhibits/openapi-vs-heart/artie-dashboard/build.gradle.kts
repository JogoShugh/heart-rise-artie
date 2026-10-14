plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("heart:heart")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // htmx, served by the dashboard itself; the same version the bespoke dashboard serves.
    runtimeOnly("org.webjars.npm:htmx.org:2.0.3")
}

kotlin {
    jvmToolchain(22)
}

application {
    mainClass.set("ArtieDashboardKt")
}
