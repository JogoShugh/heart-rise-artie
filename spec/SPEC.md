# H.E.A.R.T. + R.I.S.E. Protocol Suite
## With ARTIE Reference Client Implementation

**Version:** 0.2 — Working Draft  
**Status:** Pre-implementation  
**Author:** Joshua Gough  
**Date:** June 2026

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

- Define a media type that carries resource state and affordances as an inseparable unit
- Define compliant server behaviour that enforces business rules through affordance presence and absence
- Define compliant client behaviour that enters with a bookmark and discovers everything at runtime
- Define a hash-based schema fingerprint with a canonical computation procedure
- Define a hash-based registry that eliminates redundant schema fetches
- Define the Sync-Reason-Act cycle as the agent's processing model
- Define the RISE envelope pattern for asynchronous reciprocal interaction
- Define push and poll callback modes for RISE
- Define a profile negotiation mechanism that reduces response weight for warm clients
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

### 6.1 Media Type

The H.E.A.R.T. V1 media type is `application/hal+json` with a dual-profile parameter per RFC 6906 and RFC 9110:

```
application/hal+json; profile="https://github.com/jbadeau/hal-schema-forms https://github.com/jogoshugh/heart-rise"
```

Multiple profiles SHALL be declared as a space-separated list in a single quoted `profile` parameter. Both profiles MUST be present for a response to be considered H.E.A.R.T. compliant.

**Profile responsibilities.** The two profiles have distinct, non-overlapping concerns:

- `hal-schema-forms` (base profile) — provides HAL Forms schema semantics, including the `_forms` structure and full JSON Schema field definitions.
- `heart-rise` (H.E.A.R.T. profile) — layers the hash fingerprint, affordance registry, and Sync-Reason-Act processing model on top of the base.

A conformant H.E.A.R.T. response SHALL contain:

**Affordance Form.** A discrete unit representing a single available transition, expressed as a HAL Schema Form. SHALL contain:
- `rel` — semantic relation name identifying the affordance
- `href` — target URI provided by the server
- `hash` — fingerprint of the current schema for this affordance, computed per §6.5
- `_schema` — full JSON Schema document describing the form fields (see Field Schema below)

**Field Schema.** Embedded within each affordance form as a `_schema` object conforming to the HAL Schema Forms specification. The full JSON Schema vocabulary is available — `type`, `enum`, `minimum`, `maximum`, `pattern`, `format`, `description`, `allOf`, `anyOf`, conditional keywords, and any other standard JSON Schema keywords. H.E.A.R.T. does not restrict which keywords a server may use. Clients SHALL treat `_schema` as a standard JSON Schema document.

At minimum a conformant field schema SHALL declare:
- `properties` — JSON Schema properties object mapping field names to their full schema definitions
- `required` — JSON Schema array listing required field names

The structural contract exposed to the hash fingerprint (§6.5) is a normalised subset of the full schema. The full schema governs what the client submits; the fingerprint governs cache invalidation.

**Affordance Manifest.** A collection of affordance forms under the `_forms` key, representing all currently available transitions.

**Resource State.** Domain data representing the current state of the resource, carried in the response body alongside `_forms`.

**Atomic Unit.** Resource state and affordance manifest SHALL be delivered simultaneously in a single response. Delivering them separately is non-compliant.

### 6.2 Compliant Server Behaviour

A H.E.A.R.T. compliant server SHALL:

- Serve resource state and affordance manifest simultaneously in every response
- Vary the affordance manifest based on current domain state
- Assign a stable hash fingerprint to every affordance schema, computed per §6.5
- Reject transitions not present in the current manifest
- Respond to the `Accept-Profile` header per §6.4
- Serve hash fingerprints only when `detail=hash` is declared in the profile
- Serve full embedded schemas when no profile is declared or on cold start
- Advertise supported profiles via the `Link` header

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

### 6.3 Compliant Client Behaviour

A H.E.A.R.T. compliant client SHALL:

- Enter any interaction with a single bookmark URI and no prior domain knowledge
- Declare the H.E.A.R.T. profile URI on every resource request per §6.4
- Declare `detail=hash` when the registry contains schemas for known affordances
- Omit `detail=hash` on cold start to receive full embedded schemas
- Compare received fingerprints against the local affordance registry
- Fetch only schemas whose fingerprint is absent from or changed in the registry
- Reason only over affordances present in the current manifest
- Populate form fields within the constraints declared by the affordance schema
- Never construct a URI from prior knowledge
- On receipt of a 409 or 428, re-sync before retrying the transition

### 6.4 Profile Negotiation

H.E.A.R.T. uses the `Accept-Profile` header per the W3C Content Negotiation by Profile specification. The H.E.A.R.T. profile URI is `https://github.com/jogoshugh/heart-rise`. The base HAL Schema Forms profile URI is `https://github.com/jbadeau/hal-schema-forms`.

Profile negotiation parameters are scoped to the H.E.A.R.T. profile and govern response optimisation behaviour. They have no effect on the HAL Schema Forms base layer.

**Header syntax examples:**

Cold start — full schemas requested:
```
Accept-Profile: <https://github.com/jogoshugh/heart-rise>
```

Warm client — hashes only, autonomous mode:
```
Accept-Profile: <https://github.com/jogoshugh/heart-rise>; param:detail=hash; param:behaviour=autonomous
```

Warm client — hashes only, interactive mode:
```
Accept-Profile: <https://github.com/jogoshugh/heart-rise>; param:detail=hash; param:behaviour=interactive
```

Domain-familiar client — hashes only, known domain vocabulary:
```
Accept-Profile: <https://github.com/jogoshugh/heart-rise>; param:detail=hash; param:domain="https://example.org/vocab/farming"
```

**Profile parameters:**

| Parameter | Values | Effect |
|-----------|--------|--------|
| `detail` | `full` (default), `hash` | `hash` suppresses full schema bodies from the manifest; only fingerprints are returned for known affordances |
| `behaviour` | `interactive` (default), `autonomous` | Signals whether a human is available to clarify ambiguous intent; servers MAY use this to omit affordances requiring disambiguation |
| `domain` | URI | Declares familiarity with a specific domain vocabulary; servers MAY omit explanatory metadata for known rel names |

**Four profile levels.** All parameters are optional and combinable:

1. **Functional** — `Accept-Profile: <https://github.com/jogoshugh/heart-rise>` — minimum H.E.A.R.T. compliance signal
2. **Detail** — adds `param:detail=hash` — enables registry-based schema suppression
3. **Behavioural** — adds `param:behaviour=autonomous` or `interactive` — informs server affordance selection
4. **Data** — adds `param:domain=<uri>` — enables vocabulary-aware response compression

Graceful degradation SHALL be supported. A request without `Accept-Profile` SHALL receive a full response. A `406 Not Acceptable` SHALL be returned only when the declared profile cannot be honoured.

### 6.5 Hash Canonicalization

A H.E.A.R.T. hash fingerprint is a deterministic identifier for an affordance schema. All compliant implementations MUST use the same procedure to ensure interoperability.

**Algorithm:** SHA-256.

**Input:** A normalised structural fingerprint object derived from the affordance's `_schema`, containing only two keys: `fields` and `required`. `fields` is a map of field name to its declared JSON Schema `type` string, extracted from the `properties` object. `required` is the JSON Schema required array. All other JSON Schema keywords (`description`, `title`, `enum`, `minimum`, `maximum`, `pattern`, `format`, `allOf`, `anyOf`, etc.), the `rel`, `href`, and any server-specific metadata SHALL be excluded from the hash input.

The fingerprint captures structural identity — which fields exist and which are required — not documentation or constraint detail. Two schemas with the same field names, types, and required set produce the same fingerprint regardless of their descriptions or constraints.

**Canonicalization:** JSON Canonicalization Scheme per RFC 8785. This produces a canonical byte sequence from any JSON value by: recursively sorting object keys lexicographically, removing insignificant whitespace, and normalizing string escapes. Implementations SHALL apply RFC 8785 before hashing.

**Output:** Lowercase hexadecimal encoding of the 256-bit digest.

**Example.** Given the full HAL Schema Form `_schema`:
```json
{
  "type": "object",
  "description": "Sow seeds into the bed cell",
  "properties": {
    "cropType": { "type": "string", "description": "Plant cultivar name" },
    "seedCount": { "type": "integer", "minimum": 1, "maximum": 100 }
  },
  "required": ["cropType", "seedCount"]
}
```

The normalised fingerprint input (structural fields and required only):
```json
{
  "fields": { "cropType": "string", "seedCount": "integer" },
  "required": ["cropType", "seedCount"]
}
```

After RFC 8785 canonicalization:
```
{"fields":{"cropType":"string","seedCount":"integer"},"required":["cropType","seedCount"]}
```

The SHA-256 digest of this canonical byte sequence is the fingerprint. A server publishing this schema and a client caching it will always produce the same fingerprint for the same logical schema, regardless of how the surrounding JSON Schema keywords differ between them.

Implementations producing different hashes from the same logical schema are non-compliant.

### 6.6 Affordance Registry

The affordance registry is a client-side store of schema definitions keyed by hash fingerprint.

The registry SHALL:

- Start empty — no schemas are assumed known before first interaction
- Store a schema keyed by its fingerprint
- Return null on miss without error
- Support lightweight presence check without full retrieval
- Be idempotent on repeated store of the same fingerprint
- Persist across sessions to maximise caching benefit

The registry SHALL maintain two partitions:

**Schema partition.** Maps fingerprint to full affordance schema. Populated during sync.

**Agent directory.** Maps rel name to agent endpoint URI. Populated from affordances with the `heart:agent` rel (see §7.6). Used by RISE fallback selection.

### 6.7 Sync Behaviour

The sync step is the first step of every Sync-Reason-Act cycle.

The sync step SHALL:

- Compare all fingerprints in the received manifest against the registry schema partition
- Identify dirty affordances — those whose fingerprint is absent or changed
- Fetch full schemas for all dirty affordances before reasoning begins
- Complete with zero additional server requests when all fingerprints are known
- Complete with zero additional server requests on subsequent sessions when the registry is warm and the server manifest is unchanged

### 6.8 Sync-Reason-Act Cycle

The agent's processing model SHALL follow three steps in sequence:

**Sync.** Fetch the current resource state and manifest. Resolve all dirty affordance schemas. The agent SHALL NOT reason until the manifest is fully resolved.

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
- `template-hash` — fingerprint of the template schema, enabling the recipient to check its registry before fetching
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
- Check the affordance registry for the envelope's template hash
- Fetch the template schema only on registry miss
- Map its domain state onto the declared template fields using rel names as semantic anchors
- Submit the callback to the reply-to URI (push) or post the result to the polling resource (poll)
- Never expose its internal domain model in the callback

### 7.5 RISE Rel Name Vocabulary

The core RISE vocabulary is minimal:

- `heart:agent` — identifies an agent endpoint available for async work or fallback; stored in the agent directory partition of the affordance registry
- `reply-to` — success callback shape
- `error-reply` — failure callback shape
- `progress` — optional intermediate status update
- `heart:result` — polling resource affordance indicating a completed async result is available

Domain-specific rel names SHALL be defined in domain profiles layered on top of the core vocabulary. Domain rel names SHALL NOT be added to the core vocabulary.

### 7.6 RISE Fallback Behaviour

A RISE compliant originator SHALL:

- Define a non-response window after which fallback is triggered
- Select a fallback recipient from the agent directory partition of the affordance registry (see §6.6)
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

### 7.8 Shared Registry

The affordance registry SHALL be shared between H.E.A.R.T. and RISE interactions. A schema cached during a H.E.A.R.T. sync step SHALL be available to RISE envelope processing without re-fetch. A template hash stored by the originator SHALL be discoverable by the recipient if both participants share a registry instance or if the hash has been previously encountered by the recipient.

---

## 8. Relationship to Prior Art

H.E.A.R.T. is distinct from but informed by the following:

**HAL+JSON and HAL Schema Forms.** H.E.A.R.T. is built directly on top of `application/hal+json` and the HAL Schema Forms specification (`https://github.com/jbadeau/hal-schema-forms`). HAL Schema Forms provides the `_forms` structure and full JSON Schema field definitions. H.E.A.R.T. does not replace or extend this layer — it layers on top via the second profile URI in the dual-profile `Content-Type` parameter. H.E.A.R.T.'s additions are: hash-based schema fingerprinting (§6.5), the affordance registry (§6.6), the `Accept-Profile` negotiation mechanism (§6.4), and the Sync-Reason-Act processing model (§6.8). A server already serving HAL Schema Forms responses can evolve toward H.E.A.R.T. compliance by adding `hash` to each form and declaring the second profile URI in its `Content-Type`.

**ALPS (Application-Level Profile Semantics).** H.E.A.R.T. rel names serve a similar purpose to ALPS descriptors — they provide a semantic vocabulary for transitions. H.E.A.R.T. does not require ALPS compliance but does not preclude it. A domain profile MAY reference an ALPS document as its vocabulary definition.

**W3C Content Negotiation by Profile.** H.E.A.R.T. profile negotiation uses the `Accept-Profile` header and parameter syntax as defined in the W3C working draft. H.E.A.R.T. is not a profile of that specification; it uses its header syntax.

**JSON Canonicalization Scheme (RFC 8785).** Used as the canonicalization step in hash computation (§6.5). H.E.A.R.T. does not extend RFC 8785.

---

## 9. ARTIE — Reference Client

ARTIE is the reference implementation of a H.E.A.R.T. and RISE compliant client. ARTIE demonstrates that a single generic agent, carrying no domain-specific knowledge, can interact correctly with any H.E.A.R.T. compliant server.

### 9.1 ARTIE Requirements

ARTIE SHALL:

- Enter any H.E.A.R.T. compliant server with a single URI and no prior domain knowledge
- Execute the Sync-Reason-Act cycle correctly
- Maintain a persistent affordance registry across sessions
- Declare the appropriate profile on every request
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
- Registry cache hit and miss verification
- Warm registry lean request verification (hashes only, zero schema fetches)
- Cold start full schema verification
- Fallback path completion verification (push and poll modes)

---

## 10. Success Metrics

**Per-turn token cost.** Target: 90% reduction versus the baseline. Baseline definition: a complete OpenAPI specification document for the equivalent domain provided in full at every session turn, with all endpoints included. Measurement: tokens consumed per turn from the second turn onward, against an ARTIE interaction using a warm registry (all fingerprints known). The warm registry condition represents the steady-state for any session longer than one turn.

**Structural hallucination.** Zero calls to affordances not present in the current manifest. H.E.A.R.T. does not constrain semantic misidentification — the LLM may still select the wrong affordance from the manifest — but it eliminates the class of errors where the agent invokes an endpoint that does not exist or is not currently available.

**Server evolution adaptability.** Agent adapts to manifest changes within one sync cycle with zero client code changes.

**Registry efficiency.** Zero schema fetches on second cycle against an unchanged server manifest.

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

- Registry persistence strategy across sessions and devices
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