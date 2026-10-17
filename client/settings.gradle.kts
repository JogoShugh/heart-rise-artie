rootProject.name = "heart"

// ARTIE, the client (heart:artie); the root project holds its tests.
include("artie")

// The HEART server library, for tests only: ARTIE meets the GitHub gateway in front of a small
// GitHub, in process (heart-gateway's test fixtures). ARTIE's own code depends on none of it.
// Both builds are named heart, so this one is known as heart-library.
includeBuild("../heart") { name = "heart-library" }
