# H.E.A.R.T. + R.I.S.E. Protocol Suite
## With ARTIE Reference Client Implementation

**Version:** 0.3 — Working Draft  
**Status:** Pre-implementation  
**Author:** Joshua Gough  
**Date:** July 2026

**Changes since 0.2:** Replaced the `Accept-Profile; param:detail=hash` caching
mechanism with real content-addressed URLs plus RFC 7240 `Prefer:
embed=schemas` negotiation (§6.4–6.7), verified end-to-end against a live
server (see ADR). Made dual-dialect support (jbadeau HAL Schema Forms and
Amundsen HAL-FORMS) a first-class, named part of the media type layer
(§6.1) rather than an implicit assumption of one dialect.

---

## 1. Executive Summary

This document defines the requirements for the H.E.A.R.T. (Hypermedia Enforced Agentic Reliable Transactions) and R.I.S.E. (Reciprocal Interface for State Exchange) protocol suite, together with ARTIE (Autonomous Restful Traversal and Interactive Engine), the reference client implementation.

The suite addresses a fundamental architectural failure in the current agentic AI landscape: the use of large language models as deterministic routers over tightly coupled, out-of-band schema definitions. This approach produces systems that are expensive to operate, brittle under server evolution, and prone to hallucination. H.E.A.R.T. and R.I.S.E. correct this by applying Roy Fielding's REST constraints — specifically the uniform interface and hypermedia as the engine of application state — to agent-to-system and system-to-system integration.

The result is a protocol suite that reduces per-turn token cost, eliminates a primary class of agent hallucination, and enables server-side evolution without client redeployment.

---

## 2. Vision

A generic agent — one carrying no domain-specific knowledge — enters a system with a single URI and a known media type. The server responds with the current state of the world and the complete map of what is permitted right now. The agent reasons over that map, maps user intent to available affordances, and executes a transaction constrained entirely by the server-provided schema. The context window remains flat. The token cost is predictable and minimal. The server can evolve freely. The agent adapts at the next sync cycle.

The same agent, without code changes, can interact with a garden management system, a basketball scoring system, an insurance claims system, or any other domain — because the agent is coupled to the media type, not to the domain.

This is the web's original design applied to the agentic era.

---

## 3. Problem Statement

### 3.1 The Current State

Agentic AI systems in 2026 are overwhelmingly built on one of two patterns:

**Pattern A — OpenAPI tool calling.** The agent is provided with an API schema before any interaction with the server. The schema lives in the system prompt or in a tool definition file. The agent constructs requests from this prior knowledge. When the server evolves, the schema must be updated, the agent redeployed, and the context window reloaded. Token cost is high and fixed regardless of what the agent actually needs to do.

**Pattern B — MCP tool manifests.** A marginal improvement that makes tool definitions machine-readable but retains the fundamental coupling. Tool definitions are still out-of-band. The agent still knows what it can do before it asks the server. The server still cannot evolve without breaking the client.

Both patterns share a root cause: the client carries knowledge of the server's interface rather than discovering it at runtime from the server's responses. This is what Roy Fielding identified in 2008 as the defining failure of so-called REST APIs. It remains unaddressed in the agentic context.

### 3.2 The Consequences

**Token cost.** A 50,000-token OpenAPI spec loaded into every agent context window at every session is a fixed tax. Multiply by request volume and the cost is significant. The spec is loaded whether the agent uses ten endpoints or one.

**Hallucination.** When the agent constructs requests from prior schema knowledge rather than server-provided affordances, it operates on potentially stale information. Field names change. Endpoints move. Business rules evolve. The agent does not know. It guesses. It constructs plausible-but-invalid requests.

**Brittleness.** Server evolution requires client redeployment. This creates coordination overhead, versioning complexity, and the inevitable breaking changes that consume engineering time and create instability.

**Context rot.** Conversation history accumulates across turns. The context window fills with stale state. Reasoning quality degrades. Cost increases.

### 3.3 The Root Cause

Fielding named it in 2008: if the engine of application state is not being driven by hypertext then it cannot be RESTful. The industry built APIs that are not RESTful in this sense. It then built agents on top of those APIs and compounded the problem.

The root cause is the absence of a media type that carries both resource state and available transitions simultaneously, with embedded schema constraints, discoverable at runtime from the server's response alone.

---

## 4. Goals

- Define a media type layered into resource state, affordance set, and schema, with state always linking to its current affordances even when not delivered inline
- Define compliant server behaviour that enforces business rules through affordance presence and absence
- Define compliant client behaviour that enters with a bookmark and discovers everything at runtime
- Define a content-addressing procedure for affordance sets and schemas, with a canonical computation procedure
- Define a caching model, built on standard HTTP semantics, that eliminates redundant schema fetches
- Define the Sync-Reason-Act cycle as the agent's processing model, including the three-layer sync walk
- Support two base hypermedia-forms dialects (HAL Schema Forms and HAL-FORMS) from one shared source of truth
- Define the RISE envelope pattern for asynchronous reciprocal interaction
- Define push and poll callback modes for RISE
- Define a content negotiation mechanism (`Accept` for dialect, `Prefer` for rendering detail) that reduces response weight for warm clients
- Produce a reference implementation — ARTIE — that demonstrates all of the above across two unrelated domains
- Demonstrate measurable per-turn token cost reduction versus OpenAPI tool calling baseline
- Demonstrate zero calls to affordances not present in the current manifest

---

## 5. Non-Goals

- Retrofitting existing non-compliant APIs
- Replacing AsyncAPI, OpenAPI, or gRPC in their existing use cases
- Defining a general purpose agent framework
- Solving hallucination in the semantic reasoning step — H.E.A.R.T. constrains structural hallucination only
- Prescribing server implementation technology
- YAML and Markdown serialisation profiles (deferred to V2)
- Authentication and authorisation mechanisms
- Rate limiting and throttling
- Multi-agent coordination beyond two-party RISE

---

## 6. Protocol Requirements

### 6.1 Media Type and Dialects

H.E.A.R.T. is a layer over a base hypermedia-forms dialect, not a single fixed media type. Two base dialects are recognised, selected by ordinary HTTP content negotiation (`Accept`), never by a H.E.A.R.T.-specific parameter:

**HAL Schema Forms dialect** (`https://github.com/jbadeau/hal-schema-forms`):
```
Accept: application/hal+json; profile="https://github.com/jbadeau/hal-schema-forms https://github.com/jogoshugh/heart-rise"
```
Uses the `_forms` key. Full JSON Schema per field. Schema may be given by reference (`"schema": {"$id": "..."}`) or inline, per §6.4.

**HAL-FORMS dialect** (Amundsen's `application/prs.hal-forms+json`, the format Spring HATEOAS and other toolchains already ship natively):
```
Accept: application/hal+json, application/prs.hal-forms+json; profile="https://github.com/jogoshugh/heart-rise"
```
Uses the `_templates` key. Amundsen's inline `properties` array (`name`, `type`, `required`, `readOnly`, etc.), not JSON Schema.

A server SHOULD support both dialects for the same resource, generated from one shared source of truth for "which affordances are currently legal" — the two dialects MUST NOT be allowed to drift into disagreeing about that. A server that varies its response by `Accept` SHALL send `Vary: Accept`.

**The `heart-rise` profile is dialect-independent.** It never replaces or modifies either base dialect's own structure; it only adds: the three-layer content-addressing model (§6.2), `Prefer`-based rendering negotiation (§6.4), and the Sync-Reason-Act processing model (§6.8). A server already serving either base dialect can adopt H.E.A.R.T. compliance incrementally by adding the layering and the `heart-rise` profile URI, without changing its dialect's own wire format.

A conformant H.E.A.R.T. response is organised into three layers, each with its own identity and caching contract. This replaces 0.2's single flat "affordance form with an inline hash field":

| Layer | Content | Identity | `Cache-Control` | Revalidation |
|---|---|---|---|---|
| **1 — Resource** | Domain state + a link to the current affordance set | Stable resource URL | `no-cache` | ETag every request; `304` if unchanged |
| **2 — Affordance Set** | The dialect's manifest (`_forms` or `_templates`) for the current state | Content-addressed URL: `sha256-{hash}` of the set's own canonical bytes | `public, max-age=31536000, immutable` | None — the URL itself is proof of content |
| **3 — Schema** | Per-field definition for one affordance (JSON Schema, or a HAL-FORMS `properties` fragment) | Content-addressed URL, same scheme as Layer 2 | `public, max-age=31536000, immutable` | None |

**Resource (Layer 1).** Domain data for the current state, plus a link to where its current affordances live right now — not the affordances themselves inline. SHALL be revalidated on every request (standard `ETag`/`If-None-Match`); this layer changes, so it is never content-addressed.

**Affordance Set (Layer 2).** The complete set of currently-legal transitions, in whichever dialect was negotiated via `Accept` (§6.1). Genuinely shared across every resource currently in the same state — two unrelated resources in the same state point at the identical URL, fetched once. Each affordance entry within it SHALL contain, at minimum: a `rel`/name, a target `href` (which MAY be a URI Template — see §6.4), an HTTP method, and either an inline schema or a schema-by-reference pointer, depending on the `Prefer` header (§6.4).

**Schema (Layer 3).** The canonical per-field definition — full JSON Schema vocabulary for the HAL Schema Forms dialect (`type`, `enum`, `minimum`, `maximum`, `pattern`, `format`, `description`, and any other standard keyword H.E.A.R.T. does not restrict), or Amundsen's `properties` fragment for the HAL-FORMS dialect. Each dialect's Layer 3 artifact is its own distinct content, independently content-addressed — a JSON Schema document and a HAL-FORMS properties fragment for the same field are not interchangeable representations of one canonical hash; they are two different, correctly different, addresses.

**Layering, not atomicity.** 0.2 required resource state and the full affordance manifest in one response. That's replaced: Layer 1 SHALL always carry at least a pointer to the current Layer 2 resource (never state with no way to discover current affordances), but the manifest itself, and its schemas, are fetched — or embedded, via `Prefer` — separately. Delivering Layer 1 with no route to a current Layer 2 at all remains non-compliant.

### 6.2 Compliant Server Behaviour

A H.E.A.R.T. compliant server SHALL:

- Serve Layer 1 with, at minimum, a link to the current Layer 2 resource for this state
- Vary the Layer 2 affordance set based on current domain state
- Serve Layer 2 and Layer 3 at content-addressed URLs computed per §6.5, with `Cache-Control: public, max-age=31536000, immutable`
- Serve Layer 1 with `Cache-Control: no-cache` and a correct `ETag`, honouring conditional requests with `304`
- Reject transitions not present in the current Layer 2 affordance set
- Support `Accept`-based dialect negotiation (§6.1) and send `Vary: Accept` when the response varies by it
- Support `Prefer: embed=schemas` (§6.4) on Layer 1 and Layer 2 requests, and send `Preference-Applied` when honoured
- If serving both dialects, generate both from one shared source of truth for which affordances are currently legal — never two independently-maintained descriptions that could silently disagree

Business rules SHALL be enforced through affordance presence and absence. An action not present in the current manifest CANNOT be exercised. This is the primary mechanism for structural reliability.

**Error response codes.** A compliant server SHALL use the following status codes:

| Code | Meaning in H.E.A.R.T. context |
|------|-------------------------------|
| 403 Forbidden | Affordance exists in the schema vocabulary but business rules prohibit it in the current state. The manifest correctly omits it. |
| 404 Not Found | The resource no longer exists. |
| 405 Method Not Allowed | Client used the wrong HTTP method for the given affordance. |
| 409 Conflict | State machine transition conflict — the resource moved between the client's sync and its act. Client SHOULD re-sync. |
| 422 Unprocessable Entity | Schema validation failure — required field missing or type mismatch. |
| 428 Precondition Required | Client attempted a transition using a stale manifest. The manifest hash in the request does not match the current server state. Client SHALL re-sync before retrying. |

**Note on 428 under content addressing:** since Layer 2/3 URLs are immutable by construction, a client can never hold a "stale" schema for a manifest it already fetched — it either has the current content-addressed URL or it doesn't yet. Whether 428 still has a distinct role under this model, versus collapsing into 409 (resource moved) or 404 (target no longer legal), is an open question — see §12. Nothing above changes 409's meaning; it is unaffected by this revision.

### 6.3 Compliant Client Behaviour

A H.E.A.R.T. compliant client SHALL:

- Enter any interaction with a single bookmark URI and no prior domain knowledge
- Negotiate dialect via `Accept`, never via a H.E.A.R.T.-specific parameter (§6.1)
- Request Layer 2/3 content normally (pointer form) unless it specifically wants everything collapsed into one round trip, in which case it sends `Prefer: embed=schemas` (§6.4)
- Rely on ordinary HTTP caching for Layer 2/3 — an RFC 7234-compliant cache is sufficient; no bespoke fingerprint-comparison logic is required
- Always revalidate Layer 1 (send `If-None-Match` once an `ETag` is held)
- Reason only over affordances present in the current Layer 2 affordance set
- Populate form fields within the constraints declared by the Layer 3 schema
- Never construct a URI from prior knowledge — including never guessing a content-addressed URL; it must come from a Layer 1 or Layer 2 link
- On receipt of a 409 (or 428, pending §12), re-sync before retrying the transition

### 6.4 Content Negotiation

H.E.A.R.T. uses two independent, standard HTTP mechanisms — not a single custom parameter — because dialect choice and rendering detail are orthogonal concerns:

**`Accept` selects the dialect (§6.1).** Ordinary HTTP content negotiation between genuinely different media types/profiles, not a H.E.A.R.T. parameter:
```
Accept: application/hal+json; profile="https://github.com/jbadeau/hal-schema-forms https://github.com/jogoshugh/heart-rise"
```
```
Accept: application/hal+json, application/prs.hal-forms+json; profile="https://github.com/jogoshugh/heart-rise"
```

**`Prefer: embed=schemas` (RFC 7240) selects rendering detail**, independent of dialect:
- Absent (default) — Layer 1 links to Layer 2; Layer 2's schema fields are pointers (`$id`/reference) to Layer 3. Minimal payload, maximum cache reuse across resources/affordances sharing a set or schema.
- Present — the server collapses all three layers into one response: full Layer 2 manifest with Layer 3 schemas expanded inline. The server SHALL echo `Preference-Applied: embed=schemas` when honoured. This is the RFC 7240 "give me everything, save me the round trips" case, spanning multiple hops instead of one.

Embedding is a rendering choice, never a change of identity — a Layer 2 resource's content-addressed hash (§6.5) is computed over its canonical, never-embedded form, so the embedded and pointer renderings of the same affordance set share one hash and one URL.

The `Accept-Profile` header remains available for the two concerns unrelated to caching or dialect:

```
Accept-Profile: <https://github.com/jogoshugh/heart-rise>; param:behaviour=autonomous; param:domain="https://example.org/vocab/farming"
```

| Parameter | Values | Effect |
|-----------|--------|--------|
| `behaviour` | `interactive` (default), `autonomous` | Signals whether a human is available to clarify ambiguous intent; servers MAY use this to omit affordances requiring disambiguation |
| `domain` | URI | Declares familiarity with a specific domain vocabulary; servers MAY omit explanatory metadata for known rel names |

`detail=hash` from 0.2 is removed entirely — its job is now done by content-addressed URLs plus ordinary HTTP caching, not a request parameter.

Graceful degradation SHALL be supported. A request without `Accept-Profile` SHALL receive a full, dialect-defaulted response. A `406 Not Acceptable` SHALL be returned only when no acceptable dialect can be produced.

### 6.5 Content Addressing

A H.E.A.R.T. content address is a deterministic identifier for the *actual bytes* of a Layer 2 or Layer 3 resource. All compliant implementations MUST use the same procedure to ensure interoperability.

**Algorithm:** SHA-256 over the full, minified, canonical JSON body — not a reduced structural projection. This is a deliberate change from 0.2, which hashed only a stripped `{fields, required}` projection so cosmetic changes (a `description` edit) wouldn't invalidate the cache. That property is given up here, on purpose: a content address is only trustworthy — "this URL is proof the bytes beneath it cannot have changed" — if *any* byte difference produces a different address. A fingerprint that ignores some bytes cannot make that guarantee. The trade-off: editing a schema's `description` now does mint a new URL, exactly like editing any other byte of the content would.

**Canonicalization:** JSON Canonicalization Scheme per RFC 8785 (recursively sorted keys, no insignificant whitespace) applied to the full body before hashing — not to a subset of it.

**Scope of "the body" for Layer 2:** the affordance set's canonical, never-embedded form — schema fields as pointers (`{"$id": "..."}"`), not inlined. This is what makes embedding (§6.4) a rendering choice rather than an identity change: two requests for the same Layer 2 resource, one plain and one with `Prefer: embed=schemas`, share one hash and one URL, because the hash is computed over the pointer form regardless of which rendering is actually served.

**Scope of "the body" for Layer 3:** the schema's own raw content, verbatim — the full JSON Schema document, or the full HAL-FORMS `properties` fragment.

**Output:** Lowercase hexadecimal digest, used directly as the final URL path segment: `/affordance-sets/sha256-{digest}`, `/schemas/sha256-{digest}` (a server MAY truncate the digest for URL readability, e.g. 16 hex characters, at the cost of collision resistance below full SHA-256 — production use SHOULD use the full digest).

Implementations producing different addresses from byte-identical content are non-compliant.

### 6.6 Caching

There is no bespoke client-side affordance registry keyed by fingerprint in 0.3. Layer 2 and Layer 3 caching is standard HTTP: any RFC 7234-compliant HTTP cache correctly caches content-addressed URLs forever, with zero H.E.A.R.T.-specific logic. A client SHOULD use a persistent HTTP cache to carry this benefit across sessions, exactly as it would for any other immutable static asset.

One piece of genuine client-side state remains, unrelated to caching:

**Agent directory.** Maps rel name to agent endpoint URI. Populated from affordances with the `heart:agent` rel (see §7.6). Used by RISE fallback selection. This is a real client-side registry — it isn't standard HTTP caching, because it isn't a cache; it's a directory of known agents.

### 6.7 Sync Behaviour

The sync step is the first step of every Sync-Reason-Act cycle. Under the three-layer model it is a walk, not a single fetch:

The sync step SHALL:

- Fetch Layer 1 (always revalidated; expect `304` if state hasn't changed)
- Follow Layer 1's link to Layer 2 — a normal GET, satisfied from the HTTP cache if this exact content-addressed URL was seen before, from the network otherwise
- For each affordance in Layer 2 needing its schema, follow the Layer 3 pointer — likewise cache-or-network, fetched in parallel across affordances since none depend on each other
- Deduplicate by URL before fanning out in parallel — two affordances that happen to share one schema URL (same content, same address) MUST NOT both trigger a network request; resolve to one in-flight fetch per unique URL, not one per affordance
- Complete with zero additional network requests, on any resource, for any Layer 2/3 URL already held in cache

### 6.8 Sync-Reason-Act Cycle

The agent's processing model SHALL follow three steps in sequence:

**Sync.** Walk all three layers (§6.7): fetch the resource, follow its link to the current affordance set, resolve any schema not already held in cache. The agent SHALL NOT reason until the manifest is fully resolved.

**Reason.** Map user intent to available affordances using the rel names as semantic anchors. The LLM SHALL operate only on affordances present in the current manifest. When intent is ambiguous or no affordance matches the agent SHALL seek clarification from the user rather than guess.

**Act.** Populate the selected affordance form within the declared schema constraints. Submit to the server-provided href. Receive the next resource state and manifest. Restart the cycle.

**Context window scope.** The H.E.A.R.T. constraint applies to domain knowledge, not to conversation history. The current resource state and manifest SHALL be present in the context window. Conversation history — what the user asked, which transitions were taken — is managed by the agent framework separately from the H.E.A.R.T. manifest. The agent framework is responsible for preventing context rot in the conversation layer; H.E.A.R.T. is responsible for keeping the domain knowledge layer flat and current.

---

## 7. R.I.S.E. Requirements

R.I.S.E. is a specialisation of H.E.A.R.T. for asynchronous reciprocal interaction. It is not a separate protocol. It extends the H.E.A.R.T. media type with a standard vocabulary of async rel names.

### 7.1 Core Principle

In a RISE interaction, client and server are roles not identities. The originator begins as client. The recipient begins as server. When the recipient's work is complete the roles reverse. The recipient becomes client. The originator becomes server. The uniform interface applies in both directions.

### 7.2 The RISE Envelope

The RISE envelope is a media type construct carried in the initial request. It is the self-addressed stamped envelope.

The envelope SHALL contain:

- `reply-to` — URI at which the originator will receive the callback (push mode), or omitted in poll mode
- `callback-mode` — `push` or `poll`; if omitted, `push` is assumed when `reply-to` is present, `poll` when it is absent
- `template-rel` — semantic relation name of the expected response; SHALL be a rel name discovered from a prior H.E.A.R.T. sync, never from out-of-band knowledge
- `template-hash` — content address of the template schema (§6.5), enabling the recipient to check its HTTP cache before fetching, and to fetch it directly (it's a URL, not an opaque fingerprint) on a miss
- `template-fields` — list of field names the originator understands

The envelope SHALL embed sufficient information for the recipient to construct a valid callback without any out-of-band schema knowledge.

### 7.3 RISE Request Behaviour

A RISE compliant originator SHALL:

- Construct an envelope from affordances discovered in the current H.E.A.R.T. session
- Never populate `template-rel` or `template-hash` from out-of-band knowledge
- Select callback mode based on its deployment environment (see §7.7)
- Attach the envelope to the outbound request
- Not block on the response

### 7.4 RISE Recipient Behaviour

A RISE compliant recipient SHALL:

- Acknowledge receipt immediately with 202 Accepted
- In push mode: include the originator-provided `reply-to` URI in the response body for confirmation
- In poll mode: include a `Location` header pointing to a polling resource (see §7.7)
- Perform work asynchronously
- Resolve the envelope's `template-hash` as a content-addressed URL (§6.5) and rely on ordinary HTTP caching
- Fetch the template schema only on cache miss
- Map its domain state onto the declared template fields using rel names as semantic anchors
- Submit the callback to the reply-to URI (push) or post the result to the polling resource (poll)
- Never expose its internal domain model in the callback

### 7.5 RISE Rel Name Vocabulary

The core RISE vocabulary is minimal:

- `heart:agent` — identifies an agent endpoint available for async work or fallback; stored in the agent directory (§6.6)
- `reply-to` — success callback shape
- `error-reply` — failure callback shape
- `progress` — optional intermediate status update
- `heart:result` — polling resource affordance indicating a completed async result is available

Domain-specific rel names SHALL be defined in domain profiles layered on top of the core vocabulary. Domain rel names SHALL NOT be added to the core vocabulary.

### 7.6 RISE Fallback Behaviour

A RISE compliant originator SHALL:

- Define a non-response window after which fallback is triggered
- Select a fallback recipient from the agent directory (see §6.6)
- Agents are registered in the directory when a manifest contains affordances with the `heart:agent` rel; the originator SHALL populate its agent directory during the sync step of any H.E.A.R.T. session that returns `heart:agent` affordances
- Reuse the original envelope unchanged for the fallback request
- Accept a callback from the fallback recipient in the same declared template shape
- Record fallback provenance in the result

A fallback recipient is selected from the agent directory by matching `template-rel` — any registered agent that advertises support for the requested rel name is a candidate. If multiple candidates exist, selection is implementation-defined.

### 7.7 RISE Callback Modes

Two callback modes are defined to accommodate environments where the originator cannot expose a reachable endpoint.

**Push mode.** The originator exposes a live endpoint. It populates `reply-to` with that endpoint's URI and sets `callback-mode: push`. The recipient POSTs the result directly to `reply-to` when work is complete. Push mode is preferred when the originator is a server-side process or long-running service.

**Poll mode.** The originator cannot expose a live endpoint (edge, serverless, mobile, browser). It omits `reply-to` and sets `callback-mode: poll`. The recipient's 202 response SHALL include a `Location` header pointing to a polling resource. The polling resource is a standard H.E.A.R.T. resource. The originator polls it using the Sync-Reason-Act cycle. When work is complete, the polling resource's manifest SHALL include a `heart:result` affordance. The originator exercises `heart:result` to retrieve the completed work. The polling resource SHALL remain available for at least the declared non-response window.

Poll interval is not prescribed. The originator SHOULD use exponential backoff. The polling resource SHOULD return a `Retry-After` header as a hint.

### 7.8 Shared Caching

The HTTP cache (§6.6) SHALL be shared between H.E.A.R.T. and RISE processing. A schema cached during a H.E.A.R.T. sync step SHALL be available to RISE envelope processing without re-fetch, and vice versa — both are the same content-addressed URLs behind the same cache, not two separate stores to keep in sync. The agent directory (§6.6) remains a distinct, non-cache partition, populated only via `heart:agent` affordances.

---

## 8. Relationship to Prior Art

H.E.A.R.T. is distinct from but informed by the following:

**HAL+JSON and HAL Schema Forms.** H.E.A.R.T. is built directly on top of `application/hal+json` and the HAL Schema Forms specification (`https://github.com/jbadeau/hal-schema-forms`) as one of its two supported base dialects (§6.1). HAL Schema Forms provides the `_forms` structure and full JSON Schema field definitions. H.E.A.R.T. does not replace or extend this layer. A server already serving HAL Schema Forms responses can evolve toward H.E.A.R.T. compliance incrementally, by adopting the three-layer content-addressing model (§6.2) and declaring the second profile URI in its `Content-Type`.

**HAL-FORMS (Amundsen).** H.E.A.R.T.'s other supported base dialect, `application/prs.hal-forms+json`, using the `_templates` key and inline `properties` fragments rather than JSON Schema (§6.1). This is the format Spring HATEOAS and comparable toolchains already produce natively, so it is a first-class, equally-supported dialect, not a fallback — a server may support either or both from one shared source of truth for current affordances.

H.E.A.R.T.'s own additions, on top of whichever base dialect is negotiated, are: content-addressed schema/affordance-set identity (§6.5), `Prefer`-based rendering negotiation (§6.4), and the Sync-Reason-Act processing model (§6.8).

**ALPS (Application-Level Profile Semantics).** H.E.A.R.T. rel names serve a similar purpose to ALPS descriptors — they provide a semantic vocabulary for transitions. H.E.A.R.T. does not require ALPS compliance but does not preclude it. A domain profile MAY reference an ALPS document as its vocabulary definition.

**W3C Content Negotiation by Profile.** The `Accept-Profile` header remains available for behaviour/domain signalling (§6.4) using the W3C working draft's header and parameter syntax. Dialect and rendering-detail negotiation, which 0.2 also routed through `Accept-Profile`, now use plain `Accept` and `Prefer` respectively (§6.4) — ordinary HTTP mechanisms, not H.E.A.R.T.-specific ones.

**RFC 7240 (Prefer).** Used for the embed-vs-reference rendering choice (§6.4). H.E.A.R.T. does not extend RFC 7240; `embed=schemas` is a plain preference value like any other.

**JSON Canonicalization Scheme (RFC 8785).** Used as the canonicalization step in content-address computation (§6.5). H.E.A.R.T. does not extend RFC 8785.

---

## 9. ARTIE — Reference Client

ARTIE is the reference implementation of a H.E.A.R.T. and RISE compliant client. ARTIE demonstrates that a single generic agent, carrying no domain-specific knowledge, can interact correctly with any H.E.A.R.T. compliant server.

### 9.1 ARTIE Requirements

ARTIE SHALL:

- Enter any H.E.A.R.T. compliant server with a single URI and no prior domain knowledge
- Execute the Sync-Reason-Act cycle correctly across the three-layer walk (§6.7)
- Use a persistent HTTP cache across sessions for Layer 2/3 content (§6.6) — no bespoke registry
- Negotiate dialect via `Accept` and rendering detail via `Prefer` on every request (§6.1, §6.4)
- Operate correctly against both supported dialects — HAL Schema Forms and HAL-FORMS — including a server that offers both
- Operate in interactive mode — seeking clarification from the user on ambiguous intent
- Support RISE envelope construction and callback reception in both push and poll modes
- Demonstrate correct fallback behaviour on recipient timeout
- Produce measurable per-turn token cost comparison versus OpenAPI baseline

### 9.2 Reference Domains

ARTIE SHALL be demonstrated against two unrelated domains to prove domain agnosticism:

**Domain A — Agricultural management.** A garden bed cell state machine with affordances that vary by plant lifecycle state. Demonstrates synchronous H.E.A.R.T. interaction and RISE async processing via remote vision agents.

**Domain B — Sports scoring.** A real-time game state machine with affordances that vary by game phase. Demonstrates that the same ARTIE instance, without modification, navigates a completely unrelated domain correctly.

The two domains are chosen for maximum contrast. Their only shared characteristic is H.E.A.R.T. compliance. ARTIE's ability to interact correctly with both from a single bookmark demonstrates the generic interface working as designed.

### 9.3 Simulation Requirements

All protocol behaviour SHALL be demonstrable via in-memory simulation with no external dependencies:

- Simulated server with scripted state machine
- Scripted intent resolution replacing live LLM calls
- Simulated async recipients with configurable timeout behaviour
- Full cycle verification producing correct state transitions
- Cache hit and miss verification for Layer 2/3 content-addressed URLs
- Warm-cache lean request verification (zero schema fetches against a request log, not client-side timing)
- Cold start full-fetch verification
- Cross-resource sharing verification (two unrelated resources in the same state resolve to one shared Layer 2/3 fetch, not one each)
- Fallback path completion verification (push and poll modes)

---

## 10. Success Metrics

**Per-turn token cost.** Target: 90% reduction versus the baseline. Baseline definition: a complete OpenAPI specification document for the equivalent domain provided in full at every session turn, with all endpoints included. Measurement: tokens consumed per turn from the second turn onward, against an ARTIE interaction with a warm HTTP cache (all Layer 2/3 URLs for the affordances in play already held). The warm-cache condition represents the steady-state for any session longer than one turn.

**Structural hallucination.** Zero calls to affordances not present in the current manifest. H.E.A.R.T. does not constrain semantic misidentification — the LLM may still select the wrong affordance from the manifest — but it eliminates the class of errors where the agent invokes an endpoint that does not exist or is not currently available.

**Server evolution adaptability.** Agent adapts to manifest changes within one sync cycle with zero client code changes.

**Caching efficiency.** Zero schema/affordance-set fetches on any subsequent cycle referencing a Layer 2/3 URL already held in cache, verified against the server's own request log — not client-side timing or assumption.

**Domain agnosticism.** ARTIE interacts correctly with both reference domains from bookmark entry with no domain-specific configuration.

---

## 11. Out of Scope for V1

- YAML serialisation profile (deferred to V2 as an additional profile URI layered on `application/hal+json`)
- Markdown serialisation profile (deferred to V2 as an additional profile URI layered on `application/hal+json`)
- Registry persistence implementation details
- LLM provider selection
- Server implementation framework
- Transport layer below HTTP
- Authentication and authorisation mechanisms
- Rate limiting and throttling
- Multi-agent coordination beyond two-party RISE

---

## 12. Open Questions

- Whether 428 retains a distinct meaning under content addressing, or collapses into 409 (resource moved between sync and act) or 404 (target no longer legal) — see §6.2. Content-addressed Layer 2/3 URLs cannot themselves go stale, so 428's original "manifest hash mismatch" trigger may no longer have a case to fire on.
- Truncated (16-hex-char) versus full SHA-256 digests for content-addressed URLs (§6.5) — readability versus collision resistance at scale.
- Whether a real multiformats/CID-style identifier (with a multicodec prefix declaring "this is JSON Schema" vs. "this is a HAL-FORMS fragment") is worth adopting over a bare `sha256-` prefix plus dialect-named path segment.
- HTTP cache behaviour under `Vary: Accept` for dialect negotiation — verified server-side; client-side cache-key correctness depends on the client's HTTP cache implementation honouring `Vary` per RFC 7234, which SHOULD be confirmed against whatever cache library a given ARTIE implementation uses, not assumed.
- Registry (now: HTTP cache) persistence strategy across sessions and devices
- Standardisation path for H.E.A.R.T. media type registration with IANA
- Formal definition of rel name vocabulary governance
- Versioning strategy for the core media type
- Whether the `heart:agent` rel and agent directory pattern is sufficient for multi-party RISE or requires a separate discovery mechanism

---

## 13. Architectural Alignment

This protocol suite is explicitly grounded in Roy Fielding's REST constraints as stated in his 2000 dissertation and his 2008 blog post REST APIs Must Be Hypertext-Driven. Every requirement in this document traces to one or more of Fielding's six principles:

- Protocol independence
- No private protocol extensions
- Media types as the descriptive effort
- No fixed resource names or hierarchies
- No implementation details exposed to clients
- Entry with bookmark and known media types only

H.E.A.R.T. and RISE are not an interpretation of these principles. They are a direct implementation of them in the context of agentic AI systems.