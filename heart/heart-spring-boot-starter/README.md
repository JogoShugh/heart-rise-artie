# heart-spring-boot-starter

One dependency for a HEART server on Spring Boot: `heart-core`,
`heart-spring` and `spring-boot-starter-webflux`.

```kotlin
implementation("com.artie.heart:heart-spring-boot-starter")
implementation("com.artie.heart:heart-coap")            // optional: CoAP
implementation("com.artie.heart:heart-eventsourcing")   // optional: event store
testImplementation("com.artie.heart:heart-tck")         // conformance tests
```

Then register a `HeartResource` bean. See `../README.md` for the four steps.
