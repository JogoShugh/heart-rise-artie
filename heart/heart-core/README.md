# heart-core

HEART with no protocol in it: no Spring, no HTTP, no CoAP types. Every
binding (`heart-spring`, `heart-coap`) calls this module, so all bindings
behave the same.

## What is in it

| File | What it does |
|---|---|
| `Declaration.kt` | The model a server writes: `Profile` and `Relation` (its ALPS vocabulary), `Act` (one act; `Act.of` takes an input class), `Offers` (acts per state key, and why the others are absent), `StateView` (how to read a state), `Finding` (search and lookup), `ResourceType` (all of it together; `bookmarkActs` are the acts the service index offers for the type) |
| `HeartResource.kt` | What a binding serves: `Layers` plus `load`, `perform`, and optional `find`, `past` and `performAtBookmark` |
| `Layers.kt` | Renders HAL Schema Forms: Layer 1 at L0, L1 or L2 (`layer1`), plus every affordance set, schema and absence explanation, each as canonical bytes keyed by address |
| `InputSchemas.kt` | `InputSchemas.of(class)` generates draft 2020-12 JSON Schema (Jakarta Validation + OpenAPI `@Schema`); `BodyCheck` validates a body against it (422 with JSON Pointers, 400 if not JSON) |
| `ActPipeline.kt` | `check` (428 no validator → 412 stale → 409 not offered), then `run` (422 body check, then `perform`, on the state `check` returned). A `ConcurrentWriteException` in `perform` becomes 412. `checkAtBookmark` runs the same checks for an act the bookmark offers, against the index's validator |
| `ContentAddress.kt` | RFC 8785 canonical JSON, the `sha256-…` address, SRI integrity, `Repr-Digest` |
| `Negotiation.kt` | `Dialect` (HAL Schema Forms, HAL-FORMS), choosing it from Accept / Accept-Profile, JSON Home detection |
| `Rung.kt`, `StateETag.kt` | L0–L2 from `Prefer`; Layer 1 ETags `"seq-<version>.<dialect>.<rung>"` and If-Match comparison |
| `ServiceIndex.kt`, `SearchPages.kt` | The bookmark's bodies (index in both dialects, with its own acts as forms on `/actions/{path}`; JSON Home) and search pages with `item`/`next`/`prev` links |
| `HalFormsProperties.kt` | An act's JSON Schema as HAL-FORMS properties, for the templates of the index's own acts |
| `Refusals.kt` | The refusal exceptions and the problem-type base URI |
| `ServingLimits.kt` | Max rung and page size, shared by all bindings |

## Rule to know

The acts offered must depend on the state key alone (`Offers.offered(key)`),
so every resource in one state shares one affordance set (SPEC §6.9). Sets,
schemas and explanations are computed once, from the declaration.

## Tests

`src/test/resources/features`: content addressing, negotiation, layers,
generated schemas, the act checks, the index. The lamp in
`src/testFixtures` is the shared example every module tests against.
