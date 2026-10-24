plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

// heart-script-host: the Kotlin script engine a HEART script runs in, for artie run and the features
// alike. Its compiler has Power-assert, so check(condition) draws each part of a condition that
// failed (spec/ideas/script-checks-and-reason-blocks.md).
group = "heart"
version = "0.1.0"

repositories {
    mavenCentral()
}

val kotlinVersion = "2.3.0"

dependencies {
    api("org.jetbrains.kotlin:kotlin-scripting-jsr223:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-impl-embeddable:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    // Loaded by the script compiler, from where it sits on the class path.
    runtimeOnly("org.jetbrains.kotlin:kotlin-power-assert-compiler-plugin-embeddable:$kotlinVersion") {
        isTransitive = false
    }
}

detekt {
    buildUponDefaultConfig = true
}

kotlin {
    jvmToolchain(22)
}
