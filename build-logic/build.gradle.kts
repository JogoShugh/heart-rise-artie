plugins {
    `kotlin-dsl`
}

// The tools the convention plugins apply. Their versions live here only; a build that applies
// artie.kotlin-quality names none of them.
dependencies {
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.1")
    implementation("de.aaschmid:gradle-cpd-plugin:3.5")
}
