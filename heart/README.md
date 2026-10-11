# HEART server library

A library for building HEART servers (see `spec/SPEC.md`), the way Spring
HATEOAS is a library for building hypermedia APIs. A server writes its
domain code and one declaration of its resources and acts. The library
renders the three layers, checks every act, and serves the result over HTTP
and CoAP. A server writes no controllers.

## Modules

| Module | What it is | Depends on |
|---|---|---|
| `heart-core` | HEART itself, with no protocol: the declaration model, HAL Schema Forms rendering at L0, L1 and L2, content addressing, schemas generated from input classes, the act checks (428, 412, 409, 422), negotiation, the service index, JSON Home and search pages | Jackson, Jakarta JSON-P |
| `heart-spring` | The HTTP binding on Spring WebFlux, with HAL-FORMS rendered by Spring HATEOAS. Applied by Spring Boot auto-configuration | `heart-core` |
| `heart-coap` | The CoAP binding on Eclipse Californium (SPEC §6.0.1). Applied by auto-configuration | `heart-core` |
| `heart-eventsourcing` | Optional. An event store, in memory or on Postgres through R2DBC: numbered events per resource, as-of reads, history, snapshots | `heart-core` |
| `heart-tck` | The conformance features. A server runs them against itself to show it is HEART compliant | `heart-core` |
| `heart-spring-boot-starter` | One dependency: `heart-core`, `heart-spring` and WebFlux | the above |

## A HEART server in five minutes

The example is the lamp from `heart-core`'s test fixtures: on or off, and dimmable while on.

**1. Depend on the starter.** Inside this repo, include the build
(`includeBuild("../heart")` in `settings.gradle.kts`); outside it, run
`./gradlew publishToMavenLocal` here and add `mavenLocal()`.

```kotlin
dependencies {
    implementation("com.artie.heart:heart-spring-boot-starter")
    testImplementation("com.artie.heart:heart-tck")
}
```

**2. Write each act's input as a class.** Its JSON Schema is generated from
these annotations, and bodies are checked against that same schema.

```kotlin
@Schema(title = "Dim")
data class Dim(
    @field:NotNull @field:Min(1) @field:Max(100)
    @field:Schema(title = "Brightness", description = "Percent of full brightness", examples = ["50"])
    val brightness: Int? = null,
)
```

**3. Declare the resource type.** The relations are the vocabulary (served
as ALPS at `/profiles/home`); the offers say which acts each state allows
and why the others are absent; the state view says how to read a lamp.

```kotlin
val lamps = ResourceType(
    collection = "lamps",
    profile = Profile("home", "Lamps", "The acts a lamp offers.", relations = listOf(
        Relation("switch-on", AlpsType.UNSAFE, "Switch on", "Turns the lamp on.", "Offered while off."),
        Relation("dim", AlpsType.IDEMPOTENT, "Dim the lamp", "Sets the brightness.", "Offered while on."),
        // ...
    )),
    acts = listOf(Act.of("switchOn", "switch-on", SwitchOn::class.java), Act.of("dim", "dim", Dim::class.java)),
    offers = Offers(
        keys = listOf("ON", "OFF"),
        offered = { key -> if (key == "ON") listOf("dim") else listOf("switchOn") },
        whyAbsent = { key, _ -> if (key == "ON") "The lamp is already on." else "The lamp is off; switch it on." },
    ),
    state = StateView(keyOf = { it.status }, idOf = { it.id }, versionOf = { it.version }, bodyOf = { /* JSON */ }),
)
```

The acts offered must depend on the state's key alone, so every lamp in one
state shares one affordance set (SPEC §6.9).

**4. Register it.** Say how a lamp is read and what each act does.

```kotlin
@Bean
fun lampResource(store: LampStore) = HeartResource(
    layers = Layers(lamps),
    load = { id -> store.find(id) ?: throw ResourceNotFoundException("no lamp $id") },
    perform = { id, act, input -> Acted(store.apply(id, act, input)) },
)
```

That is the whole server. `GET /`, `GET /lamps/7` in either dialect and at
any rung, `POST /lamps/7/actions/dim` with If-Match, the shared affordance
sets, schemas and explanations, and `/profiles/home` are all served. Add
`heart-coap` and the same lamp answers on `coap://`.

**5. Prove it.** Register a `TckFixture` bean (one lamp, an act it offers,
an act it does not, a valid and an invalid body, a search term) and run the
features under `classpath:heart/tck` with the glue `com.artie.heart.tck`.
`heart-tck`'s own tests do exactly this for the lamp; `server-spring`'s
`RunHeartTckTest` does it for the garden.

## Optional parts of a declaration

- **Finding** (`ResourceType.finding`, `HeartResource.find`): a search form
  and a lookup form on the service index, and `/{collection}?name=` pages.
- **Past** (`HeartResource.past`): for state folded from events, the state
  as of an instant at `/{collection}/{id}?asOf=` and the events at
  `/{collection}/{id}/history`. `heart-eventsourcing` keeps such state.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `heart.max-rung` | `L2` | The highest rung served (SPEC §6.11.1) |
| `heart.page-size` | `20` | Items in one page of a search |
| `heart.title` | the first profile's title | The API's title in the index and home document |
| `heart.coap.port` | `5683` | CoAP's UDP port; `0` for a free one, `-1` for none |

## Building

```
JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.12-graal ./gradlew check
```

`heart-eventsourcing`'s features run against Postgres in Testcontainers, so
they need Docker.
