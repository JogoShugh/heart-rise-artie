# Architecture Decision Record — H.E.A.R.T. + R.I.S.E. Client

Decisions recorded in the order they were made.

---

## 1. Interface + implementation split — `HeartRiseClient` / `ArtieClient`

**Context:** Step definitions were calling `HeartResponseParser.parse()` directly, leaking an internal detail through the test boundary.

**Options considered:**
- Keep step definitions calling the parser directly
- Introduce a concrete `ArtieClient` class and use it directly
- Introduce a `HeartRiseClient` interface with `ArtieClient` as the implementing class

**Decision:** Interface + implementation.

**Rationale:** The interface is not merely an abstraction for testability — it is the protocol contract. Any conformant client in any language is a thing that implements `HeartRiseClient`. `ArtieClient` is the Kotlin reference implementation. Other teams (Python, Go, TypeScript) implementing the same protocol would produce their own equivalent. Naming the interface after the protocol, not the implementation, makes this explicit. Step definitions depend only on the interface; `HeartResponseParser` becomes a fully hidden internal.

---

## 2. Naming `enter` — the bookmark entry point

**Context:** The interface needed a method for initiating contact with a server via a URL. Working name was `sync`, after the SRA cycle's internal Sync step.

**Options considered:**
- `sync(url)` — rejected: implementation vocabulary leaking into the interface; the caller doesn't need to know about schema reconciliation
- `fetch(url)` — rejected: mechanism-focused and HTTP-flavoured; carries baggage from the browser Fetch API
- `navigate(url)` — rejected: implies movement away from somewhere, awkward on first call
- `enter(url)` — chosen

**Decision:** `enter(url)`

**Rationale:** Fielding's own word. From his 2008 post: *"A REST API should be entered with no prior knowledge beyond the initial URI."* The HEART spec picks it up verbatim in §6.3. `enter` names what is actually happening: the caller arrives from outside, holding a bookmark, knowing nothing else. It also sets up the asymmetry with `act` cleanly — with `enter` the caller holds the URL, with `act` the server provided the rel.

---

## 3. Two methods, not one — why `enter` and `act` do not collapse

**Context:** Both `enter` and `act` return `HeartRepresentation`. The question arose whether they should collapse into a single method.

**Options considered:**
- Single method taking a `ServerResponse` — caller provides the response directly, no HTTP inside the client
- Optional parameters encoding both paths — rejected immediately as a code smell
- Keep two distinct methods

**Decision:** Two distinct methods.

**Rationale:** They differ in what triggers the server response. `enter` says: here is a URL, go get the current state. `act` says: go cause a transition, then come back with what the server says. Same return type is a protocol property; it is not evidence the operations are the same. Collapsing them would mean the caller takes on responsibility for HTTP calls, URI lookup, and payload routing — work that belongs inside the client.

---

## 4. `act(rel, payload)` — the type system as Fielding enforcement

**Context:** Deciding what `act` takes as parameters.

**Options considered:**
- `act(url, payload)` — caller provides the target URL
- `act(rel, payload)` — caller provides a rel name; client looks up the URL internally

**Decision:** `act(rel: String, payload: JsonObject)`

**Rationale:** This is the most significant architectural decision in the interface. Fielding's principle is that clients must never construct URIs from prior knowledge — all navigation must follow what the server provided. Every previous API paradigm expressed this as a guideline and relied on discipline. `act(rel, payload)` encodes it as a type constraint. There is no parameter for a URL. The compiler rejects any attempt to pass one. The caller is architecturally forbidden from knowing the href after the initial bookmark. The architecture is the enforcement mechanism.

---

## 5. `act` dispatches to any HTTP verb internally

**Context:** Clarifying that `act` is not a POST wrapper.

**Decision:** `act` reads the method declared in the affordance form and dispatches accordingly — GET, POST, PUT, DELETE, PATCH, or any other verb the server specifies.

**Rationale:** The HTTP method is the server's declaration, not the caller's choice. The server embedded the method in the affordance form when it served the manifest. The client honors it. The caller never knows which verb was used and has no mechanism to override it. This preserves HTTP's semantic guarantees (idempotency, safety) while keeping them fully server-controlled.

---

## 6. Why two methods is correct — Fielding is about media types, not verb count

**Context:** Question of whether Fielding would object to a two-method interface when HTTP itself has many verbs.

**Decision:** Two methods is correct. More is not better.

**Rationale:** Fielding's uniform interface constraint is about the properties of the interface, not the count of its operations. HTTP verbs are one implementation of REST, not the definition of it. The web in practice runs on GET and POST — two operations. The browser's interface is follow a link, submit a form. Two operations built the entire human web because the complexity is in the media type, not the client. Fielding's 2008 post is explicit: *"spend almost all descriptive effort in defining the media type."* `HeartRiseClient` is thin by design. All descriptive effort is in `application/hal+json` with the dual profile.

---

## 7. `HeartRepresentation` — naming the return type

**Context:** Working name for the return type of `enter` and `act` was `SyncResult`.

**Options considered:**
- `SyncResult` — rejected: names how you got the thing, not what it is
- `Manifest` — rejected: loses the state half; the spec calls state and affordance manifest an atomic unit
- `ResourceView` — considered
- `HeartRepresentation` — chosen

**Decision:** `HeartRepresentation`

**Rationale:** This is the in-memory form of the H.E.A.R.T. media type — what `application/hal+json` with the dual profile looks like after parsing, alive in memory. It is not a result of syncing; it is a representation. It carries behaviour (`rels()`, `affordance(rel)`) because it is the input to the Reason step of the SRA cycle. A passive data class would be insufficient; the agent layer needs to query it.

---

## 8. Transport abstraction — hiding HTTP from the interface

**Context:** Early sketch had the agent layer calling `httpClient.get(url)` separately before calling `client.enter()`.

**Options considered:**
- Agent layer owns the HTTP call, passes response body to the client
- Client takes a `Transport` interface internally; HTTP is the default implementation

**Decision:** `Transport` interface injected into `ArtieClient`; `ServerResponse` is an internal type never surfacing on `HeartRiseClient`.

**Rationale:** The agent calling `httpClient.get()` before calling `client.enter()` is a transport leak. The caller should not know or care how the client obtains a server response — that may be HTTP today, AMQP tomorrow, or an in-memory fake in tests. The `FakeTransport` in the test package queues pre-canned responses; the feature file step definitions configure it and call `client.enter()`. Transport is fully pluggable without any change to the `HeartRiseClient` interface or the agent code.

---

## 9. Replacing `Accept-Profile; param:detail=hash` with content-addressed URLs + `Prefer`

**Context:** SPEC.md 0.2 specified a single custom negotiation mechanism — `Accept-Profile: <heart-rise>; param:detail=hash` — to control both which dialect a response used (implicitly always jbadeau's `_forms`) and whether schema bodies were suppressed for a warm client. Three parallel exploration efforts produced conflicting answers about how caching should actually work: `exhibits/cache-demo` tried bare content-addressed URLs (hash-in-URL, single dialect, its own design writeup overclaimed a dual-dialect version that was never actually built); `server-spring` built and *verified*, against a real captured request log, both real dialects (jbadeau `_forms` and Amundsen's real HAL-FORMS `_templates`) plus RFC 7240 `Prefer: embed=schemas` for inline-vs-pointer rendering, layered over content-addressed `Layer 2`/`Layer 3` URLs.

**Options considered:**
- Keep `detail=hash` as specified, add dual-dialect support as a separate, additional axis on top of it
- Adopt `exhibits/cache-demo`'s bare content-addressed URL scheme as-is
- Adopt `server-spring`'s verified mechanism: `Accept` for dialect, `Prefer: embed=schemas` for rendering, content-addressed URLs for Layer 2/3 caching

**Decision:** The third option. `detail=hash` is removed from the spec entirely (SPEC.md §6.4, 0.3). Dialect selection moves to plain `Accept` (§6.1); rendering detail moves to `Prefer`/`Preference-Applied` (§6.4); caching moves from a bespoke client-side fingerprint registry to ordinary HTTP caching of content-addressed URLs (§6.5–6.6).

**Rationale:** Only `server-spring`'s version is backed by a real, live-captured proof — real `Date` headers, real `Cache-Control`/`Vary`/`Preference-Applied` response headers, a documented and fixed real bug (an earlier version silently collapsed Layer 2 into Layer 1, defeating cross-resource cache sharing), and a verification pipeline (`render_journal.py`) that was itself tested against deliberately-broken data to confirm it can report failure, not just success. `exhibits/cache-demo`'s writeup made the same dual-dialect claim but the code never matched it — kept as a labelled exhibit, not used as a spec source. Standard `Accept`/`Prefer` also does less work than a custom `Accept-Profile` parameter for the same job: any RFC 7234-compliant cache, not just a purpose-built client, benefits from Layer 2/3 immutability for free.

**Trade-off accepted knowingly:** 0.2's hash fingerprint deliberately excluded `description`, `enum`, `minimum`, etc. so cosmetic schema edits wouldn't invalidate the client's cache. Real content addressing can't preserve that property — a trustworthy "this URL is proof the content can't have changed" claim requires hashing every byte, so a `description` edit now mints a new URL like any other content change would. Confirmed explicitly before making this change (see SPEC.md §6.5).

**Open question carried forward, not resolved here:** whether 428 (stale manifest hash) still has a distinct role once Layer 2/3 URLs can't go stale by construction, or collapses into 409/404 — see SPEC.md §12.
