# Slice 1 Review — Media type model: parse a cold-start response

---

## Original plan

- Define data classes reflecting the dual-profile structure:
  - `HeartResponse` — top-level resource state + `_forms`
  - `AffordanceForm` — `rel`, `href`, `hash`, `_schema`
  - `JsonSchema` — full JSON Schema document (`type`, `properties`, `required`, plus any other keywords preserved as-is)
  - `ResourceState` — domain data carried in the response body

**Tests:**
- Given a valid `application/hal+json` response with both profile URIs in the `Content-Type`, parsing produces the correct `rel`, `href`, `hash`, and a `_schema` object containing `properties` and `required`
- `_schema.properties` entries carry full schema keywords (`description`, `minimum`, `enum`, etc.), not just type strings
- Resource state is extracted independently of `_forms`

---

## What was actually built

### Data model

`JsonSchema` and `ResourceState` were not created. `kotlinx.serialization` already provides `JsonObject` — an arbitrary JSON container with full fidelity. Wrapping it in custom classes adds no value and fights the library. Both the schema and the resource state are held as `JsonObject` directly.

`HeartResponse` was created and then replaced by `HeartRepresentation`. The difference: `HeartRepresentation` is a class with behaviour, not a passive data bag. It exposes `rels(): Set<String>` and `affordance(rel: String): AffordanceForm?` because it is the in-memory form of the H.E.A.R.T. media type and the direct input to the SRA Reason step.

`AffordanceForm` was created as planned: `rel`, `href`, `hash`, `schema: JsonObject`. Constants (`_forms`, `href`, `hash`, `_schema`, `properties`, `required`, etc.) are top-level declarations in `Model.kt` — Kotlin does not support wildcard import from objects, so `object Keys` was rejected.

### Parser

`HeartResponseParser` introduced as an internal `object` — not referenced outside the `heart` package. It validates both profile URIs in the `Content-Type`, parses the JSON body, extracts `_forms` into `Map<String, AffordanceForm>`, and returns a `HeartRepresentation` with `_forms` filtered out of the state.

### Client layer — not in the original plan

Initial step definitions called `HeartResponseParser.parse()` directly, which violated outside-in BDD: tests were coupled to an internal implementation detail. The refactor introduced:

- `HeartRiseClient` — interface with two methods: `enter(url: String): HeartRepresentation` and `act(rel: String, payload: JsonObject): HeartRepresentation`. This is the protocol contract. See `ADR.md` for the full rationale on naming and method design.
- `ArtieClient` — the reference implementation. Takes a `Transport`, delegates parsing to `HeartResponseParser` internally. `act` is stubbed pending its own slice.
- `Transport` — interface abstracting the wire: `get(url)` and `submit(url, method, payload)`. Default will be HTTP; anything else is a swap.
- `ServerResponse` — value type (`body: String`, `contentType: String`) passed between transport and client. Never surfaces on `HeartRiseClient`.
- `FakeTransport` — queues pre-canned `ServerResponse` values. Step definitions enqueue a response, then call `client.enter()`. No HTTP in tests.

### Tests

Framework: Cucumber/Gherkin. Decision made in Slice 0 but load-bearing here — `.feature` files are language-neutral, so other teams implementing the same protocol can reuse them. The Kotlin steps are the reference implementation of the tests, not the tests themselves.

Feature file: `media_type.feature` (named after §6.1). Step definitions: `MediaTypeSteps.kt`.

**Background** (shared across all scenarios):
- YAML docstring for the cold-start response body (converted to JSON inside the step via Jackson)
- Content-Type set to the dual-profile value
- `When the client parses the response` — enqueues the response on `FakeTransport`, calls `client.enter()`

**Scenarios:**
1. Affordance fields parsed correctly — data table asserting `href` and `hash` for the `update` affordance
2. Schema preserves full JSON Schema keywords — Scenario Outline over `(property, keyword)` pairs: `(name, description)`, `(count, minimum)`, `(status, enum)`
3. Required fields captured — Scenario Outline over `field`: `name`, `status`
4. Resource state extracted independently of `_forms` — asserts `id` and `name` present, `_forms` absent

Generic data table handling via `@DefaultDataTableEntryTransformer` and `kotlin-reflect` — one reflection-based transformer handles all row types with no per-type boilerplate.

---

## Key divergences from plan

| Plan | Actual | Reason |
|---|---|---|
| `JsonSchema` class | `JsonObject` | Library already handles arbitrary JSON |
| `ResourceState` class | `JsonObject` | Same |
| `HeartResponse` (data class) | `HeartRepresentation` (class with methods) | Needs behaviour for Reason step |
| No parser class specified | `HeartResponseParser` (internal) | Parsing logic needs a home; hidden behind client |
| No client layer | `HeartRiseClient` + `ArtieClient` + `Transport` | Outside-in BDD: tests must talk to the protocol boundary, not internals |
| Tests unspecified format | Cucumber/Gherkin | Cross-language portability of feature files |
