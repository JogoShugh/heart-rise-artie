plugins {
    `java-library`
    `java-test-fixtures`
    application
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

// heart-gateway: presents a service that only publishes OpenAPI to HEART
// clients as a HEART server (spec/PLAN-OPENAPI-GATEWAY.md). It reads the
// upstream's spec, builds resource types from it at run time, and serves them
// with heart-http4k; it calls the upstream with http4k's own client.
dependencies {
    api(project(":heart-http4k"))
    implementation("org.http4k:http4k-server-jetty")
    // Reads the upstream's OpenAPI 3.x description, with every $ref resolved.
    implementation("io.swagger.parser.v3:swagger-parser-v3:2.1.22")
    // Connectors are YAML files, one per resource, in a folder per API.
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    // A connector's escape hatch: a JSONata expression, bounded in time and depth. No dependencies of its own.
    implementation("com.dashjoin:jsonata:0.9.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // The small GitHub, on GitHub's own example JSON, and the gateway in front of it: for this
    // module's features and for clients' tests (ARTIE's, through a composite build).
    testFixturesApi(project(":heart-http4k"))
    testFixturesImplementation("com.fasterxml.jackson.core:jackson-databind")

    // The conformance features run against the gateway in front of a plain JSON
    // twin, served by heart-http4k in memory.
    testImplementation(project(":heart-tck"))
    testImplementation(testFixtures(project(":heart-core")))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.4"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("io.projectreactor.netty:reactor-netty-http")
    testImplementation(platform("io.cucumber:cucumber-bom:7.18.0"))
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
}

kotlin {
    jvmToolchain(21)
}

// `installDist` builds build/install/heart-gateway/bin/heart-gateway; it is configured by its environment.
application {
    mainClass.set("com.artie.heart.gateway.MainKt")
}

// The connectors that ship with the gateway (connectors/github, ...), beside bin/ and lib/.
distributions {
    main {
        contents {
            from("connectors") { into("connectors") }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
}
