# An act's target, read: richer answers, all opt-in

**Status:** idea, 2026-10-25. The base is built (SPEC §6.2, "An act's target is a resource"): `GET` on an
act's target answers with a small representation under its owner's ETag, linking its form where it already
lives. That closed the stretch of RFC 9110 the Fielding review found under Rule 2. What follows are the richer
answers the same address could give. None is built.

## The rule every one of them keeps

An advanced reading exists only where the server says so: in a header it applies (`Preference-Applied`), in a
dialect it serves (`Accept`), or in a link it offers. No version numbers, no out-of-band flags. A client that
knows none of them sees only the base, and a server that offers none of them is complete.

## The holes a form has

A shared form has holes the owner's state fills: the target's `{id}`, through `hr:templatePointers`
(HAL-SCHEMA-FORMS-HR Rule 1), and the choices, through `hr:options` pointers such as `/{team}/players`
(Rule 8). The base answer leaves them to the client, which already holds the owner. The variations below fill
some or all of them on the server.

| | What the act's `GET` returns | Per-resource bytes | Client needs the owner? | How a client asks for it |
|---|---|---|---|---|
| **V0, built** | links `self`, `up`, `hr:form` (to the shared form, with its integrity), and `ownerETag` | tiny | yes, to fill the holes | nothing: the default |
| **V1, with what the form reads** | V0, plus only the owner's members the form points at (`/id`, `/home/players`), as a partial `_embedded` owner | small | no | a `Prefer` token, e.g. `act=context` |
| **V2, the resolved form** | the form with its target filled, its choices inline and its defaults filled | per resource | no | `Accept: application/prs.hal-forms+json`: the HAL-FORMS rendering already does this, and is served there today |
| **V3, shared form and bindings** | a link to the shared form, and a small document of this resource's values: `{target, options: {playerNumber: […]}, defaults}` | small | no | a `Prefer` token, e.g. `act=bindings` |
| **V4, narrowing** | `GET …/record-foul?team=home`: the form narrowed by the fields given so far, here only the home players still in | small, per query | no | a templated link the base offers, e.g. `hr:narrow` `{?team,playerNumber}` |
| **V5, a dry run** | `GET …/record-foul?team=home&playerNumber=8`: whether the act would be taken, and if not why ("8 is not one of the choices now"), with nothing done | small | no | a templated link, e.g. `hr:check` |

V3 follows the layering's own idea one level down: what is shared stays shared, and only the per-resource
values are per resource. V4 and V5 are the ones an agent would use most: "who can I choose if the team is
home?" and "would this be taken?" are questions a model or a script can ask the server instead of working out,
and both are safe reads, revalidated like any Layer 1 resource.

## The ETag's scope

The base keeps the owner's ETag: any change to the owner makes a pending act stale. That is safe, and sometimes
more cautious than it needs to be: a basket by one player makes a foul on another 412.

A body that holds only the slices the form reads (V1, V3, V4) could carry a narrower validator, one that
changes only when those slices do. Conflicts would then arise only over what this act depends on: fewer
needless 412s. The cost is that a domain rule reading other state is no longer covered by `If-Match`; the
server still checks its rules when the act arrives (422 `rule-refused`), so nothing unsafe is done, but a
decision made on stale wider state could go through. That is per-act optimistic concurrency, and a declaration
could let each act choose it. The default stays the owner's ETag.

## Also not yet

- `GET` on an act the bookmark offers, at `/actions/{path}`. Its owner is the service index, whose forms are
  inline rather than in a shared set, so `hr:form` would point into the index itself.
- The `hr:narrow` and `hr:check` relations, and the `Prefer` tokens, would each need a page under
  `https://w3id.org/heart-rise/` before they are used (`standards/identifiers.json`).
