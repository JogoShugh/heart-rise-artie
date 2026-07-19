# ARTIE — Executive Summary

- **Core idea:**
    * ARTIE holds no static, pre-declared tool list.
    * At any turn, its entire universe of possible actions is exactly what the server included in its *most recent* response:
        + `_forms` (actions)
        + `_links` (navigation)
    * Nothing is remembered from a previous turn about **what's legal right now** — the manifest is re-read fresh, every sync, with no carry-over.
    * Nothing is assumed from a previous session.
    * (One deliberate exception to "nothing is remembered," addressed in full below: a cost optimization caches schema *shape* by hash. It never caches — and is never consulted for — action legality. See "Token minimization," next.)

- **Why that matters for LLM inference, specifically:**
    * Reasoning cost and error surface scale with the size of the decision space handed to the model:
        + Not just raw prompt length.
        + More candidate actions means more room to weigh, disambiguate, or mis-pick.
    * ARTIE keeps that space narrow and current by construction:
        + Only the affordances legally available on *this* resource.
        + In *this* state.
        + Right now.
        + Never the union of everything the API can ever do.
    * Contrast with the common pattern:
        + An agent wired to a fixed OpenAPI-derived toolset carries the *entire* action catalog on every turn.
        + Reachable or not.
        + That's a permanent token tax.
        + And a permanent surface for picking a tool that isn't actually valid right now.

- **Token minimization is built into the protocol, not bolted onto the client:**
    * A hash-keyed registry:
        + A schema is paid for, in tokens, once per hash.
        + A resync against an unchanged affordance costs a fingerprint comparison.
        + Not a full schema re-transmission.
    * **This registry is the one thing ARTIE remembers across turns — and it's deliberately narrow.** It caches only the *shape* of a schema already proven legal once, purely to avoid re-paying its token cost. It is never used to decide whether an affordance is currently legal. That decision is re-derived from the manifest on every single sync, cache hit or not. Concretely: even when a schema is served as a hash only (warm sync) because the client already holds it, the client still compares that hash against the server's current value every turn. A mismatch means the affordance changed shape — the cached entry is discarded and the full schema re-fetched, not trusted past its hash. So the registry saves tokens on *what a legal affordance looks like*; it never substitutes for the server's live word on *whether that affordance is legal at all*. Those are two different questions, and only the second one is "nothing remembered."
    * `param:detail=hash` (warm sync):
        + Shrinks the wire payload itself once a schema is already held.
        + Which shrinks what has to be read and reasoned over that turn too.
    * Nothing is prefetched "just in case":
        + Schemas are fetched lazily.
        + Only when dirty.
        + Only right before they matter to Reason.

- **Where this differs from MCP:**
    * MCP is a real step past hardcoded, docs-driven integration:
        + Tools are discovered at runtime via `tools/list`.
        + Rather than baked in from a spec read offline.
        + That's a legitimate win over the failure mode above.
    * But MCP's discovery is scoped differently:
        + Scoped to the *server connection*.
        + Not to the *current resource's state*.
        + A client typically loads a tool list once per session.
        + And treats it as available for the session's duration.
        + Regardless of whether every tool is actually actionable right now.
    * There's no structural equivalent of "this action disappears the instant it's no longer legal":
        + That would have to be layered on top via tool descriptions.
        + Or runtime errors.
        + Or a `listChanged` notification the client must separately handle.
        + Rather than falling out of the shape of the very response that changed state.
    * Net effect:
        + MCP narrows *which server* you're coupled to at runtime — a real improvement over hardcoded docs.
        + But the per-turn action surface an MCP client reasons over still tends toward "everything this server can ever do."
        + Not "everything legal on this resource right now."

- **Correctness and efficiency reinforce each other here — not a trade-off:**
    * Because the action surface is always exactly what the server currently says is legal:
        + ARTIE can't accidentally reason about a stale action.
        + Or a removed action.
        + Or a not-yet-available action.
        + Example: `sowSeed` after the seed's already planted.
        + Example: `recordScore` while the game is `HALF-TIME`.
    * Refusing to invent an affordance that isn't in the current manifest (the "harvest tomatoes" case):
        + Is the *same discipline* as refusing to carry an oversized static action space.
        + Both just mean reasoning only over what's actually, currently true.

- **Net effect:**
    * ARTIE's per-turn cognitive and token load scales with *what this one resource currently allows*.
    * Not with the total surface area of every domain, endpoint, or server version that could ever exist behind the bookmark.
    * Fielding's hypermedia constraint and modern context-window economy turn out to be the same answer to two different questions.

---

## Addendum: current build status vs. target architecture

Everything above describes the target protocol architecture — most of it specified in `SPEC.md` (v0.2, status: pre-implementation), not all of it built yet. In the interest of the same discipline this document argues for — never claiming more than what's currently true — here is what's actually real today versus what's still ahead.

**Built and tested today:**
- `HeartRiseClient` interface (`enter(url)`, `act(rel, payload)`) and `ArtieClient`, its reference implementation.
- The core structural guarantee already holds in code, not just in principle: acting on a `rel` absent from the current manifest raises an error before the transport is ever touched — no HTTP call, no server round-trip, rejected at the client boundary. This is the "harvest tomatoes" case above, and it's covered by a passing test today.
- `act(rel, payload)` — no URL parameter exists on this method. The caller cannot pass an href even if it wanted to; the client looks it up internally from the current manifest. The type signature enforces the "never construct a URI" rule at compile time, not by convention.
- All of this is driven by Cucumber/Gherkin feature files against a `FakeTransport` — no real HTTP, no real server, on either end yet.

**Not yet built:**
- Hash canonicalization and the fingerprint registry (the token-minimization mechanism described above is specified, not implemented).
- Warm/cold `Accept-Profile` negotiation and `param:detail=hash`.
- The Reason step — there is no LLM-driven intent-to-affordance mapping yet; the resolver is currently a stub interface with no scripted or model-backed implementation behind it.
- The in-memory domain servers (agriculture, sports scoring) — today's tests exercise a protocol-neutral fake, not a real state machine.
- 409/428 recovery, and all of R.I.S.E. (push/poll callbacks, agent directory, fallback).

**Where this started.** The repository's first commit was an LLM-heavy Koog agent that reasoned over the entire protocol at runtime, with no deterministic guarantees anywhere in the loop — the thing this whole effort is a reaction against. Everything since has been a slice-by-slice rebuild: each slice's actual deviation from the original written plan is recorded as it happens (see `slice_001_review.md`, `slice_002_pre_analysis.md`, `slice_02_001_act_unknown_analysis.md`), including *why* the plan changed, not just that it did. The claim this document makes isn't "ARTIE does this today." It's narrower and, we think, more defensible: every structural guarantee described above is either already enforced in code and covered by a test, or is specified precisely enough in `SPEC.md` to be built the same deliberate way — one failing test, then the code that makes it pass, in that order.
