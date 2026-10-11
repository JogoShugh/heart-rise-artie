plugins {
    `java-library`
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

// heart-eventsourcing: an optional event store for declared resource types.
// A resource's state is the fold of its events; the store numbers them and
// refuses a second event at a number already taken. In memory, or on Postgres
// through R2DBC.
dependencies {
    api(project(":heart-core"))
    api(platform("org.springframework.boot:spring-boot-dependencies:3.3.4"))
    api("org.springframework:spring-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.slf4j:slf4j-api")

    testImplementation(platform("io.cucumber:cucumber-bom:7.18.0"))
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-picocontainer")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:r2dbc")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
}
