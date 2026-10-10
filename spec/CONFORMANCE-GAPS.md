# Conformance gaps: code vs. SPEC.md 0.5

Where each implementation in this repo stands against the 0.4 conformance
ladder (SPEC §6.10), including the `[0.5]` proposals. I checked each line
against the code on 2026-10-04 (rows dated later were added then) and cite the file where the evidence lives.
"0.3 behaviour" means the server does what SPEC 0.3 asked for, which 0.4
has since changed.

## server-spring (the reference server)

| Rung / rule | Status | Evidence |
|---|---|---|
| L0: no `Prefer` returns everything embedded | **Done** (step 1) | `rendering-ladder.feature` |
| L1 `Prefer: forms=link` | **Done** (step 1) | `Rung.fromPrefer`, `rendering-ladder.feature` |
| L2 `Prefer: forms=link, schema=link` | **Done** (step 1) | same |
| §6.5: L1 and L2 sets have different addresses | **Done** (step 1) | registry keyed by (status, `SetRendering`); `ContentAddressHttpTest` |
| §6.1: Layer 2/3 send no `Vary` | **Done** (step 1) | `AffordanceSetController` ignores `Prefer` |
| §6.5: one URL per distinct body (the reference server names it by the SHA-256 of the served bytes; the client reads nothing from it) | **Done** (2026-10-04; naming made the server's own 2026-10-09) | `ContentAddressHttpTest` |
| §6.5 (2026-10-08): `integrity` on the forms link; L2 schema is `$ref` + `hr:integrity`; `Repr-Digest` on Layers 2 and 3 | **Done** (2026-10-08) | `rendering-ladder.feature`, journal |
| Layer 1 `Cache-Control: no-cache` | **Done** (step 2), also on `304` | `layer1-revalidation.feature` |
| Layer 1 `304` on `If-None-Match` | **Done** (step 2). Spring's built-in conditional GET; an ETag from another rung gets `200` | same |
| Strong, per-variant `ETag` (§6.1) | **Done** (step 2). `"seq-N.hsf.L2"` | `StateETag` |
| §6.3 (2026-10-08): acts repeat their negotiation headers; `If-Match` compared whole with the selected representation's `ETag`, `*` matches | **Done** (2026-10-08). No narrowing of RFC 9110 | `layer1-revalidation.feature`, `conditional-acts.feature`, client `sync-l2.feature` |
| L3: templated profile, sharing | **Done** for jsonschema; cross-bed proof passes | journal entry 04 |
| §6.11 (2026-10-08): service index at `/`; `searchBeds` and `getBed` GET forms; search results as `item` links | **Done** (2026-10-08), in both dialects: HAL-FORMS uses query GET templates (`/beds?name=`, `/beds?id=`) | `service-index.feature`, `hal-forms-on-ramp.feature`, live "From the bookmark alone" (L0, L2, HAL-FORMS) |
| §6.9.1 (2026-10-08): every template variable declared in `hr:templatePointers` | **Done** (2026-10-08). `{"id": "/id"}` on every shared form; L0's concrete forms carry none | `templated-targets.feature` |
| L4 `412` stale write | **Done** | `StaleWriteConflict`, journal entry 06 |
| L4 `409` illegal transition | **Done**. Now checked against the manifest before the body is read | `checkPreconditions` |
| L4 `428` missing `If-Match` | **Done** (step 3) | `conditional-acts.feature` |
| `422` on schema failure | **Done** (step 3). Wrong-typed fields are `422` too; a body that is not JSON is `400` | `ActBodyBinder` |
| `[0.5]` evaluation order 428→412→409→422 | **Done** (step 3). Bodies bind after the checks, not via `@Valid` | `GardenBedController.act` |
| L5: both dialects, one legality source | **Done**, now checked over HTTP in all three states | `cross-dialect.feature`, `RequestDtoMetadataTest` |
| L5: HAL-FORMS Layer 2/3 addressing | **Not required** (decided for 0.5): HAL-FORMS is the L0 on-ramp; it ignores `Prefer` | `buildHalFormsResponse` |
| §6.1 `Accept-Profile` / `Link: rel="profile"` | **Done** (step 4). `Link` names the composite profile and the parent; `Accept-Profile` picks the dialect when `Accept` leaves it open; unknown profiles fall back to the default; `406` only for an unsatisfiable `Accept`. The media-type `profile` parameter is gone from Layers 1 and 2 | `profile-negotiation.feature`, `Negotiation` |
| `Preference-Applied` | **Done** (step 1). Echoes `forms=link` or `forms=link, schema=link`; absent at L0 | `Rung.preferenceApplied` |
| Digest length | URL names use 16 hex characters, which only the server reads; every `integrity` value carries the full digest (§6.5, closed 2026-10-10) | `take(16)`, `integrityOf` |
| §6.2 (2026-10-10): refusals as RFC 9457 problem details, a `type` per refusal, 422 `violations` as JSON Pointers | **Done** (2026-10-10) | `problem-details.feature`, `Problems` |
| §6.1, §8.5 (2026-10-10): forms keyed by `garden:` relations, with titles, descriptions and field annotations; ALPS profile at `/profiles/garden` | **Done** (2026-10-10) for HAL Schema Forms; HAL-FORMS keeps template names | `form-relations.feature`, `GardenProfile` |
| §6.12 (2026-10-10): `hr:unavailable` explanation, immutable, linked with integrity | **Done** (2026-10-10) | `unavailable.feature` |
| L6 | n/a (server) | |
| Snapshot write on real Postgres | **Fixed** (C3). The first snapshot (event 5) threw after the event was committed, so the act returned `500` for a write that happened. Now an upsert, and best-effort | `EventStorePostgresTest` |
| Tests run the production web stack | **Done**. Production now runs WebFlux on Netty, like the Cucumber suite: `spring-boot-starter-web` is excluded from the HATEOAS starter. `runtime-stack.feature` fails if a servlet stack returns. Journal, content addresses and ARTIE live all pass on Netty |

## client/ (ARTIE, the deterministic Kotlin client)

| Rung / rule | Status |
|---|---|
| L0 parse of an embedded response | **Done** (C1) for the 0.4 wire format: `_forms` with `_links.target`, `method`, `contentType`, `schema`; targets resolved against the entry URL. Tested against `FakeTransport` |
| Reject an affordance absent from the manifest before any I/O | **Done**, with no network call (strengthened 2026-10-04) |
| Never construct a URI (`act(rel, payload)` has no URL parameter) | **Done**, enforced by the type signature |
| Hold the Layer 1 `ETag` and send `If-Match` on acts | **Done** (C2). The held value is sent unchanged; an act with no ETag held is refused locally |
| Handle `409` / `412` / `428` on separate paths | **Done** (C2). `412` re-syncs and drops the act; `409` re-syncs and reports; `428` re-syncs and resends once; `422` reports the server's violations. Each is its own `ActRefused` type |
| Send `Prefer`; walk L1/L2; HTTP cache | **Done** (C4). `Rung` picks the `Prefer` header; the walk follows Layer 1, the set, then each schema `$ref` (or an older server's `$id`) once; `CachingTransport` serves immutable layers without a request and revalidates Layer 1 with `If-None-Match`; `IntegrityCheck`, below the cache, refuses bytes that do not match the link's `integrity` or a `Repr-Digest`, and reads nothing from the URL (2026-10-08). Templates fill from `hr:templatePointers`, then form fields, then the same-named property. Live at L0, L1 and L2. Fetches are sequential, not parallel |
| RFC 6570 template expansion | **Done**. Level 1, from the form's declared `hr:templatePointers`, then its fields, then the same-named property (HAL-SCHEMA-FORMS-HR Rule 1); an unresolvable variable is refused before any request |
| `Accept` / `Accept-Profile` negotiation | **Done** (C1). Sends `Accept: application/hal+json` and `Accept-Profile` |
| Read the profile from `Link: rel="profile"` | **Done** (C1). A response that does not name the hal-schema-forms profile is refused |
| A real HTTP transport (talking to server-spring) | **Done** (C3). `HttpTransport` on `java.net.http`; `live-server.feature` runs sow, water, harvest, a stale write, and a 422 against server-spring + Postgres (`scripts/live-client-check.sh`) |
| L6 Reason step | **Done** (L6). `Reasoner` sees offers (name and schema, no URI or method) and returns `Act` or `Clarify`; `ArtieAgent` checks the selection against the manifest and the schema (`SchemaCheck`) before anything is sent, and records rejections. `ClaudeReasoner` keeps tool and system prompt as a cached prefix and the manifest last (§6.8); checked against the scripted transport. A real-model scenario (`@llm`, `./gradlew llmTest`) needs `ANTHROPIC_API_KEY` and has not been run |
| L1 walk (set with embedded schemas) | **Done**: fake and live (`l1-and-sharing.feature`, live full cycle at L1) |
| L3 sharing property | **Done**: two clients behind one shared cache, two beds in one state, one set fetch, fake and live (seed bed 4). The concrete-profile counter-case (one set per bed, two fetches, one schema fetch) is checked against the scripted server |
| L5: speak both dialects | **Done**. ARTIE reads HAL-FORMS at L0 (`_templates`, properties mapped to JSON Schema) and refuses to be set above L0 in it. Live: both dialects show the same act targets on all four seeded beds, and a full cycle runs over HAL-FORMS |
| RISE | **Missing** |
| Rel name of the Layer 1 link to the set | **Done** (decided for 0.5). `https://github.com/jogoshugh/heart-rise/rels/forms`; server-spring sends the `hr:forms` CURIE, ARTIE expands CURIEs (`forms-link.feature`) |
| §6.3 (2026-10-10): `Cache-Control: no-cache` on every Layer 1 request, honoured by its own cache | **Done** (2026-10-10) | `layer1-freshness.feature` |
| §6.11.1: the rung read from `Preference-Applied` before any forms link is followed | **Done** (2026-10-10, Buster #37) | `rung-discovery.feature` |
| §6.2 (2026-10-10): reads RFC 9457 problem details, and an older server's plain body | **Done** (2026-10-10) | `conditional-acts.feature` |
| §6.1, §6.8 (2026-10-10): form names expanded as relations; the reasoner sees titles and descriptions, and may ask for an ALPS descriptor | **Done** (2026-10-10) | `form-relations.feature` |
| §6.12 (2026-10-10): explains an absent act from `hr:unavailable`, checked against its integrity; never infers a reason | **Done** (2026-10-10) | `explaining-absence.feature` |
| Breadcrumbs (hack log 2026-10-10): `User-Agent` per client, W3C `traceparent`, an opt-in OpenTelemetry-style event log (file or POST) | **Done** (2026-10-10) | `user-agent.feature`, `breadcrumbs.feature` |

## exhibits/cache-demo

This exhibit demonstrates the L2/L3 caching *property*: shared sets fetched once,
`304` revalidation, and server-log verification. Its fixtures do not follow the
0.4 wire format: sets use `schemaRef.href`, not `schema.$id`, there are no
`_forms`, and there is no `Prefer` negotiation. It is evidence for §6.6/§10.5 as
a mechanism, not a conformance test. Its fixtures still use `{bedId}`.

## Suggested order to close the server gaps

The order follows the ladder: each step can be shown on its own.

1. **L0 default + `forms=link`/`schema=link` + per-rung addresses.** This is
   one change to `Prefer` parsing and to the affordance-set registry, which
   is keyed by (status, rung). Drop `Vary` on Layer 2.
2. **Layer 1 caching:** `Cache-Control: no-cache`, `304` on
   `If-None-Match`, and a per-variant `ETag` with the state version inside.
3. **L4 completion:** `428`, `422`, and the `[0.5]` evaluation order.
4. **Profiles:** `Link: rel="profile"` and `Accept-Profile`, replacing the
   media-type parameter.
5. **Template variable naming:** rename `{bedId}` to `{id}`, or add `bedId`
   to Layer 1.
6. ~~HAL-FORMS Layer 2/3~~: dropped. The author decided HAL-FORMS is the L0-only on-ramp (SPEC §6.1, 0.5).

Steps 1-5 are done (2026-10-04). Each has a feature file under
`server-spring/src/test/resources/features/` that was seen failing before
the fix.

Live evidence: `server-spring/capture.sh` and `journal.html` still exercise
the 0.3 wire format (`embed=schemas`, unconditional POSTs). They are
refreshed against 0.4 once the server steps are in.

The client work starts once step 1 is in: a real HTTP transport that
walks L0→L2 against server-spring is the first test of ARTIE against a
real server.
