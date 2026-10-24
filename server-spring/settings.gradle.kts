rootProject.name = "spring-artie-backend"

// The HEART server library (spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, Part A),
// built from source beside this server and substituted for com.artie.heart:*.
includeBuild("../heart")

// Buster, the chaos filter, is a standalone repo checked out next to this one.
// It is opt-in: only -Pbuster=on brings it in, and the composite build then
// substitutes it for com.artie:buster. Without the flag the server never sees it.
if (providers.gradleProperty("buster").orNull == "on") {
    require(file("../buster/settings.gradle.kts").exists()) {
        "-Pbuster=on needs Buster checked out at ../buster"
    }
    includeBuild("../buster")
}
