// The quality plugins every Kotlin build in this repo applies, defined once (artie.kotlin-quality).
// Each build includes this one in its pluginManagement: includeBuild("../build-logic").
rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
