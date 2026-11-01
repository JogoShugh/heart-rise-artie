plugins {
    `java-library`
    `java-test-fixtures`
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

// heart-sims: narrow simulated HEART services, a build service and a deploy
// service beside the gateway's small GitHub, for the client conformance features
// every HEART client is held to (spec/PLAN-CONFORMANCE-SERVICES.md). They are test
// doubles, so they are test fixtures: ARTIE's steps use them in memory, and
// heart-ts's steps run them as a process (installSims, then MainKt).
dependencies {
    testFixturesApi(project(":heart-http4k"))
    testFixturesImplementation(project(":heart-eventsourcing"))
    testFixturesImplementation("org.http4k:http4k-server-jetty")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testFixturesImplementation("com.fasterxml.jackson.core:jackson-databind")
}

kotlin {
    jvmToolchain(21)
}

// The simulator and all it needs, for a process: java -cp 'build/sims/lib/*' com.artie.heart.sims.MainKt <port>.
val installSims by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Copies the simulator and its classpath to build/sims/lib, for clients that run it as a process."
    from(tasks.named("testFixturesJar"))
    from(configurations.named("testFixturesRuntimeClasspath"))
    into(layout.buildDirectory.dir("sims/lib"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
}
