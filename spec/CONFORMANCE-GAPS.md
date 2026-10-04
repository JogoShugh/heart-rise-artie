# Conformance gaps: code vs. SPEC.md 0.5

Where each implementation in this repo stands against the 0.4 conformance
ladder (SPEC §6.10), including the `[0.5]` proposals. I checked each line
against the code on 2026-10-04 and cite the file where the evidence lives.
"0.3 behaviour" means the server does what SPEC 0.3 asked for, which 0.4
has since changed.

## server-spring (the reference server)

| Rung / rule | Status | Evidence |
|---|---|---|
| L0: no `Prefer` returns everything embedded | **Done** (step 1) | `rendering-ladder.feature` |
| L1 `Prefer: forms=cid` | **Done** (step 1) | `Rung.fromPrefer`, `rendering-ladder.feature` |
| L2 `Prefer: forms=cid, schema=cid` | **Done** (step 1) | same |
| §6.5: L1 and L2 sets have different addresses | **Done** (step 1) | registry keyed by (status, `SetRendering`); `ContentAddressHttpTest` |
| §6.1: Layer 2/3 send no `Vary` | **Done** (step 1) | `AffordanceSetController` ignores `Prefer` |
| §6.5: address = SHA-256 of the served bytes | **Done** (2026-10-04) | `ContentAddressHttpTest` |
| Layer 1 `Cache-Control: no-cache` | **Done** (step 2), also on `304` | `layer1-revalidation.feature` |
| Layer 1 `304` on `If-None-Match` | **Done** (step 2). Spring's built-in conditional GET; an ETag from another rung gets `200` | same |
| Strong, per-variant `ETag` (§6.1, `[0.5]`) | **Done** (step 2). `"seq-N.hsf.L2"`; `If-Match` accepts any variant of the current state version | `StateETag` |
| L3: templated profile, sharing | **Done** for jsonschema; cross-bed proof passes | journal entry 04 |
| `[0.5]` template variable = Layer 1 property | **Done** (step 5). `/beds/{id}/actions/...`; expanding it from Layer 1 reaches the act | `templated-targets.feature` |
| L4 `412` stale write | **Done** | `StaleWriteConflict`, journal entry 06 |
| L4 `409` illegal transition | **Done**. Now checked against the manifest before the body is read | `checkPreconditions` |
| L4 `428` missing `If-Match` | **Done** (step 3) | `conditional-acts.feature` |
| `422` on schema failure | **Done** (step 3). Wrong-typed fields are `422` too; a body that is not JSON is `400` | `ActBodyBinder` |
| `[0.5]` evaluation order 428→412→409→422 | **Done** (step 3). Bodies bind after the checks, not via `@Valid` | `GardenBedController.act` |
| L5: both dialects, one legality source | **Done**, now checked over HTTP in all three states | `cross-dialect.feature`, `RequestDtoMetadataTest` |
| L5: HAL-FORMS Layer 2/3 addressing | **Open: needs a spec decision.** HAL-FORMS is inline only. HAL-FORMS has no schema-pointer mechanism, so an L2 `_templates` set needs a wire format SPEC does not define yet (see step 6 below). HAL-FORMS stays at L0 and ignores `Prefer` | `buildHalFormsResponse` |
| §6.1 `Accept-Profile` / `Link: rel="profile"` | **Done** (step 4). `Link` names the composite profile and the parent; `Accept-Profile` picks the dialect when `Accept` leaves it open; unknown profiles fall back to the default; `406` only for an unsatisfiable `Accept`. The media-type `profile` parameter is gone from Layers 1 and 2 | `profile-negotiation.feature`, `Negotiation` |
| `Preference-Applied` | **Done** (step 1). Echoes `forms=cid` or `forms=cid, schema=cid`; absent at L0 | `Rung.preferenceApplied` |
| Digest length | 16 hex chars. Allowed for a closed-world demo server (§6.5); production would need the full digest | `take(16)` |
| L6 | n/a (server) | |

## client/ (ARTIE, the deterministic Kotlin client)

| Rung / rule | Status |
|---|---|
| L0 parse of an embedded response | **Done** (C1) for the 0.4 wire format: `_forms` with `_links.target`, `method`, `contentType`, `schema`; targets resolved against the entry URL. Tested against `FakeTransport` |
| Reject an affordance absent from the manifest before any I/O | **Done**, with no network call (strengthened 2026-10-04) |
| Never construct a URI (`act(rel, payload)` has no URL parameter) | **Done**, enforced by the type signature |
| Hold the Layer 1 `ETag` and send `If-Match` on acts | **Partly there** (C1). The `ETag` is held; `If-Match` comes in C2 |
| Handle `409` / `412` / `428` on separate paths | **Missing** |
| Send `Prefer`; walk L1/L2; HTTP cache | **Missing** |
| RFC 6570 template expansion | **Missing** |
| `Accept` / `Accept-Profile` negotiation | **Done** (C1). Sends `Accept: application/hal+json` and `Accept-Profile` |
| Read the profile from `Link: rel="profile"` | **Done** (C1). A response that does not name the hal-schema-forms profile is refused |
| A real HTTP transport (talking to server-spring) | **Missing** |
| L6 Reason step | **Missing.** No resolver, scripted or LLM |
| RISE | **Missing** |

## exhibits/cache-demo

This exhibit demonstrates the L2/L3 caching *property*: shared sets fetched once,
`304` revalidation, and server-log verification. Its fixtures do not follow the
0.4 wire format: sets use `schemaRef.href`, not `schema.$id`, there are no
`_forms`, and there is no `Prefer` negotiation. It is evidence for §6.6/§10.5 as
a mechanism, not a conformance test. Its fixtures still use `{bedId}`.

## Suggested order to close the server gaps

The order follows the ladder: each step can be shown on its own.

1. **L0 default + `forms=cid`/`schema=cid` + per-rung addresses.** This is
   one change to `Prefer` parsing and to the affordance-set registry, which
   is keyed by (status, rung). Drop `Vary` on Layer 2.
2. **Layer 1 caching:** `Cache-Control: no-cache`, `304` on
   `If-None-Match`, and a per-variant `ETag` with the state version inside.
3. **L4 completion:** `428`, `422`, and the `[0.5]` evaluation order.
4. **Profiles:** `Link: rel="profile"` and `Accept-Profile`, replacing the
   media-type parameter.
5. **Template variable naming:** rename `{bedId}` to `{id}`, or add `bedId`
   to Layer 1.
6. **HAL-FORMS Layer 2/3,** if L5 is meant to cover addressing as well as
   legality. `[AUTHOR]` decision needed first: §6.1 names a HAL-FORMS
   Layer 3 "properties fragment", but HAL-FORMS has no field that points
   to one. Options: (a) a `_templates` entry carries a link such as
   `_links.properties` to the fragment; (b) HAL-FORMS stays an L0-only
   dialect and §6.2's "serve L0, L1, L2" applies per server, not per
   dialect.

Steps 1-5 are done (2026-10-04). Each has a feature file under
`server-spring/src/test/resources/features/` that was seen failing before
the fix.

Live evidence: `server-spring/capture.sh` and `journal.html` still exercise
the 0.3 wire format (`embed=schemas`, unconditional POSTs). They are
refreshed against 0.4 once the server steps are in.

The client work starts once step 1 is in: a real HTTP transport that
walks L0→L2 against server-spring is the first test of ARTIE against a
real server.
