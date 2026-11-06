# ARTIE (H.E.A.R.T. Reference Client) — Session Summary

This documents a live session of the `heart-client` "fat-prompt" skill against
a local H.E.A.R.T. server (**StarbornAg API**, `http://localhost:3000`). The
assistant has no supporting client code — every request below was built by
hand and issued via `curl`, with the assistant reading and reasoning over the
raw response each time. This transcript is meant to demonstrate that the
protocol logic in `SKILL.md` alone is sufficient to drive a full
Sync → Reason → Act loop against an unfamiliar, domain-agnostic server.

Two small enhancements were made to `SKILL.md` mid-session (a `DEBUG ON` /
`DEBUG OFF` logging mode, and explicit support for following plain HAL
`_links` even without an accompanying `_forms` manifest) — both are noted
inline where they became relevant.

## Context: Roy Fielding's 2008 complaint

In his October 2008 post ["REST APIs must be
hypertext-driven"](https://roy.gbiv.com/untangled/2008/rest-apis-must-be-hypertext-driven),
Fielding vented about the industry calling things "REST APIs" that were
really just RPC-over-HTTP with a fixed, out-of-band contract. His core
complaints, paraphrased:

- **"A REST API should not be dependent on any single communication
  protocol... A client cannot be expected to know beyond the initial URI
  (bookmark)."** Most "REST" APIs required a client to already know every
  endpoint path and parameter shape in advance, usually from a PDF or wiki
  page — none of that is supposed to be necessary.
- **"A REST API must not define fixed resource names or hierarchies (an
  obvious coupling of client and server)."** Hardcoded, client-side
  knowledge of `/api/v2/widgets/{id}/sub-widgets` is exactly the coupling he
  was calling out.
- **"If the engine of application state (and hence the API) is not being
  driven by hypertext, then it cannot be RESTful and cannot be a REST API.
  Period."** Every next action — what you can do from here — must be
  discovered by parsing the current representation's hypermedia controls,
  not by consulting documentation or remembering what the API "usually"
  looks like.

That's a direct description of what `SKILL.md` forces ARTIE to do, and the
session below is effectively a small, live proof of it: no domain
vocabulary, business rules, or endpoint shapes were known ahead of time —
only the bookmark `http://localhost:3000`. Everything else, including how
the available actions *changed* mid-session as the crop's state changed,
came from parsing `_forms`/`_links` in the response actually in hand.
Callouts below (**Fielding note:**) tie specific moments back to specific
complaints from the post.

---

## 1. Cold sync — discover what the server offers

**User:** "tell me what's available at http://localhost:3000, its a HEART server"

Cold start (registry empty) → request full schemas.

**Request:**
```
curl -s -i "http://localhost:3000" \
  -H "Accept-Profile: <https://github.com/jogoshugh/heart-rise>" \
  -H "param:behaviour: interactive"
```

**Response:**
```json
{
  "_links": {
    "self": { "href": "/" }
  },
  "message": "Welcome to the StarbornAg API",
  "_forms": {
    "searchBeds": {
      "method": "GET",
      "_links": {
        "target": { "href": "/api/beds", "templated": false }
      },
      "schema": {
        "type": "object",
        "required": ["search"],
        "properties": {
          "search": { "type": "string" }
        }
      }
    }
  }
}
```

**Reasoned outcome:** exactly one affordance, `searchBeds` — a `GET` on
`/api/beds` requiring a `search` string. Reported this back to the user
rather than guessing a next step.

**Fielding note:** the only thing known in advance was the bookmark URI,
`http://localhost:3000` — no field name (`search`), path (`/api/beds`), or
method was assumed beforehand. That's the "client cannot be expected to know
beyond the initial URI" requirement, satisfied by construction rather than
by convention.

---

## 2. `DEBUG ON` requested

The user asked to turn debugging on mid-session (not present in the initial
prompt, so — per the skill's own rule — it applies from that point forward
rather than retroactively). From here on, every `curl` request/response pair
is printed to screen and appended to a per-session YAML log,
[`20260709-110634-search-beds-jupiter.yaml`](./20260709-110634-search-beds-jupiter.yaml).

---

## 3. Act on `searchBeds` — "search jupiter"

**User:** "search jupiter"

Reasoned: only one affordance exists (`searchBeds`), and "jupiter" maps
directly onto its `search` field.

**Fielding note:** the `href` (`/api/beds`) was never typed or invented by
the assistant — it was lifted verbatim from the `searchBeds._links.target`
entry synced a moment earlier. That's the "must not define fixed resource
names or hierarchies" rule in practice: the client holds no opinion about
what the path *should* be, only what the server most recently said it *is*.

**Request:**
```
curl -s -i "http://localhost:3000/api/beds?search=jupiter"
```

**Response:**
```json
{
  "_links": {
    "self": { "href": "/api/beds?search=jupiter" }
  },
  "_embedded": {
    "results": [
      {
        "name": "Jupiter Bed - Cell 4 (EMPTY)",
        "_links": {
          "details": { "href": "/api/beds/jupiter/cells/4" }
        }
      }
    ]
  }
}
```

**Protocol note flagged to the user:** this response carries resource state
(`_embedded.results`) but **no `_forms` manifest at all**, which §6.1 treats
as non-compliant (state and manifest are supposed to travel together). There
was, however, a plain HAL `_links.details.href` sitting right there in the
result — begging the question of whether the client should be allowed to
follow it even without a matching `_forms` affordance.

**Fielding note:** this is precisely the failure mode his post rails
against in reverse: a body with state but nothing hypertext-shaped to act
on would force a client to fall back on out-of-band knowledge ("I happen to
know `details` links are viewable") to make progress — the one thing a
REST API is supposed to make unnecessary. Flagging it, rather than silently
tolerating or silently improvising past it, keeps the violation visible
instead of normalizing it.

---

## 4. Skill amendment — HAL links as a first-class capability

The user asked for `SKILL.md` to explicitly support plain `GET` navigation of
any `_links.<rel>.href`, independent of whether a `_forms` affordance exists
for it — pointing out that this is core HAL behavior that H.E.A.R.T. layers
on top of, not something H.E.A.R.T. gets to obviate.

Added a new section, **"HAL links (`_links`) vs. H.E.A.R.T. affordances
(`_forms`)"**, and loosened Sync step 3 so a missing `_forms` manifest is
flagged but no longer treated as a dead stop when a usable `_links` entry is
available. The skill was reloaded so the change took effect immediately in
the same session.

**Fielding note:** this closes a gap rather than opening one. `_links` is
plain HAL, and hypertext-driven navigation was always supposed to include
*any* link the server hands over in-band — not just the subset the
H.E.A.R.T. profile happens to also describe as a `_forms` affordance.
Restricting "hypertext-driven" to only forms-with-schemas would have been
its own, narrower violation of the same principle; the fix makes the client
follow *all* server-given hypertext controls, exactly as Fielding's "the
engine of application state must be driven by hypertext" demands, without
ever falling back to a URI the client constructed itself.

---

## 5. Follow `_links.details.href` — Jupiter Bed, Cell 4

Under the new rule, followed the `details` link from the search result as a
plain HAL navigation (no schema, no hash, not gated behind an affordance).

**Request:**
```
curl -s -i "http://localhost:3000/api/beds/jupiter/cells/4"
```

**Response:**
```json
{
  "bedId": "jupiter",
  "cellId": "bed-1-cell-4",
  "status": "EMPTY",
  "_links": {
    "self": { "href": "/api/beds/jupiter/cells/4" },
    "up": { "href": "/api/beds/jupiter" }
  },
  "_forms": {
    "sowSeed": {
      "method": "POST",
      "_links": {
        "target": { "href": "/api/beds/jupiter/cells/4/sow" }
      },
      "schema": {
        "type": "object",
        "required": ["cropType", "seedCount"],
        "properties": {
          "cropType": { "type": "string" },
          "seedCount": { "type": "integer" }
        }
      }
    }
  }
}
```

This resource *is* fully compliant — state and `_forms` together — and now
exposes exactly one affordance, `sowSeed`.

---

## 6. Act on `sowSeed` — "PLANT A SEEDLING" → tomato × 4

**User:** "i want you to PLANT A SEEDLING" → asked for the two required
fields → **User:** "tomato 4"

Reasoned: only one affordance (`sowSeed`) present; mapped "plant a
seedling" onto it. Required fields (`cropType`, `seedCount`) were missing
from the request, so they were asked for explicitly rather than defaulted.

**Request:**
```
curl -s -i -X POST "http://localhost:3000/api/beds/jupiter/cells/4/sow" \
  -H "Content-Type: application/json" \
  -d '{"cropType":"tomato","seedCount":4}'
```

**Response:**
```json
{
  "_links": { "self": { "href": "/api/beds/jupiter/cells/4" } },
  "message": "Action executed. Resource state has changed."
}
```

Per protocol, Act is never terminal — immediately resynced the resource the
response pointed back to.

**Request:**
```
curl -s -i "http://localhost:3000/api/beds/jupiter/cells/4"
```

**Response:**
```json
{
  "bedId": "jupiter",
  "cellId": "bed-1-cell-4",
  "status": "PLANTED",
  "_links": {
    "self": { "href": "/api/beds/jupiter/cells/4" },
    "up": { "href": "/api/beds/jupiter" }
  },
  "_forms": {
    "waterCell": {
      "method": "POST",
      "_links": {
        "target": { "href": "/api/beds/jupiter/cells/4/water" }
      },
      "schema": {
        "type": "object",
        "required": ["volumeMl"],
        "properties": {
          "volumeMl": { "type": "integer" }
        }
      }
    }
  }
}
```

Status flipped `EMPTY` → `PLANTED`, and the manifest evolved: `sowSeed` is
gone, replaced by `waterCell` — a clean example of §10 server evolution
adaptability, adapted within the same sync cycle with no memory of the old
shape.

**Fielding note:** this is "engine of application state" in its most literal
sense — the set of *legal next moves* changed the instant the resource's
state changed, and the client had no choice but to notice, because it never
had an independent model of "what this API lets you do" to fall back on. A
client that had cached "this resource supports `sowSeed`" from earlier in
the conversation and kept offering it would be exhibiting exactly the
stale, out-of-band assumption Fielding's post was written to prohibit.

---

## 7. Attempted "harvest tomatoes" — correctly refused

**User:** "i want you to harvest tomatoes"

The current manifest exposed only `waterCell` — no harvest affordance
existed. Rather than inventing one or picking the closest-sounding `rel`,
this was surfaced plainly to the user as unavailable, with `waterCell`
offered as the only real option.

**Fielding note:** this is the counterfactual that proves the rest of the
transcript isn't just going through the motions. A client that "knew" from
domain familiarity that beds eventually get harvested, and issued a request
to a guessed `/harvest` endpoint anyway, would be reintroducing exactly the
out-of-band, client-side knowledge the hypertext constraint exists to
eliminate. Refusing here is the load-bearing behavior, not a side effect.

---

## 8. Act on `waterCell` — 5 mL

**User:** "water it" → asked for the required `volumeMl` → **User:** "5"

**Request:**
```
curl -s -i -X POST "http://localhost:3000/api/beds/jupiter/cells/4/water" \
  -H "Content-Type: application/json" \
  -d '{"volumeMl":5}'
```

**Response:**
```json
{
  "_links": { "self": { "href": "/api/beds/jupiter/cells/4" } },
  "message": "Action executed. Resource state has changed."
}
```

Resynced again per protocol:

**Request:**
```
curl -s -i "http://localhost:3000/api/beds/jupiter/cells/4"
```

**Response:**
```json
{
  "bedId": "jupiter",
  "cellId": "bed-1-cell-4",
  "status": "PLANTED",
  "_links": {
    "self": { "href": "/api/beds/jupiter/cells/4" },
    "up": { "href": "/api/beds/jupiter" }
  },
  "_forms": {
    "waterCell": {
      "method": "POST",
      "_links": {
        "target": { "href": "/api/beds/jupiter/cells/4/water" }
      },
      "schema": {
        "type": "object",
        "required": ["volumeMl"],
        "properties": {
          "volumeMl": { "type": "integer" }
        }
      }
    }
  }
}
```

Status remains `PLANTED` and `waterCell` is still the only affordance — no
harvest affordance has appeared yet, consistent with a single 5 mL watering
not being enough to mature the crop.

---

## What this demonstrates

- **No hardcoded domain knowledge.** The assistant never knew about "beds,"
  "cells," "sowing," or "watering" in advance — every field name and action
  came from the manifest in the most recent response.
- **Manifest-driven, not memory-driven.** When the affordance set changed
  (`sowSeed` → `waterCell`), the client adapted within the same cycle instead
  of assuming the old shape still applied.
- **Refuses to guess.** When intent ("harvest") didn't map to anything in
  the current manifest, the client said so instead of inventing an
  affordance or picking the nearest-sounding one.
- **Non-compliance is surfaced, not silently patched.** The `searchBeds`
  response missing a `_forms` manifest was flagged to the user rather than
  quietly worked around.
- **The skill itself evolved mid-session** to add HAL `_links` navigation as
  a first-class capability, and took effect immediately without restarting
  the session.

### Against Fielding's 2008 complaints, specifically

| Fielding's complaint | How this session avoided it |
|---|---|
| Clients that know endpoint paths/fields in advance from docs, not hypertext | Only the bookmark URI was known ahead of time; every path, field, and method (`/api/beds`, `search`, `sowSeed`'s `cropType`/`seedCount`, `waterCell`'s `volumeMl`) came from the manifest actually returned. |
| Fixed resource names/hierarchies baked into the client | Every `href` acted on (`/api/beds`, `/api/beds/jupiter/cells/4`, `/api/beds/jupiter/cells/4/sow`, `/.../water`) was copied from a prior response, never constructed. |
| Application state engine *not* driven by hypertext (his central "Period." complaint) | The available action set visibly changed as state changed (`searchBeds` → `sowSeed` → `waterCell`), and the client tracked *that*, not a fixed mental model of the API. |
| APIs that quietly work around missing hypermedia controls instead of treating it as broken | The `searchBeds` response's missing `_forms` was flagged as non-compliant to the user rather than silently patched over. |

Full request/response detail for every call above (as raw pretty-printed
JSON) is in
[`20260709-110634-search-beds-jupiter.yaml`](./20260709-110634-search-beds-jupiter.yaml),
generated automatically by the skill's `DEBUG ON` logging mode.
