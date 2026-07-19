# Deep Dynamism vs Static Analysis: Why System Boundaries Demand a Different Contract

## The Brilliant Mistake

Static type systems are one of the greatest inventions in software engineering. Inside a single deployable unit, they compress enormous amounts of correctness verification into compile-time. A Kotlin function signature `fun calculateInterest(principal: BigDecimal, rate: Double, periods: Int): BigDecimal` is a machine-checked claim about behavior. Rename the parameter, change the return type, remove the function entirely — the compiler tells you every single callsite that breaks, instantly, for free, before anything runs. This is not a small gift. This is load-bearing infrastructure for building complex systems with confidence.

The brilliant mistake was assuming this model could be stretched across system boundaries.

It cannot. The reasons are not accidental limitations that better tooling will eventually overcome. They are structural, and they emerge from what a distributed system actually is.

---

## What a System Boundary Actually Means

Inside a single JVM process, when you call `calculateInterest(...)`, the compiler has verified the types at compile time, the linker has resolved the symbol, and the CPU executes the call. All of this happens in the same address space, under the same deployment artifact, at coordinated times. Change and deploy are coupled. The entire thing moves together.

Across a system boundary, none of that is true. When Service A calls Service B over HTTP, they are separate processes, running on separate machines, deployed on separate schedules by separate teams, potentially in separate organizations. Service A and Service B do not share a deployment. They do not share a type system. They do not share a linker. The only thing they share is a message format transmitted over a wire.

The question of "what contract holds between them" is therefore a question about the message, not about the code.

This is where the field went wrong for roughly fifteen years. The industry's first instinct was to try to recreate the compiler-checked contract at the boundary — WSDL, CORBA IDL, gRPC protobuf schemas, OpenAPI generated clients. All of these approaches say: let's describe the shape of the message in a schema language, generate code from that schema in both Service A and Service B, and then treat the generated code as if it were statically typed. Compile-time guarantees are restored. Problem solved.

Except the problem isn't solved. It's hidden, and it gets worse.

---

## The Phantom Guarantee

When you generate a Kotlin client from an OpenAPI spec, you feel like you have compile-time safety. You call `client.sowSeed(request)` and the compiler checks the types. But what you actually have is compile-time safety against the *snapshot of the spec that existed when you ran the generator*. You have zero compile-time safety against what the server actually does at runtime, which is the thing you actually care about.

More dangerously: the generated client encodes assumptions about the server's structure — its URL paths, its HTTP methods, its field names, its response shapes — as compiled artifacts in your service's binary. These assumptions are invisible. They don't announce themselves. They hide inside `generated/` folders that nobody reads. They're regenerated infrequently. They drift.

When the server changes, the client doesn't know. It continues to compile. It continues to pass tests (against mocks generated from the same stale spec). It fails at runtime, in production, in ways that are confusing because the type system promised it was fine.

This is the phantom guarantee. It looks like safety. It is coupling in disguise.

---

## The Real Cost of Static Binding Across Boundaries

Let's be concrete about what actually happens in organizations that operate this way.

**Cascading release coordination.** Service B wants to rename a field. They cannot just rename it and deploy. They must notify every consumer, wait for those consumers to update their generated clients, coordinate deployment timing, maintain the old field name in parallel for a migration window, eventually deprecate it. A single field rename in one service creates a project that spans multiple teams, multiple sprints, multiple risk windows. The internal cost of change has been externalized and multiplied across the organization.

**API versioning as a damage control mechanism.** Because change is so expensive, teams avoid it. When change finally becomes unavoidable, it arrives as a breaking version: `/v1/`, `/v2/`. Versioning is not a feature. It is the scar tissue left by tight coupling. It exists because the contract was so brittle that change couldn't be made incrementally. Now you're maintaining two complete versions of the same service, both with clients that need their own generated SDKs, their own documentation, their own support burden.

**Consumer-driven contract testing — good idea, still misses the point.** Pact and similar tools try to make this better by recording what each consumer actually uses and testing that the provider still satisfies those contracts. This is genuinely better than nothing. But it's still a tool for managing tight coupling, not for eliminating it. Every consumer registers claims about provider structure. When the provider changes, all those claims must be re-evaluated. The coordination overhead is improved but not removed.

**SDK dependency hell.** If the service exposes a published SDK (a common pattern for platform teams), consumers take a dependency on a library version. That library version encodes structural assumptions. Upgrading it is a migration project. Not upgrading it means running on stale assumptions. There is no good option once you're in this model.

None of these costs are accidental. They all flow from the same root cause: structural coupling encoded in compiled artifacts deployed on independent schedules.

---

## What Fielding Actually Said (and Why It Gets Ignored)

Roy Fielding's 2000 dissertation identified exactly this failure mode and gave it a name: connector coupling. REST, as an architectural style, was explicitly designed to minimize connector coupling by enforcing the uniform interface constraint. The uniform interface says that all interactions between components must go through a fixed set of operations (in HTTP: GET, POST, PUT, DELETE, etc.), and that the representation of state must be self-descriptive.

Self-descriptive means: the message carries enough information to process itself, without the receiver needing out-of-band knowledge about the sender's structure. The Content-Type header is the key. It says: here is the media type of this message. If you understand the media type, you can process this message without knowing anything else about where it came from.

What the industry built instead — and labeled "REST" for marketing reasons — was HTTP-transported RPC with resource-shaped URLs. The URL is the API. The structure of the URL is the contract. The response shape is implicitly contracted. Nothing is self-descriptive. Everything is out-of-band. This is precisely what Fielding's architecture was designed to prevent, and it produces precisely the pathologies described above.

Fielding himself wrote about this in 2008 in a widely-cited but widely-ignored blog post: "I am getting frustrated by the number of people calling any HTTP-based interface a REST API... REST APIs must be hypertext-driven." The uniform interface, the hypermedia constraint — these are not optional decorations. They are the mechanism by which connector coupling is controlled.

He was largely ignored because generating clients from specs is a better developer experience in the short term. It's only in the long term — when the coordination overhead of tight coupling accumulates — that the cost becomes visible and undeniable.

---

## The Two Layers That Must Not Be Conflated

Once you accept that static structural binding across services is load-bearing technical debt, the question becomes: what replaces it? What contract CAN hold across a system boundary, durably, across independent deployments?

The answer requires distinguishing two layers that tight coupling conflates.

**The structural layer**: How do I parse and process this message? What encoding does it use? What fields have what types? What do I do with this response at a mechanical level?

**The semantic layer**: What does this operation *mean* in the domain? What does this rel name *refer to*? What business concept does this state transition represent?

The structural layer should be defined by the media type and discoverable at runtime. The semantic layer should be defined by domain documentation — rel registries, ALPS profiles, plain human-readable descriptions. These are two different things and they change at different rates for different reasons.

When tight coupling embeds both layers into generated code, any change to either layer — structural OR semantic — requires recompilation and redeployment of all consumers. When the layers are separated, structural changes that preserve semantics are handled automatically by the media type parser. Only genuine semantic changes — new concepts, new operations that clients need to understand and respond to — require active client updates.

This is where H.E.A.R.T. sits. The media type (`application/hal+json` with the dual profile) defines the structural layer. The rel names define the semantic layer. A client that speaks the media type can handle structural evolution transparently. A client that understands the domain vocabulary can handle semantic evolution explicitly — not because the compiler forced it to, but because the human team recognized that a new business concept arrived.

---

## The Artie Pattern: Impervious to Physical Change

The `HeartRiseClient` interface — two methods, `enter(url)` and `act(rel, payload)` — is the practical expression of this separation.

```kotlin
interface HeartRiseClient {
    fun enter(url: String): HeartRepresentation
    fun act(rel: String, payload: JsonObject): HeartRepresentation
}
```

Consider what an `ArtieClient` (a client implementing this interface) does NOT know:

- The URL of any operation other than the entry point bookmark. URLs are discovered at runtime from the current manifest. The server can restructure its entire URL space and no client code changes.
- The HTTP method for any affordance. Methods are declared in `_forms` and honored at runtime. The server can change a transition from POST to PUT. No client code changes.
- Which affordances exist at any given moment. The manifest is read at runtime. The server can add new affordances, retire old ones, conditionally expose transitions based on resource state. No client code changes until a consumer wants to use a new capability.
- The response shape beyond what the media type specifies structurally. Business fields in `state` are domain content. The server can add, rename, or remove domain fields. Clients that don't reference those fields by name are unaffected.

What an `ArtieClient` DOES know:

- The structural contract of the media type: `_forms`, `href`, `method`, `hash`, `_schema`, HAL links. This is the stable part. The media type is versioned by its profile URI. When the media type genuinely evolves, that is a deliberate event, not an accident.
- The entry point bookmark. One URL, known once, stable forever by convention.

The surface area of the coupling is radically reduced. The coupling that remains is to semantics — to what rel names mean in the domain — rather than to physical structure. Semantic coupling is honest. It admits that clients need to understand what operations mean, not just how to serialize them.

---

## JSON Schema as the Sufficient Structural Contract for Payloads

There's one place where a structural contract at the boundary is genuinely useful: input payloads. When you `act`, you submit a payload. The server needs to validate it. The client needs to construct it correctly. This is exactly the use case JSON Schema was designed for.

The H.E.A.R.T. approach embeds the input schema in the affordance itself, inline in the manifest:

```yaml
_forms:
  sow-seed:
    href: https://garden.example.com/beds/7/sow
    method: POST
    hash: abc123
    _schema:
      type: object
      properties:
        cropType:
          type: string
          enum: [KALE, WHEAT, CORN]
        seedCount:
          type: integer
          minimum: 1
      required: [cropType, seedCount]
```

The schema is not a separate document you download at build time. It arrives with the affordance, at runtime, inline. This means:

- The schema can evolve between calls. Add a field, change validation, make something optional. The client gets the new schema on the next manifest retrieval.
- A validating client can check its payload against the schema before submitting, reducing round-trips.
- An AI agent (like ARTIE's intended use case) can read the schema and construct a valid payload without any prior knowledge of the server's domain model.
- An integration test can load the schema and use it as a property-based generator to explore the valid input space.

This is the structural contract that actually belongs at a system boundary: not a compiled client, but a discoverable, inline, runtime-provided schema for what the server accepts right now.

---

## Adding and Removing Affordances Without Cascading Updates

Here is the concrete test of the claim. Suppose you have a garden service with a bed resource. Initially:

- State: EMPTY → affordances: [`sow-seed`]
- State: SEEDED → affordances: [`water`, `inspect`]
- State: GROWING → affordances: [`water`, `harvest`, `inspect`]

The server team decides to add a new affordance: `apply-fertilizer`, available in GROWING state. What happens to existing clients?

**In the tight-coupling model:**
The API team adds a new endpoint, documents it, updates the OpenAPI spec, publishes a new SDK version, files tickets with every consumer team to upgrade their dependency, waits for those teams to have capacity, coordinates regression testing, and eventually ships the new capability weeks or months after the server code was written. Or they skip all that and just ship it, and half the consumers don't know it exists for six months.

**In the H.E.A.R.T. model:**
The server adds `apply-fertilizer` to the `_forms` of GROWING state. It's live at next deploy. Clients that navigate to a GROWING bed and inspect the manifest will see `apply-fertilizer` present. Clients that don't look for it don't see it, don't care, continue operating exactly as before. Nothing is broken. No coordination is required.

If a client wants to USE `apply-fertilizer`, a developer writes a new test scenario: "given a bed in GROWING state, act on `apply-fertilizer` with the appropriate payload." They write the semantic understanding of what this operation means. The structural mechanics — serialize the payload, submit to the right URL with the right method, parse the response — are handled by `ArtieClient` exactly as for any other affordance.

Now the removal case. The server team decides `inspect` is going away:

**In the tight-coupling model:** Every client that calls `/beds/{id}/inspect` will break. Emergency coordination. Deprecation headers. Version negotiation. Service reliability incidents while the migration is in flight.

**In the H.E.A.R.T. model:** The server removes `inspect` from all manifests. Clients that checked for presence before acting — which is the correct pattern — simply find it absent. No error. They navigate based on what's available. If a client hard-coded `act("inspect", payload)` without checking `containsAffordance("inspect")` first, THAT client errors — but correctly, because it violated the protocol. The fault is localized to that client's code, not systemic.

This is what "impervious to internal physical changes cascading outward from the origin server" means operationally. It's not magic. It's what the uniform interface constraint was designed to provide.

---

## What Requires Human Coordination — and What Does Not

To be precise about the boundary of the claim: dynamic affordance discovery does not eliminate the need for semantic coordination. It eliminates the need for structural coordination.

**Does not require coordination:**
- Adding fields to the resource state
- Removing fields from the resource state that clients don't reference
- Changing URLs
- Changing HTTP methods
- Adding new affordances
- Removing affordances (clients that check presence are safe; clients that don't check are violating the protocol)
- Changing input schema constraints in backward-compatible ways (adding optional fields)

**Does require coordination (human communication, not necessarily code changes):**
- Adding affordances that clients need to actively use for their workflows
- Removing affordances that clients depend on (graceful sunset with notice)
- Changing the semantic meaning of a rel name (this should be a new rel name, not a mutation)
- Breaking changes to the domain model that change the meaning of state fields

Notice the shape of the list. Everything on the "does not require coordination" list is structural change. Everything on the "does require coordination" list is semantic change — change to meaning, not to shape.

Teams that build on HTTP-transported RPC conflate these two lists completely. Every change, structural or semantic, requires coordination because both are encoded in the same compiled artifact. Teams that build on H.E.A.R.T. separate them. Structural change is free. Semantic change is handled as what it actually is — a domain-level decision that requires human communication about meaning, not a deployment coordination problem about bytes.

---

## The Kotlin Insight Applied at Scale

The user's intuition about Kotlin is exactly right, and it generalizes.

Inside a single compiled artifact, Kotlin's type system provides exhaustive, free, compile-time verification of structural invariants. This is the right tool for the job inside that boundary. The compiler is your servant.

Across compiled artifacts deployed independently, a type system cannot bridge the deployment gap. There is no shared compiler. There is no shared linker. What looks like a type-checked contract is a snapshot encoded in compiled code that cannot update itself when the other side changes. The compiler becomes a liability — it provides false confidence about a guarantee it cannot actually provide.

The right tool at a system boundary is a runtime-evaluated, self-describing, semantically stable contract. In the hypermedia model: the media type defines the structural contract (self-describing), the rel names define the semantic contract (stable across structural change), and both are evaluated at runtime by a generic client that needs to know nothing about the specific server it's talking to.

This is not "no types" — JSON Schema provides a rich structural vocabulary for validating payloads. This is "types at the right layer." The structural layer is governed by the media type and JSON Schema. The semantic layer is governed by domain documentation and rel name registries. The application logic layer is governed by whatever language you like, with full type safety, internally.

Static types inside a boundary. Dynamic discovery across boundaries. These are not in tension. They are complementary, each appropriate to the layer it operates at.

---

## The Practical Demonstration

If you are showing this to a team evaluating integration strategy, the demonstration is:

1. Show them the entry point. One URL. They bookmark it. They never construct another URL.
2. Show them the `act.feature` files. Plain language descriptions of state machine transitions. These are the integration contract — not a YAML spec describing URL structure, but a behavioral description of what the service does.
3. Change something on the server that would be breaking in the traditional model — rename a URL, add a required field to a new optional affordance, restructure the response. Run the tests. Watch them pass.
4. Add a new affordance to the server. Show that no existing test breaks. Show that a new test can be written that exercises the new capability in minutes, without touching a spec file, without regenerating a client, without coordinating a library release.

That is the value proposition made concrete. The tests ARE the contract. The contract is expressed in domain language, not structural language. Adding capability extends the contract without breaking it. That is what "open-closed principle applied across system boundaries" looks like.

---

## The Wager

The bet H.E.A.R.T. makes has two parts, and the order matters enormously.

**The primary wager: the mechanical protocol pattern is stable, and a generic client couples to that — not to any domain.**

A web browser enters a page and gets back a representation. It reads whatever the server sent. The user acts — follows a link, submits a form. The browser gets back a new representation and enters the identical cycle again. This mechanical loop has not fundamentally changed since 1993. Browsers have grown enormously in capability, but `enter → read manifest → act → enter` at their core is identical to what Mosaic did.

The crucial point is not that the domain content stayed stable. The crucial point is that the browser never coupled to the domain content in the first place. A browser has no idea whether it's rendering a garden management system, an airline booking flow, a government tax portal, or a social network. It doesn't care. It is coupled to HTML — the media type — and through that coupling alone it can handle any domain thrown at it without modification. Amazon and Wikipedia and your local restaurant's reservation page are all discovered at runtime. The browser's code doesn't change for any of them.

This is the primary value of a generic client. Not that the domain is stable. That the client is **decoupled from the domain entirely**. The coupling is to the media type. The domain is whatever arrives.

`ArtieClient` is the same wager applied to machine-to-machine integration. It is coupled to `application/hal+json` with the H.E.A.R.T. dual profile. Nothing else. It can enter any H.E.A.R.T.-compliant service — garden management, financial instruments, healthcare records, logistics — and execute the enter/act cycle without any domain-specific code. The affordances in the manifest tell it what's possible. The JSON Schema in each affordance tells it how to construct a valid payload. It never needed to know the domain in advance. It discovers it at runtime.

The JSON Schema form renderer ecosystem makes this concrete from a different angle. There are dozens of libraries — in JavaScript, Python, Java, Go — that read a JSON Schema and render input controls. They are stable, widely used, and domain-agnostic. Hand one a schema describing crop types and seed counts, it renders a form. Hand it a schema describing bond instruments, it renders a different form. The rendering engine didn't change. It was never coupled to any particular domain. This is the browser/HTML relationship reproduced exactly at the input-payload layer, and it already works in production across countless teams who don't think of it in these terms.

**The secondary wager: within a given business, domain concepts tend to change less frequently than their structural implementations.**

This is worth noting but should not be overemphasized. The meaning of "sow-seed" — initiate cultivation of a crop in an empty bed — will likely outlive the URL that points to it, the HTTP method it uses, and the field names in its response. Coupling to the semantic rel name rather than to the structural URL is therefore coupling to the more durable thing. But this is a supporting argument, not the main one. Even if domain concepts changed constantly, the generic client model would still be correct — because the client doesn't couple to the domain regardless. The secondary wager is merely favorable; the primary wager is load-bearing.

**The combined picture**

The mechanical protocol pattern (enter/act) is proven stable at internet scale over thirty years. The generic client couples only to the media type, making it capable of serving any domain that speaks the protocol without modification. Within a business, domain semantics tend to outlive structural representations — providing an additional buffer against churn. Structural changes to any individual service propagate to zero client codebases by design.

Compare this to an ecosystem of generated OpenAPI clients. Each client is a one-time artifact for one service at one point in time. It couples to domain structure: URLs, methods, field names, response shapes. Every evolution of the service is potentially a breaking change for every client. The clients must be regenerated, recompiled, tested, and deployed. The coordination overhead compounds with every service, every client, every release cycle.

The economics are not close. A generic client built once against a stable media type, capable of interacting with any domain, is a fundamentally different cost structure than a bespoke client per service per version. The browser didn't become the dominant interface to all human information because it was clever. It became dominant because its economics — build once, serve any domain — were categorically better than the alternative.
