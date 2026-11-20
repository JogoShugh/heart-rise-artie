# The World H.E.A.R.T. + R.I.S.E. Makes Possible

---

## Prologue — Fielding's complaint, and the lesson the industry ignored

In 2008, Roy Fielding — the author of REST — published a blog post titled *REST APIs Must Be Hypertext-Driven*. The industry had spent years building "REST APIs" that violated every principle he had defined. He was not subtle about it.

The core complaint:

> *"A REST API should be entered with no prior knowledge beyond the initial URI and set of standardized media types that are appropriate for the intended audience. From that point on, all application state transitions must be driven by client selection of server-provided choices that are present in the received representations."*

And more directly:

> *"If the engine of application state is not being driven by hypertext, then it cannot be RESTful and cannot be a REST API. Period. There is some broken software out there that needs to be fixed."*

The industry read this, nodded, and kept building the same thing with a different name. SOAP became REST. REST became GraphQL. GraphQL became OpenAPI. OpenAPI became MCP. Each generation inherited the original sin: the client carries knowledge of the server before the interaction begins. The schema lives in the client. The URI structure lives in the client. The expectations live in the client. The server is a passive executor of pre-negotiated contracts that the client already knows about.

Fielding called this out in 2008. Eighteen years later, in the age of agentic AI, we are still doing it — and doing it at a scale and cost that was unimaginable when he wrote those words.

---

## Part 1 — The browser got it right, and we forgot why

Consider what a web browser actually knows.

A browser knows HTML. It knows CSS. It knows JavaScript. It knows JPEG, PNG, WebP, SVG, MP4, WebM. It knows the HTTP protocol. It knows how to follow a link, submit a form, render an image, execute a script. These are its media types. This is the totality of its built-in domain knowledge.

A browser does not know how State Farm processes an insurance claim. It does not know how Chase handles a wire transfer. It does not know how Domino's tracks a pizza, how a lemonade stand manages inventory, how the New York Times organizes its editorial workflow, or how the London Stock Exchange settles a trade. It knows none of this. It carries zero domain-specific knowledge.

And yet it can interact correctly with all of them.

This is not an accident. This is the design. The browser is coupled to the **media type** — HTML — not to any domain. Every website on the planet speaks HTML. The content is domain-specific. The container is universal. When Amazon launches a new feature, they express it in HTML, and the browser renders it without being updated. When a bank redesigns its entire UI, the browser handles it because the bank still speaks HTML. When an entirely new kind of business that didn't exist five years ago builds a web presence, the browser works on day one because the media type is the contract, not the domain.

This is what Fielding meant by spending "almost all descriptive effort in defining the media types." The web's entire value network — billions of websites, billions of users, decades of content — rests on a remarkably small number of well-defined media types. The richness is not in the client knowing every domain. The richness is in the media types being expressive enough that any domain can be represented through them.

HTML is the lingua franca of human web interaction. The browser is its generic client. The web works because these two things are coupled to each other, and nothing else.

---

## Part 2 — The agentic era is repeating the original mistake

In 2026, AI agents interact with APIs the way pre-REST clients interacted with SOAP services: by carrying the schema out of band, constructing requests from prior knowledge, and treating the server as a static executor.

OpenAPI puts the entire API surface in the agent's context window before any interaction occurs. MCP loads tool definitions at startup, independent of what the server's current state permits. Both require the client to know what it can do before it asks. Both break when the server changes. Both flood the context window with information the agent may never use. Both are, in Fielding's precise terms, not RESTful — because the engine of application state is being driven by the client's prior knowledge, not by the server's current representation.

The result is an agentic ecosystem that looks superficially powerful but is architecturally brittle. Agents that can only talk to APIs they were specifically built for. Tool manifests that go stale. Context windows that bloat with schema definitions for affordances the agent can't use right now. Hallucinations that are blamed on the LLM when the real cause is stale or absent schema knowledge. And an explosion of narrow, domain-specific adapters — one MCP server per API, one tool manifest per system — because there is no universal container for expressing what any system can do.

This is the browser problem, solved wrong. Instead of one generic client that knows a media type, we have thousands of specific clients that each know a specific domain. Instead of HTML as the universal container, we have thousands of bespoke tool manifests, OpenAPI specs, and MCP server configurations.

---

## Part 3 — The media type is the answer, and H.E.A.R.T. is the media type

This is where H.E.A.R.T. and R.I.S.E. enter — not as an improvement on MCP, but as a return to the principle that the web's architecture demonstrated was correct and that the API industry has been ignoring for twenty-five years.

The H.E.A.R.T. media type is:

```
application/hal+json; profile="https://github.com/jbadeau/hal-schema-forms
                                https://github.com/jogoshugh/heart-rise"
```

This is a two-layer composition. HAL Schema Forms provides the structural container — the `_forms` object, the `_schema` embedded in each affordance, the full JSON Schema vocabulary for describing fields. H.E.A.R.T. layers on top of this the intelligence layer — hash fingerprinting, the affordance registry, the Sync-Reason-Act processing model, and the profile negotiation mechanism.

Together they define a media type expressive enough to represent any domain's current state and available transitions, with enough semantic richness that a generic agent can reason over them without domain-specific knowledge. The content is domain-specific. The container is universal.

**This is exactly what HTML does for the browser.**

An insurance company's claims system, a bank's wire transfer system, a farm's crop management system, and a sports league's scoring system can all speak `application/hal+json` with the H.E.A.R.T. dual profile. They expose their current state and available affordances through the same container. A single generic H.E.A.R.T. client — the ARTIE reference implementation demonstrates this — can enter any of them with nothing but a bookmark and navigate them correctly without knowing anything about insurance, banking, agriculture, or sports.

The client knows the media type. It does not know the domain. This is the browser and HTML. This is the principle Fielding articulated in 2000 and complained was being ignored in 2008. H.E.A.R.T. applies it to agentic AI interaction.

### The affordance form as the agent's hyperlink

In HTML, the hyperlink is the fundamental unit of navigation. A browser follows links it finds in the document. It does not construct URLs from prior knowledge. It does not guess where to go next. The server tells it — through the representation — what is reachable from here.

The H.E.A.R.T. affordance form is the agent's hyperlink. Every affordance in the manifest carries the `href` the agent must use — provided by the server in the current response. The agent never constructs a URI. It never guesses an endpoint. It follows what the server gave it, exactly as the browser follows `<a href="...">`. Fielding's constraint — "never construct a URI from prior knowledge" — is met not by discipline but by architecture. There is no other path available.

### The `_schema` as the agent's form element

HTML forms are how the browser knows what a server expects when the user submits data. The `<form>` element carries the method, the action URL, and the field definitions. The browser renders the form; the user fills it; the browser submits exactly what the form requested. No out-of-band knowledge required.

The H.E.A.R.T. `_schema` is the agent's form element. Every affordance carries a full JSON Schema document describing exactly what fields are expected, what types they must be, what constraints apply. The agent reads the schema, populates the fields, validates against the schema, and submits. Like the browser and HTML forms, no out-of-band schema knowledge is required. The server tells the client what it needs in the representation itself.

This is what Fielding meant by the engine of application state being driven by hypertext. The representation drives the next action. The server is not a passive executor of pre-negotiated contracts. It is the active provider of the current possible transitions.

### The hash fingerprint as the browser's cache

A browser caches resources. It has a mechanism — HTTP cache headers, ETags, conditional requests — for knowing whether a cached resource is still fresh without re-fetching the full payload. This is what makes the web fast at scale. Resources are fetched once and reused until they change.

The H.E.A.R.T. hash fingerprint is the agent's schema cache. A schema fetched during cold start is fingerprinted, stored in the affordance registry, and reused on every subsequent warm turn. The server signals freshness through the fingerprint: the same hash means "you have the current schema, nothing to fetch." A changed hash means "re-fetch." The agent never carries a full schema in its context window once it has been seen — only the fingerprint. This is how the 90% token reduction becomes a steady-state reality, not just a demo number.

### The dual profile as composable standards

HAL Schema Forms and H.E.A.R.T. are declared as separate profile URIs in the same `Content-Type` header. This is intentional. HAL Schema Forms is an existing standard for expressing affordances with embedded JSON Schema. H.E.A.R.T. does not replace it or modify it — it composes on top of it by adding a second profile URI.

A server already serving HAL Schema Forms responses can adopt H.E.A.R.T. compliance incrementally: add the `hash` fingerprint to each form, declare the second profile URI in the `Content-Type`, and respond to the `Accept-Profile` negotiation header. The two layers evolve independently. A client that only knows HAL Schema Forms gets a valid response. A client that knows both profiles gets the full H.E.A.R.T. experience.

This is how the web's media type stack composes. HTML, CSS, and JavaScript are separate standards that compose in the browser. None of them owns the others. Each can evolve independently. The composition is the power.

---

## Part 4 — The MCP world, and the entire classes of problems H.E.A.R.T. eliminates

MCP is useful. It standardized the wire format for giving agents tools. That was necessary and good. But it inherited the fundamental mistake of every API integration paradigm before it: the client carries knowledge of the server. MCP tool definitions are static. They're loaded at startup. They don't vary based on resource state. They don't fingerprint. They don't cache. They don't async. And they don't know about each other.

What follows is not a list of improvements. These are entire classes of engineering problems that simply cease to exist under H.E.A.R.T. + R.I.S.E.

---

### Class 1 — The forbidden-action-at-wrong-time class

In the MCP world, the agent discovers that an action is invalid only when the server rejects it. The LLM decided to call `cancel-order`. The server returned 409. Now you need retry logic, error recovery prompts, context window repair, and an apology to the user. Multiply this across every state-dependent workflow in your system.

Under H.E.A.R.T., this class of failure does not exist. If the order cannot be cancelled in its current state, `cancel-order` is not in the manifest. The agent has no knowledge of it. It cannot attempt it. The failure mode is eliminated at the architecture level, not patched at the prompt level.

This is not a reduction in errors. It is the removal of an entire failure category.

---

### Class 2 — The stale-schema class

MCP tool definitions go stale. Backend changes, someone forgets to update the MCP server, the agent starts submitting payloads that no longer match the server's expectations. These bugs are invisible until they fail in production, and when they fail they fail silently — the server rejects the call, the agent retries with the same bad payload, the user gets a generic error message.

Under H.E.A.R.T., the hash fingerprint makes staleness structurally impossible. The moment the server changes a field's type, adds a required field, or removes a field, the fingerprint changes. The client detects the mismatch on the next sync, re-fetches the schema, and is immediately current. There is no window of staleness. There is no class of stale-schema production failures.

---

### Class 3 — The context window bloat class

Every MCP session loads every tool definition for every possible operation, regardless of what the agent is actually doing or what state the resource is in. A complex enterprise API might expose 200 endpoints. 190 of them are irrelevant to what the user just asked. All 200 are in the context window. All 200 cost tokens on every turn.

Under H.E.A.R.T., the context window contains exactly the affordances that are valid right now. In a five-state workflow, that might be one or two affordances per turn. After the first turn, even those are hashes. The context window is flat by design, not by accident.

At enterprise scale — millions of agent turns per day — this is not a UX improvement. It's an infrastructure cost story.

---

### Class 4 — The security surface class

MCP's attack surface is large and poorly understood. Tool injection attacks — where a malicious data source embeds instructions that cause the agent to call unintended tools with unintended payloads — are a real and documented threat. The agent will call whatever tool it thinks is appropriate based on the text it's reading. It has no structural constraint on what it can call or where it can send data.

Under H.E.A.R.T., the client can only call affordances present in the current server-provided manifest, at the server-provided `href`, with a payload that passes JSON Schema validation against the server-provided schema. The client never constructs a URI from prior knowledge. It never calls an endpoint the server didn't explicitly offer in this response. Prompt injection can still manipulate which affordance the agent selects — but it cannot cause the agent to hit an endpoint the server didn't sanction in the current state. That entire attack vector is structurally closed.

---

### Class 5 — The one-agent-per-API class

Today's MCP ecosystem is a collection of narrow adapters. One MCP server for GitHub. One for Jira. One for Slack. One for your internal ERP. Each requires a purpose-built server, a domain-specific tool manifest, and integration logic that knows about that system's specific data model. The agent is not generic — it's a collection of adapters pretending to be generic.

Under H.E.A.R.T., there is one client. It enters any compliant server with a bookmark and no prior knowledge. It discovers everything at runtime. You don't build an adapter for each system — you make the system H.E.A.R.T. compliant and the existing agent handles it. The adapter layer, as a category of engineering work, disappears.

This is the browser rendering any website. Not a bespoke app for every website. One client. One media type. Any domain.

---

### Class 6 — The multi-agent schema negotiation class

When two MCP-based agents need to collaborate today, they need to share schema knowledge out of band. Agent A needs to know what format Agent B expects. This has to be configured somewhere — in a shared registry, a config file, a system prompt that tells A about B's interface. When B changes its interface, A breaks. When you add a third agent, the coordination problem grows combinatorially.

RISE eliminates this class entirely. The originator discovers the expected response shape from its own H.E.A.R.T. session, embeds it in the envelope, and sends it. The recipient has everything it needs to respond correctly without any prior knowledge of the originator. Agents that have never interacted before can collaborate correctly on first contact. There is no shared configuration layer to maintain, no versioning problem, no combinatorial coordination explosion.

---

### Class 7 — The observability vacuum class

In the MCP world, answering "what was the agent allowed to do at this moment?" requires reconstructing state from logs, tool manifests, and server-side business logic. There is no authoritative record of permitted actions at a given moment. Auditing an agent decision requires forensic reconstruction.

Under H.E.A.R.T., the manifest IS the complete, authoritative record of what was permitted at the moment of the sync. An audit trail is: manifest received, affordance selected, payload submitted, response received. Clean, bounded, verifiable. Regulated industries — finance, healthcare, insurance — need exactly this. They need to be able to prove that the agent could only have done what it did. H.E.A.R.T. makes that proof possible without additional instrumentation.

---

### Class 8 — The async infrastructure class

Async operations in MCP require you to build it yourself. You need a queue, a callback endpoint, a polling mechanism, a timeout strategy, a fallback path, and error recovery for each. This is not protocol-level support — it's application-level infrastructure that every team reinvents for every system.

RISE standardizes this completely. Push mode and poll mode cover every deployment environment. Fallback via the agent directory covers recipient failure. The callback shape is negotiated in the envelope, not configured out of band. The entire class of "build your own async infrastructure for agent operations" goes away. You implement the protocol once and it works everywhere.

---

## The deeper shift

Fielding's 2008 complaint was not really about REST. It was about where knowledge should live in a distributed system. His answer was unambiguous: knowledge of what is possible belongs in the representation the server provides, not in the client that consumes it. The media type is the contract. The server drives the state. The client follows.

The web succeeded because it took this seriously. The browser knows HTML. It does not know your business. Every website on earth expresses its domain through the same media type, and the browser handles all of them.

H.E.A.R.T. applies this to the agentic era. The agent knows `application/hal+json` with the H.E.A.R.T. dual profile. It does not know your business. Every H.E.A.R.T. compliant server — insurance, banking, agriculture, healthcare, logistics, sports — expresses its current state and available transitions through the same media type, and the agent handles all of them.

The knowledge lives where it belongs. The server owns the domain. The client owns the media type. Between them is a clean, stable, evolvable contract that neither party needs to negotiate out of band.

This is not an incremental improvement on MCP. It is a return to the principle that the web proved was correct, applied to the problem the current generation of AI systems is failing to solve.
