# ARTIE — Executive Summary

- **Core idea:**
    * ARTIE holds no static, pre-declared tool list.
    * At any turn, its entire universe of possible actions is exactly what the server included in its *most recent* response:
        + `_forms` (actions)
        + `_links` (navigation)
    * Nothing is remembered from a previous turn.
    * Nothing is assumed from a previous session.

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
        + Example: `recordScore` while the game is `PAUSED`.
    * Refusing to invent an affordance that isn't in the current manifest (the "harvest tomatoes" case):
        + Is the *same discipline* as refusing to carry an oversized static action space.
        + Both just mean reasoning only over what's actually, currently true.

- **Net effect:**
    * ARTIE's per-turn cognitive and token load scales with *what this one resource currently allows*.
    * Not with the total surface area of every domain, endpoint, or server version that could ever exist behind the bookmark.
    * Fielding's hypermedia constraint and modern context-window economy turn out to be the same answer to two different questions.
