# HEART server library

Build a HEART server (`spec/SPEC.md`) by declaring your resources. You write
the domain code and one declaration; the library serves HAL Schema Forms and
HAL-FORMS at every rung, checks every act, and answers over HTTP and CoAP.
You write no controllers.

## Modules

| Module | Purpose |
|---|---|
| `heart-core` | HEART without a protocol: the declaration model, HAL Schema Forms rendering (L0–L2), content addressing, schemas generated from input classes, act checks (428 → 412 → 409 → 422), negotiation, service index, JSON Home, search pages |
| `heart-spring` | HTTP binding on Spring WebFlux. HAL-FORMS via Spring HATEOAS |
| `heart-coap` | CoAP binding on Eclipse Californium (SPEC §6.0.1), HAL Schema Forms only |
| `heart-eventsourcing` | Optional event store, in memory or Postgres (R2DBC) |
| `heart-tck` | Conformance features a server runs against itself |
| `heart-spring-boot-starter` | `heart-core` + `heart-spring` + WebFlux |

The bindings are Spring Boot auto-configurations: adding the dependency is enough.

## A server in four steps

**1. Depend on the starter** (and `heart-coap` for CoAP).

```kotlin
implementation("com.artie.heart:heart-spring-boot-starter")
```

Inside this repo, `includeBuild("../heart")`. Elsewhere, run
`./gradlew publishToMavenLocal` here and add `mavenLocal()`.

**2. Write each act's input as a class.** Its JSON Schema is generated from
Jakarta Validation and OpenAPI `@Schema` annotations, and request bodies are
checked against that same schema.

```kotlin
@Schema(title = "Dim")
data class Dim(
    @field:NotNull @field:Min(1) @field:Max(100)
    @field:Schema(title = "Brightness", examples = ["50"])
    val brightness: Int? = null,
)
```

**3. Declare the resource type.**

```kotlin
val lamps = ResourceType<Lamp, String>(
    collection = "lamps",
    profile = Profile("home", title = "Lamps", doc = "The acts a lamp offers.", relations = listOf(
        Relation("switch-on", AlpsType.UNSAFE, "Switch on", "Turns the lamp on.", "Offered while off."),
        Relation("dim", AlpsType.IDEMPOTENT, "Dim the lamp", "Sets the brightness.", "Offered while on."),
    )),
    acts = listOf(
        Act.of("switchOn", "switch-on", SwitchOn::class.java),
        Act.of("dim", "dim", Dim::class.java),
    ),
    offers = Offers(
        keys = listOf("ON", "OFF"),
        offered = { key -> if (key == "ON") listOf("dim") else listOf("switchOn") },
        whyAbsent = { key, _ -> if (key == "ON") "The lamp is already on." else "Switch it on first." },
    ),
    state = StateView(
        keyOf = { it.status },
        idOf = { it.id },
        versionOf = { it.version },
        bodyOf = { mapOf("id" to it.id, "status" to it.status, "brightness" to it.brightness) },
    ),
)
```

- `profile`: the vocabulary, served as ALPS at `/profiles/home`; forms are keyed `home:dim`.
- `offers`: which acts each state key allows. It must depend on the key alone,
  so every lamp in one state shares one affordance set (SPEC §6.9).
- `versionOf`: changes with every change of state; it is part of the ETag.

**4. Register it as a bean.**

```kotlin
@Bean
fun lampResource(store: LampStore): HeartResource<*, *> = HeartResource(
    layers = Layers(lamps),
    load = { id -> store.find(id) ?: throw ResourceNotFoundException("no lamp $id") },
    perform = { id, act, input -> Acted(store.apply(id, act, input)) },
)
```

`perform` runs only after the checks pass. `input.bind(Dim::class.java)` gives
the body, already valid against the act's schema. Throw `ConcurrentWriteException` if another write landed first
(it becomes 412).

## What is then served

| Path | Content |
|---|---|
| `GET /` | Service index (either dialect), or JSON Home when `Accept` ranks it first |
| `GET /lamps/{id}` | Layer 1: L0 by default, L1/L2 with `Prefer: forms=link[, schema=link]`; ETag, 304 |
| `POST /lamps/{id}/actions/{path}` | The act, with `If-Match`; answers the new Layer 1 and `Content-Location` |
| `GET /affordance-sets/{address}`, `/schemas/{address}`, `/unavailable/{address}` | Shared layers, immutable, named by SHA-256 |
| `GET /profiles/home` | ALPS profile |

Refusals are RFC 9457 problems (`precondition-required`, `stale`,
`not-offered`, `invalid-input`). Over CoAP the same paths answer on UDP.

## Optional

- **Search and lookup**: add `finding = Finding(...)` to the type and
  `find = { name, id -> ... }` to the resource. The index then offers both
  forms, and `GET /lamps?name=` returns pages of `item` links.
- **Past**: add `past = Past(at = ..., history = ...)`. Then
  `GET /lamps/{id}?asOf=<instant>` and `GET /lamps/{id}/history` are served.
  `heart-eventsourcing`'s `EventStore` can answer both (`state(asOf=…)`, `history`).

## Prove it

Add `testImplementation("com.artie.heart:heart-tck")`, register a
`TckFixture` bean (a resource, an act it offers, an act it does not, a valid
and an invalid body, a search term), and run the features at
`classpath:heart/tck` with glue `com.artie.heart.tck`. Thirteen scenarios
cover the layers, revalidation, every refusal, both dialects, the index and
the profile.

## Configuration

| Property | Default | |
|---|---|---|
| `heart.max-rung` | `L2` | Highest rung served |
| `heart.page-size` | `20` | Items per search page |
| `heart.title` | first profile's title | API title |
| `heart.coap.port` | `5683` | `0`: any free port, `-1`: off |

## Working examples

- The lamp: `heart-core/src/testFixtures/.../fixtures/Lamps.kt`, served and
  checked in `heart-tck/src/test/.../lamp/LampTck.kt`.
- The garden: `server-spring/src/main/kotlin/com/artie/gardenbed/`
  (`web/GardenBeds.kt`, `config/HeartConfig.kt`).

Build: `./gradlew check` (Java 21; the event-store features need Docker).
