# Slice 2 Pre-Analysis

---

## What the plan said

**Slice 2 — Hash canonicalization: extraction + known-answer test**

- Implement schema normalizer: extract `{fieldName: typeString}` from `_schema.properties`, producing `{fields: {...}, required: [...]}`
- Implement RFC 8785 canonicalization (recursive key sort, no whitespace)
- Implement SHA-256 hex digest over canonical bytes

Tests covering: known-answer fingerprint stability, key-order independence, fingerprint change on type change, fingerprint change on required change, exclusion of non-structural keywords.

---

## Why we're not doing that next

Hash canonicalization is an internal primitive — not a `HeartRiseClient` public operation. The client needs it eventually for two things: verifying received hashes against locally computed fingerprints (Slice 5) and building the warm `Accept-Profile` header (Slice 6). Neither of those is needed for a bare-min `enter` + `act` cycle. Building a hashing primitive before the client can actually act on an affordance is horizontal, not vertical.

Slice 1 produced `enter`. The natural next move is `act`. The client interface has two methods; only one of them works.

---

## The London / Chicago question

Considered whether going deeper on the client creates a TDD style conflict.

**Current position (London/mockist):** `FakeTransport` queues pre-canned `ServerResponse` values. Step definitions enqueue a response, call `client.enter()`, and assert on the result. The server is a fiction — a queue of answers.

**The classical alternative:** An in-memory state machine server (the garden bed: EMPTY → SEEDED → GROWING → HARVESTED) that actually handles requests and returns the correct next state. This tests that the client submits to the right `href` with the right payload, not just that it processes a response correctly.

**Decision for this slice:** Stay with FakeTransport and enqueue two responses — one for `enter`, one for `act`. This is not dishonest. It correctly tests the client's behavior at the right boundary: does it read the form from the current `HeartRepresentation`, submit to the transport with the correct href and method, and process the response into a new `HeartRepresentation`? FakeTransport answers that question cleanly.

The in-memory state machine server belongs in the original Slice 3. That is the right moment to introduce a real server-side collaborator. Building it now would be building the server before the client is fully exercised.

---

## What `act` actually is at bare minimum

`act` is not complex. At the protocol level it is:

1. Look up the `AffordanceForm` for the given `rel` from `current`
2. Validate the payload against the form's schema (bare minimum: required field presence)
3. Submit to `form.href` via `form.method` via the transport
4. Parse the response into a new `HeartRepresentation`, update `current`
5. Return it

No hashing. No warm/cold. No registry. Just: the server gave you a form, you submit a valid payload to it, you get back the next state and the next manifest.

---

## What is missing before `act` can be implemented

`AffordanceForm` currently has no `method` field. The transport's `submit()` takes a method string — the client has to know whether the affordance is a GET, POST, PUT, DELETE, etc. That comes from the affordance form, which means the parser must extract it and the model must carry it.

This was an omission in the original plan. It must be added before `act` can dispatch correctly.

---

## Plan for this slice

1. Add `method: String` to `AffordanceForm`
2. Update `HeartResponseParser` to extract `method` from each affordance form
3. Implement `ArtieClient.act()` — look up form, submit via transport, parse response
4. Write `act.feature` covering a two-step cycle: `enter` receives a manifest, `act` exercises one affordance, client holds the new state and new manifest
5. Wire with FakeTransport — two enqueued responses, no HTTP, no real server

The feature file should remain protocol-neutral: "client", "affordance", "the service" — no garden bed yet. Domain-specific scenarios come with the in-memory server in the next slice.
