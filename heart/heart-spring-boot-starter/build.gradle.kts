plugins {
    `java-library`
}

// heart-spring-boot-starter: one dependency for a HEART server on Spring Boot.
// It brings heart-core, the HTTP binding (heart-spring) and WebFlux. Add
// heart-coap for CoAP and heart-eventsourcing for an event store, as needed.
dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:3.3.4"))
    api(project(":heart-core"))
    api(project(":heart-spring"))
    api("org.springframework.boot:spring-boot-starter-webflux")
}
