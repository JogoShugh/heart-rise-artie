# H.E.A.R.T. + R.I.S.E. Implementation Plan

**Based on:** SPEC.md v0.2 Working Draft (updated June 2026)  
**Date:** June 2026

---

## Part 1 — Real-World Assessment

### What works

**The core insight holds.** The token-cost argument is real and measurable. The structural hallucination elimination claim is mechanically correct and verifiable: if the client cannot construct a URI from prior knowledge and can only exercise affordances present in the current manifest, that failure class is prevented at the protocol boundary, not just reduced. That's a strong, testable property.

**Full JSON Schema in field definitions.** The updated spec grounds field schemas in HAL Schema Forms, giving the full JSON Schema vocabulary to every affordance form's `_schema` object. Servers can declare `enum` constraints, `minimum`/`maximum` bounds, `pattern` and `format` restrictions, `description` text at both the form and property level, and conditional keywords. The LLM has rich semantic context for every field — not just a name and a type string. This substantially reduces value-level hallucination (submitting out-of-range values, invalid enum members, malformed strings) in addition to the structural hallucination H.E.A.R.T. eliminates mechanically.

**The hash fingerprint correctly abstracts over the full schema.** Fingerprinting only the structural identity (`{fields: {name: type}, required: [...]}`) means the cache is invalidated when the contract changes (a field added, type changed, required status changed) but not when descriptions or constraint hints are updated. This is the right separation: structural identity for cache keying, full schema for runtime validation.

**The hash registry is sound.** SHA-256 + RFC 8785 canonicalization is deterministic, interoperable, and fully specified. The cold/warm distinction makes the 90% token reduction a real steady-state number.

**The SRA cycle is the right model.** It maps cleanly to an agent loop, the phases are well-separated, and §9.3 simulation requirements mean it's fully testable without a live LLM or network.

**RISE poll mode is practical.** Accommodating serverless/edge/browser environments that can't expose a callback endpoint is the right design. The polling resource as a standard H.E.A.R.T. resource is an elegant recursive application of the same protocol.

**409/428 recovery is the right safety valve.** Forcing a re-sync rather than a retry-from-stale prevents the exact race condition it addresses.

**The dual-profile `Content-Type` composition is clean.** Separating HAL Schema Forms (structural/schema layer) from the H.E.A.R.T. profile (fingerprint/registry/SRA layer) means the two concerns evolve independently and a server already serving HAL Schema Forms can adopt H.E.A.R.T. compliance incrementally.

---

### Where it will be hard in practice

**1. `rel` name quality is load-bearing and only partially mitigated.**
The SRA Reason step depends on the LLM mapping user intent to `rel` names. Full JSON Schema helps — `description` fields at the form and property level give the LLM semantic context beyond the rel name alone. But if a server publishes `rel: action-7` with no description, the LLM still has nothing to reason over. The protocol relies on server authors making good vocabulary choices. There is no governance, no rel registry, no minimum naming convention in V1. This is a reference implementation non-issue (you control both sides) but is the primary adoption friction for the protocol generally.

**2. The 428 mechanism has an unspecified transport.**
§6.2 says the server checks whether the client's manifest hash matches current server state and returns 428 if stale. §6.3 says the client SHALL re-sync on 428. But the spec does not define how the client sends its current manifest hash to the server in the act request — there is no defined request header or body field for it. The 428 path cannot be implemented without resolving this, and the resolution affects both sides. **This must be specified before Slice 15 is written.**

**3. Registry persistence is deferred but breaks the core metric without it.**
The 90% token reduction holds for the lifetime of a running process. On cold restart, the registry is empty and the first turn is a full cold-start fetch regardless of how many prior sessions have occurred. For serverless deployments (the same environments that need poll mode) this is zeroed on every invocation. §11 correctly defers persistence implementation details, but it means the flagship metric only holds in the demo unless persistence is addressed before production use.

**4. The 90% claim is steady-state only.**
The spec is careful about this — "from the second turn onward" — but the headline claim will be read as a per-request reduction. Turn 1 of a cold session against a server with many affordances could be more expensive than a minimal OpenAPI spec. The measurement protocol in §10 is correctly scoped to the warm condition.

**5. RISE fallback has a cold-directory bootstrap problem.**
The agent directory is populated during sync when a manifest contains `heart:agent` affordances. If the primary recipient times out before any sync has returned a `heart:agent` affordance, the directory is empty and fallback behaviour is undefined. The spec does not address this case.

**6. LLM re-sync cooperation must be enforced in code, not by the LLM.**
§6.3 says "Client SHALL re-sync before retrying" on 409/428. In the agentic pattern the client is an LLM reasoning over tool results. "SHALL re-sync" is a programmatic requirement that must be wired into the SRA cycle implementation as a deterministic code path triggered by status code — not left to LLM judgment. The reference implementation must enforce this unconditionally.

---

### Overall verdict

Within its stated scope — a reference implementation demonstrating the protocol on two controlled domains with simulated servers and scripted intent — this works well. The updated spec substantially closes the field-constraint gap (full JSON Schema, not a simplified type map), correctly specifies the media type as a composable dual-profile, and retains all the properties that make the core claims testable. The remaining gaps (428 transport, rel governance, persistence) are either explicitly deferred or are V1 adoption concerns rather than reference implementation blockers.

---

## Part 2 — Vertically Sliced Delivery Plan

Each slice delivers one runnable, testable, demonstrable capability end-to-end. The test for each slice fails before implementation and passes after. No horizontal layers.

Slices 0–19 cover the full H.E.A.R.T. protocol and are demonstrable independently of R.I.S.E.  
Slices 20–27 cover R.I.S.E.

---

### Slice 0 — Build skeleton

- Empty Kotlin project compiles
- Test runner wired (JUnit 5)
- Single smoke test passes (`1 == 1`)

**Done when:** `./gradlew test` is green

---

### Slice 1 — Media type model: parse a cold-start response

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

### Slice 2 — Hash canonicalization: extraction + known-answer test

Two-step process: normalize the full `_schema` down to the structural fingerprint, then hash it.

- Implement schema normalizer: extract `{fieldName: typeString}` from `_schema.properties` (take only `type`), producing `{fields: {...}, required: [...]}`
- Implement RFC 8785 canonicalization (recursive key sort, no whitespace)
- Implement SHA-256 hex digest over canonical bytes

**Tests:**
- The §6.5 example full schema (with `description`, `minimum`, `maximum`) produces the same fingerprint as the same schema stripped of those keywords
- Key insertion order doesn't affect the fingerprint — `{seedCount, cropType}` in `properties` == `{cropType, seedCount}`
- Changing a field's `type` produces a different fingerprint
- Adding a field to `required` produces a different fingerprint
- `rel`, `href`, `description`, `minimum`, `maximum`, `enum`, `format`, `pattern` are all excluded from fingerprint input

---

### Slice 3 — In-memory simulated server, Domain A (agriculture)

- State machine: `EMPTY → SEEDED → GROWING → HARVESTED`
- Each state returns a valid H.E.A.R.T. response with `Content-Type: application/hal+json; profile="https://github.com/jbadeau/hal-schema-forms https://github.com/jogoshugh/heart-rise"`
- Each affordance form carries a full `_schema` with realistic JSON Schema (descriptions, type constraints, `minimum` on counts, `enum` on categorical fields like crop type)
- Hash fingerprints are precomputed and stable

**Tests:**
- `EMPTY` state manifest contains `sow-seed`, does NOT contain `harvest`
- `GROWING` state manifest contains `harvest`, does NOT contain `sow-seed`
- `sow-seed` schema includes `required` fields and at least one constraint beyond type (e.g., `minimum: 1` on seed count)
- Transition request to an affordance not in the current manifest returns 404

---

### Slice 4 — Registry: cold start population

- Implement `AffordanceRegistry` with schema partition (hash → full `_schema`)

**Tests:**
- Registry starts empty; `contains(hash)` returns false
- `store(hash, schema)` followed by `contains(hash)` returns true
- `get(hash)` returns null on miss, full `_schema` on hit
- Re-storing the same hash is idempotent (no error, no duplicate)
- Registry stores the full `_schema` (not the normalised fingerprint input) — descriptions and constraints are preserved

---

### Slice 5 — Sync step: cold start populates registry

- Sync reads all affordance forms from a cold-start response
- Computes and verifies each `hash` matches the server-provided fingerprint
- Stores each full `_schema` under its hash

**Tests:**
- After sync against a 3-affordance response, registry contains exactly 3 entries
- Each stored schema preserves the full JSON Schema (descriptions, constraints, not just field names and types)
- Sync makes zero additional server requests (schemas were inline)
- If a received hash does not match the locally computed fingerprint for the same schema, sync raises a compliance error

---

### Slice 6 — Profile header construction

- Client generates `Accept-Profile` header value based on registry state

**Tests:**
- Cold start (empty registry) → header is `Accept-Profile: <https://github.com/jogoshugh/heart-rise>` with no `detail` param
- Warm (registry has known hashes) → header includes `param:detail=hash`
- `behaviour` and `domain` params are optional and combinable with `detail`

---

### Slice 7 — Simulated server: warm response (hashes only)

- Server inspects `Accept-Profile` header for `param:detail=hash`
- When present: affordance forms contain only `rel`, `href`, `hash` — no `_schema`
- When absent or missing: full `_schema` returned in every form

**Tests:**
- Cold response byte count > warm response byte count for the same state (schemas suppressed)
- Warm response still contains correct hash fingerprints
- Request without `Accept-Profile` returns full schemas (graceful degradation)
- Response `Content-Type` carries both profile URIs regardless of warm/cold mode

---

### Slice 8 — Sync step: warm, zero additional fetches

- Client sends warm profile header after cold start has populated registry
- Receives hash-only manifest; all hashes are known

**Tests:**
- Sync completes with zero additional requests
- Full `_schema` (with descriptions and constraints) still accessible in registry after warm sync — nothing was lost

---

### Slice 9 — Sync step: warm, one dirty affordance

- Server changes schema for one affordance (adds a new required field, which changes the structural fingerprint)
- Client has the old hash in registry

**Tests:**
- Sync detects exactly one dirty affordance (fingerprint mismatch)
- Sync fetches exactly one additional schema (only the dirty one)
- Registry updated with new full `_schema`; old hash superseded
- The two other affordances (clean hashes) produce zero additional fetches

---

### Slice 10 — Reason step (scripted intent resolver)

- Implement `IntentResolver` interface: `(intent: String, manifest: AffordanceManifest) → AffordanceForm?`
- Scripted implementation maps intent strings to rel names; has access to `_schema.description` for richer matching
- Returns null when no affordance in the current manifest matches

**Tests:**
- Intent "plant some seeds" against EMPTY manifest resolves to `sow-seed`
- Intent "harvest crops" against EMPTY manifest (no `harvest` affordance) returns null
- Intent matching a rel absent from the current manifest never returns that rel regardless of schema descriptions
- Resolver can use `_schema` description text — "plant kale" matches an affordance whose `_schema` description mentions cultivar selection

---

### Slice 11 — Act step: field population and JSON Schema validation

- Client selects an affordance, populates fields using the full `_schema` constraints
- Validates the populated payload against the full JSON Schema before submission (not just required-field check)
- Submits to the server-provided `href`
- Server returns next resource state

**Tests:**
- Submitted payload contains only fields declared in `properties`
- Required fields are all present
- A value violating `enum` constraint is rejected before submission (not sent to server)
- A value below `minimum` is rejected before submission
- `href` used is exactly the one from the manifest (logged for assertion)
- Server returns new state with updated manifest

---

### Slice 12 — Full SRA cycle: single turn, Domain A

- Cold start → sync → reason ("plant kale seeds") → act → receive SEEDED state

**Tests:**
- State transitions from EMPTY to SEEDED in one turn
- New manifest contains `water-plant`, does NOT contain `sow-seed`
- The `sow-seed` schema's `minimum` constraint was respected in the submitted payload

---

### Slice 13 — Full SRA cycle: multi-turn lifecycle, Domain A

- EMPTY → SEEDED → GROWING → HARVESTED (3 complete state transitions)

**Tests:**
- Each state advance produces the correct next manifest
- No affordance exercised that was absent from the manifest at the time of the act (logged and asserted for all turns)
- JSON Schema constraints respected in every submitted payload across all turns

---

### Slice 14 — 409 recovery: state conflict

- Simulated server configured to return 409 on first act (state changed between sync and act)
- Recovery is deterministic in code, not LLM-directed

**Tests:**
- First attempt gets 409
- Client re-syncs (one additional GET to resource root) without LLM involvement
- Retry with fresh manifest succeeds (200, state advances)

---

### Slice 15 — 428 recovery: stale manifest

> **Pre-condition:** Before writing this slice, add to the spec the mechanism by which the client communicates its current manifest hash to the server in an act request (e.g., `X-Heart-Manifest-Hash` request header or an envelope field in the request body). Pick one approach, specify it, and update both §6.2 and §6.3.

- Server verifies received hash matches current state; returns 428 on mismatch
- Recovery is deterministic in code, not LLM-directed

**Tests:**
- Client sends the manifest hash in the act request using the specified mechanism
- Server returns 428 when hash doesn't match current state
- Client re-syncs and retries successfully without LLM involvement

---

### Slice 16 — Domain B simulated server (sports scoring)

- State machine: `PRE-GAME → IN-PROGRESS → HALF-TIME → IN-PROGRESS → FINAL`
- Affordances: `start-game`, `score-point`, `end-half`, `end-game`
- Each form carries a realistic full `_schema` with appropriate constraints (e.g., `score-point` requires `team` as an enum of valid team names)

**Tests:**
- PRE-GAME manifest has `start-game`, not `score-point`
- IN-PROGRESS manifest has `score-point`, not `start-game`
- `score-point` schema's `team` field has an `enum` constraint; submitting an unknown team name is rejected before submission
- Full game lifecycle traversal (4 state transitions to FINAL)

---

### Slice 17 — Domain agnosticism: same client, two domains

- Run Slices 13 and 16 with the SAME client instance and shared registry

**Tests:**
- Agriculture schemas and sports schemas coexist in registry without hash collision
- Client enters Domain B from a bookmark with zero Domain A knowledge in prompt or config
- No Domain A rel name appears in any Domain B act
- Registry populated from Domain A warm session is not polluted into Domain B reasoning

---

### Slice 18 — Token cost measurement

- Instrument cold-start sync: count total bytes received (full `_schema` per affordance)
- Instrument warm turn: count bytes received (hashes only, no schemas)
- Produce a synthetic OpenAPI spec for Domain A with all affordances and their full schemas

**Tests:**
- Warm turn byte count ≤ 10% of equivalent OpenAPI spec byte count (90% steady-state reduction metric)
- Cold turn byte count documented and compared to the OpenAPI baseline (first-turn cost is higher; this is expected and must be stated honestly)

---

### Slice 19 — Structural hallucination proof (sweep)

- Interceptor records every act: `(affordance rel, manifest received in immediately preceding sync)`
- Run all SRA cycle tests across both domains

**Tests:**
- For every recorded act, the `rel` was present in the manifest received in the immediately preceding sync — zero violations across all tests and both domains
- JSON Schema validation rejections (enum, minimum, etc.) are recorded separately and counted as constraint enforcement, not hallucination events

---

### Slice 20 — Agent directory partition

- Server returns an affordance with `rel: heart:agent`, `href: <agent-endpoint>`
- Sync step detects `heart:agent` rels and stores them in the agent directory partition (rel → URI)

**Tests:**
- Agent directory starts empty
- After sync with a `heart:agent` affordance, directory contains the entry for that rel
- Lookup by rel returns the URI
- Agent directory is NOT consulted during the Reason step (it's only for RISE fallback)

---

### Slice 21 — RISE envelope construction

- Client constructs a RISE envelope from an affordance discovered during sync

**Tests:**
- `template-rel` equals the rel name from the manifest
- `template-hash` matches the registry fingerprint for that rel (the structural fingerprint, not the full schema)
- `template-fields` lists field names from `_schema.properties`
- Constructing an envelope from a rel NOT present in the current manifest raises an error
- `template-rel` cannot be set from out-of-band knowledge (must come from a manifest affordance)

---

### Slice 22 — RISE push mode: happy path

- Simulated RISE recipient: returns 202 Accepted immediately, does work async, POSTs callback
- Originator exposes an in-memory callback endpoint

**Tests:**
- 202 received; response body contains `reply-to` URI confirmation
- Callback arrives within timeout window
- Callback payload conforms to the declared template schema — all `template-fields` present, full JSON Schema constraints respected

---

### Slice 23 — RISE push mode: registry hit on recipient side

- Recipient's registry already contains the `template-hash` from a prior interaction

**Tests:**
- Recipient makes zero additional schema fetches
- Callback still correctly populated (descriptions and constraints from cached schema were used)

---

### Slice 24 — RISE push mode: registry miss on recipient side

- Recipient's registry is empty; receives envelope with `template-hash` it hasn't seen

**Tests:**
- Recipient fetches the full schema (one additional request)
- Full `_schema` stored in recipient registry after fetch (descriptions and constraints preserved)
- Callback correctly populated using the fetched schema

---

### Slice 25 — RISE poll mode: happy path

- Originator omits `reply-to`, sets `callback-mode: poll`

**Tests:**
- 202 response includes `Location` header pointing to polling resource
- Polling resource is a valid H.E.A.R.T. resource (parseable as `HeartResponse` with correct `Content-Type`)
- Before work completes, polling resource manifest does NOT contain `heart:result`
- After work completes, polling resource manifest DOES contain `heart:result`
- Client exercises `heart:result`, receives completed payload conforming to template schema

---

### Slice 26 — RISE fallback: timeout path

- Primary recipient configured to not respond within the non-response window
- Agent directory (populated in Slice 20) contains one fallback candidate with matching `template-rel`

**Tests:**
- Originator detects timeout
- Fallback recipient selected from agent directory by rel match
- Original envelope sent to fallback unchanged (no mutation)
- Callback arrives from fallback recipient
- Result object records fallback provenance (which agent responded)

---

### Slice 27 — RISE fallback: cold agent directory

- Primary times out; agent directory is empty (no prior sync returned a `heart:agent` affordance)

**Tests:**
- Originator returns a clear, typed error indicating no fallback available
- No undefined behaviour; no silent failure; no null pointer

---

## Delivery Notes

- Slices 0–19 are the complete H.E.A.R.T. core, demonstrable as a standalone before RISE is started.
- Slices 20–27 are R.I.S.E. They depend on a warm registry from H.E.A.R.T. slices but not on a live LLM.
- **Pre-condition on Slice 15:** The 428 transport mechanism (how the client sends its manifest hash in an act request) must be specified in the SPEC before that slice is implemented.
- Slice 18 (token measurement) and Slice 19 (hallucination sweep) are verification slices — they run across outputs of prior slices and require no new production code, only instrumentation.
- Slice 2's hash normalization step (extracting `{fieldName: typeString}` from full `_schema.properties`) is a critical shared primitive. Any bug here will cause fingerprint mismatches between client and server. Test it exhaustively before it is relied upon by Slices 5, 7, 8, 9.
