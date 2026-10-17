plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
}

// ARTIE: the HEART client (SPEC §6.3, §6.8). Its tests live in the client's root project for now.
group = "heart"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // SPEC §6.0.1: the CoAP binding (RFC 7252), for coap: URIs.
    api("org.eclipse.californium:californium-core:3.14.0")
    // coaps: DTLS with a pre-shared key.
    api("org.eclipse.californium:scandium:3.14.0")
}

detekt {
    buildUponDefaultConfig = true
    // The client's one baseline, shared with its root project: shrink it as code is touched; never grow it.
    baseline = file("../detekt-baseline.xml")
}

kotlin {
    jvmToolchain(22)
}
