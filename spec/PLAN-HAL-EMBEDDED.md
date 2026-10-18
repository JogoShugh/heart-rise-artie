# Plan: `_embedded`, the HAL way, across HEART

Drafted 2026-10-18. Not started. The precursor to `PLAN-GATEWAY-URL-LINKS.md`, which needs it for
nested objects (a pull request's user, head repository, base repository). Decisions marked **D**
were made by the author on 2026-10-18.

## Where things stand

No HEART code handles `_embedded` today: not the servers, not ARTIE, not heart-ts, not heart-mcp,
not the reports. A map of what it touches (2026-10-18):

- **heart-core** renders Layer 1 in `Layers.kt` (`layer1` for HAL Schema Forms, `halForms` for
  HAL-FORMS); `_links` gets self, curies, `hr:unavailable` and sub-collection links. The ETag hashes
  the whole rendered map (`StateETag.kt`).
- **Affordance sets** (Layer 2) are content-addressed and shared; they carry no per-resource data.
- **Collections** are links only: `_links.item[{href, title}]`, `count`, paging (`SearchPages.kt`).
- **ARTIE** strips only `_forms`, `_links`, `_templates` from state, so an `_embedded` would leak into
  state, the reasoner's prompt and heart-mcp. **heart-ts** strips every `_` key. They disagree.
- **Following** is by top-level rel and title only, in ARTIE, heart-ts, heart-mcp and the DSL.
- **Pinned bytes:** `content-addresses.feature` pins Layer 2 and 3 addresses; TCK ETags are
  derived (`{digest}`), so they follow body changes.

## What "the HAL way" means here

From the HAL draft, applied to HEART:

1. `_embedded` is an object whose keys are link relations (CURIEs apply, as in `_links`) and whose
   values are a resource object or an array of them.
2. Each embedded resource object is a full or partial representation of the resource at its own
   address: its state members, its own `_links` with `self`, and its own `_embedded`, recursively.
3. **Only resources are embedded.** A nested object with no address of its own (a basketball
   game's `home` team, a ticket's `conversation`) is state and stays state. An embedded object must
   have `self`.
4. An embedded relation is also a relation: a client may follow it like a link, by its `self`.

## Rules HEART adds

- **D1. Embedded resources are for reading; acting needs the resource itself.** No `_forms` or
  `_templates` inside `_embedded`. HAL Schema Forms allows them ("This property MAY appear on any
  HAL document (including embedded HAL documents)", README line 68); HEART's profile narrows that,
  for a stated reason: every act carries `If-Match`, and an embedded copy has no ETag. To act, follow
  it: the fetch brings its ETag and its forms. Decided for now; a remedy is filed as
  `spec/ideas/embedded-forms-with-validators.md`.
- **Embedded content is part of the bytes.** It counts in the parent's ETag, as SPEC already says
  for every byte. A server should embed only what belongs with the parent's state, or accept that
  changes to the embedded resource make the parent's validator change.
- **Template pointers and `hr:options` pointers** resolve against the owning resource's state, as
  today. `_embedded` isn't part of state, and pointers don't reach into it.
- **Layer 2 doesn't change.** Sets still carry no per-resource data; their addresses stay pinned.
- **Plain JSON (the OpenAPI twin)** keeps the state as it is: an embedded resource stays where the
  state holds it, as a plain member, so plain clients see the shape they would expect.

## Work, by module

### 1. SPEC and profile (first)

- SPEC §6.1 and the Layer 1 row: Layer 1 may carry `_embedded`, with the rules above.
- §6.11: collections may embed their items (see 4).
- `HAL-SCHEMA-FORMS-HR.md`: a short rule, "Embedded resources", and the pointer rule restated.
- The Internet-Draft: one paragraph.

### 2. heart-core and the bindings

- The declaration says what to embed: `embeds` per resource type, each a relation, a pointer to
  the nested object (or array) in the body, and how its `self` is made (an item path template, or a
  pointer to its address).
- `Layers.layer1` and `Layers.halForms` render `_embedded` after `_links`, each embedded object with
  its state, `_links.self` and its own lifted links; `bodyOf` loses those members.
- http4k, Spring and CoAP need no change beyond the shared renderer; the ETag follows.
- TCK: features for both dialects (an embedded resource has self; no forms inside; its members
  are not in the parent's state; HAL-FORMS the same; plain JSON nests it back).

### 3. Clients

- **ARTIE** (`HeartResponseParser`, `HeartRepresentation`): strip `_embedded` from state; a model,
  `embedded: Map<rel, List<Embedded>>`, where `Embedded` holds address, state, links and its own
  embedded. `follow(rel, title)` accepts an embedded relation and fetches its `self`.
- **D2. Following always fetches.** The embedded copy is for reading; following brings the ETag and
  the forms. Using the copy as a cache would save a request but leave a script holding a resource it
  can't act on.
- **D3. Both clients take the same four members out of state, by name:** `_links`, `_embedded`,
  `_forms`, `_templates`. Any other `_` member from an upstream API stays data. heart-ts, which
  strips every `_` key today, changes to match.
- **heart-script:** `Representation` gains `embedded`; the DSL gets `embedded(rel)` to read
  (address, state, links) without a request; `follow` works on embedded relations; the trace
  records embedded resources.
- **Reasoner:** the prompt gets an `<embedded>` section (relation, title, state) and embedded
  relations among the links it may follow.
- **heart-mcp:** `Views` adds `embedded: [{rel, title, state}]`; `heart_follow` accepts them.
- **heart-ts and the conformance suite:** the same model, follow and stripping; scenarios to match.

### 4. Collections (D4: in this plan, as its last phase)

The commonest use of `_embedded` in HAL: a page embeds its items, so a client sees each item's
summary without one request per item. `_links.item` stays (a client that follows still works), and
`_embedded.item` adds each item's summary state with its `self`. `Found` gains an optional summary;
the garden's bed search is the demo. It is where `_embedded` earns its keep for every HEART
server, not only the gateway.

### 5. Reports

`run-report.js`: the answer sentence counts embedded resources ("N data fields, F forms, L links,
E embedded"); the neutral trace carries them; the JSON viewer needs nothing.

## Order

Features first in every module, with tables (the bdd-gates skill loaded first), red at test level
before any `src/main` change, `.feature` and `src/main` edits only through Edit and Write.

1. SPEC, the profile rule, the draft paragraph.
2. heart-core and the TCK; all three bindings green; pinned Layer 2 addresses unchanged.
3. ARTIE, heart-script, the reasoner, heart-mcp.
4. heart-ts and the conformance suite.
5. Collections embed their items; the garden's search as the demo.
6. Reports.
7. Every suite green: heart and the TCK, garden, basketball, tickets, gateway, client, mcp, heart-ts.

Then `PLAN-GATEWAY-URL-LINKS.md`, which uses all of it.

Commits local, in module order; the author pushes.
