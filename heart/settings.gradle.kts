rootProject.name = "heart"

// The HEART server library (spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, Part A).
// A server includes this build and depends on com.artie.heart:<module>.
include("heart-core")
include("heart-spring")
include("heart-coap")
include("heart-eventsourcing")
include("heart-tck")
include("heart-http4k")
include("heart-gateway")
include("heart-spring-boot-starter")
