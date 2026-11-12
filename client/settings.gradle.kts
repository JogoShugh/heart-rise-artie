rootProject.name = "heart"

// ARTIE, the client (heart:artie); the root project holds its tests.
include("artie")

// The HEART script DSL, and the contract a client implements to run it.
include("heart-script")

// expect, for test scripts: an add-on to the DSL.
include("heart-script-test")

// The script engine a HEART script runs in, with Power-assert.
include("heart-script-host")

// artie run: a script, run from the command line.
include("artie-cli")

// ARTIE in Minecraft: a HEART resource as a dialog, shown by a Paper server plugin.
include("artie-minecraft")

// The merge watch release with ARTIE inside its parties: the release desk, and the watch and CI answering through ARTIE.
include("artie-release")

// The HEART server library, for tests only: ARTIE meets the GitHub gateway in front of a small
// GitHub, in process (heart-gateway's test fixtures). ARTIE's own code depends on none of it.
// Both builds are named heart, so this one is known as heart-library.
includeBuild("../heart") { name = "heart-library" }
