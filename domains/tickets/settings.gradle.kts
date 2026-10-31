rootProject.name = "tickets"

// The HEART server library, built from source beside this domain.
includeBuild("../../heart")

// Buster, the chaos filter, is opt-in: only -Pbuster=on brings it in, and the
// composite build then substitutes it for com.artie:buster. See ../../buster.
if (providers.gradleProperty("buster").orNull == "on") {
    require(file("../../buster/settings.gradle.kts").exists()) {
        "-Pbuster=on needs Buster checked out at ../../buster"
    }
    includeBuild("../../buster")
}
