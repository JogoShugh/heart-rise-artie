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
| `[0.5]` template variable = Layer 1 property | **Gap.** The template uses `{bedId}` but Layer 1 has `id` | `buildForms` vs `buildBedBody` |
| L4 `412` stale write | **Done** | `StaleWriteConflict`, journal entry 06 |
| L4 `409` illegal transition | **Done** | `InvalidTransitionException` handler |
| L4 `428` missing `If-Match` | **Missing.** `If-Match` is optional; with no header the write proceeds | `checkIfMatch` returns early on null |
| `422` on schema failure | **Gap.** Bean validation failures fall through to WebFlux's default `400` | no handler for `WebExchangeBindException` |
| `[0.5]` evaluation order 428→412→409→422 | **Gap.** `@Valid` runs before `checkIfMatch`, so `422`/`400` wins over `412` | method parameter binding order |
| L5: both dialects, one legality source | **Done** | `allowedAffordances` shared; `RequestDtoMetadataTest` |
| L5: HAL-FORMS Layer 2/3 addressing | **Missing.** HAL-FORMS is inline only, with no content-addressed layers | `buildHalFormsResponse` |
| §6.1 `Accept-Profile` / `Link: rel="profile"` | **Missing.** Still uses the withdrawn media-type `profile` parameter | `contentType(... ;profile=...)` |
| `Preference-Applied` | **Done** (step 1). Echoes `forms=cid` or `forms=cid, schema=cid`; absent at L0 | `Rung.preferenceApplied` |
| Digest length | 16 hex chars. Allowed for a closed-world demo server (§6.5); production would need the full digest | `take(16)` |
| L6 | n/a (server) | |

## client/ (ARTIE, the deterministic Kotlin client)

| Rung / rule | Status |
|---|---|
| L0 parse of an embedded response | **Partly there.** Parses `_forms` from canned responses; tested against `FakeTransport` only |
| Reject an affordance absent from the manifest before any I/O | **Done**, with no network call (strengthened 2026-10-04) |
| Never construct a URI (`act(rel, payload)` has no URL parameter) | **Done**, enforced by the type signature |
| Hold the Layer 1 `ETag` and send `If-Match` on acts | **Missing** |
| Handle `409` / `412` / `428` on separate paths | **Missing** |
| Send `Prefer`; walk L1/L2; HTTP cache | **Missing** |
| RFC 6570 template expansion | **Missing** |
| `Accept` / `Accept-Profile` negotiation | **Missing** |
| A real HTTP transport (talking to server-spring) | **Missing** |
| L6 Reason step | **Missing.** No resolver, scripted or LLM |
| RISE | **Missing** |

## exhibits/cache-demo

This exhibit demonstrates the L2/L3 caching *property*: shared sets fetched once,
`304` revalidation, and server-log verification. Its fixtures do not follow the
0.4 wire format: sets use `schemaRef.href`, not `schema.$id`, there are no
`_forms`, and there is no `Prefer` negotiation. It is evidence for §6.6/§10.5 as
a mechanism, not a conformance test.

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
   legality.

Live evidence: `server-spring/capture.sh` and `journal.html` still exercise
the 0.3 wire format (`embed=schemas`, unconditional POSTs). They are
refreshed against 0.4 once the server steps are in.

The client work starts once step 1 is in: a real HTTP transport that
walks L0→L2 against server-spring is the first test of ARTIE against a
real server.
