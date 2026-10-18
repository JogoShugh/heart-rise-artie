# HAL Schema Forms, H.E.A.R.T. profile

**Status:** draft, 2026-10-08; Rules 6 and 7 added 2026-10-10; Rule 8 added 2026-10-15. Differences only.

**Profile:** `https://w3id.org/heart-rise/hal-schema-forms`

**Base:** HAL Schema Forms, https://github.com/jbadeau/hal-schema-forms
(referred to below as "upstream").

This document lists only what the H.E.A.R.T. profile adds to upstream. Where it
says nothing, upstream applies unchanged. A client that does not know this
profile can still use every L0 response (SPEC §6.10.1): L0 responses use none
of the additions below and stay valid upstream HAL Schema Forms.

The additions exist for one reason. H.E.A.R.T. shares one Layer 2 affordance
set among every resource in the same state, and caches it for a year (SPEC
§6.9). A shared set cannot contain instance identity, and the client must be
able to trust bytes it will keep for a year. Upstream has no way to express
either, so this profile adds two keywords for that purpose. It also names
forms by link relation and asks them to describe themselves, so a client, or
the reasoner behind it, knows what each act means without prior knowledge
(Rules 6 and 7).

Each addition is taken from an existing standard. Nothing here is a new
mechanism.

| Addition | Taken from |
|---|---|
| `hr:templatePointers` | JSON Hyper-Schema `templatePointers` (draft-handrews-json-schema-hyperschema, 2019-09); JSON Pointer (RFC 6901) |
| `integrity`, `hr:integrity` | W3C Subresource Integrity (the `integrity` attribute) |
| Schema by `$ref` | JSON Schema `$ref` |
| `Repr-Digest` | RFC 9530 |
| Form names as relations | RFC 8288 link relation types; HAL CURIEs |
| Relation descriptions | ALPS profile documents |
| Refusal bodies | RFC 9457 problem details |
| `hr:options` | HAL-FORMS `options` (`link`, `promptField`, `valueField`, `minItems`, `maxItems`); JSON Pointer (RFC 6901); URI Template (RFC 6570) |

The `hr:` prefix is the HAL CURIE the server declares in `_links.curies`,
expanding to `https://w3id.org/heart-rise/rels/{rel}`.

## Rule 1: template pointers

Upstream says a templated target "MUST be expanded using the form fields".
That still holds. This profile adds a second source for a variable: the
resource that owns the form.

A form MAY carry `hr:templatePointers`, an object whose keys are template
variable names and whose values are JSON Pointers (RFC 6901). Each pointer is
resolved against the JSON body of the **owning resource**: the Layer 1
resource whose response linked to, or embedded, the form.

```json
{
  "_links": { "target": { "href": "/beds/{id}/actions/water-cell", "templated": true } },
  "hr:templatePointers": { "id": "/id" },
  "method": "POST",
  "contentType": "application/json",
  "schema": { "$ref": "/schemas/db7bab9848b1b240", "hr:integrity": "sha256-…" }
}
```

A client fills each variable from the first of these that applies:

1. **A pointer** in `hr:templatePointers` for that variable.
2. **A form field** of the same name (upstream).
3. **The owning resource's top-level property** of the same name. This is the
   default JSON Hyper-Schema defines for a variable with no
   `templatePointers` entry.

The value found SHALL be a string or a number. A pointer that resolves to
nothing, to an object, to an array, to a boolean or to null is an error, and
the client SHALL NOT send the request. A variable that none of the three fills
is the same error.

Template expansion is done by deterministic client code, never by a reasoner
(SPEC §6.8). Templates are RFC 6570 Level 1 unless the server declares
otherwise (SPEC §6.9.1).

A server using this profile SHOULD declare a pointer for every variable that
does not come from a form field. The default in step 3 exists so that a form
with no pointers still has one defined meaning. It is not the preferred way
to bind a variable.

**Why this is in the representation.** The form tells the client where each
value comes from. The client assumes nothing about the structure of the
owning resource beyond what the form declares. This is the kind of instruction
Fielding allows: servers instructing clients "on how to construct appropriate
URIs, such as is done in HTML forms and URI templates, by defining those
instructions within media types and link relations."

## Rule 2: integrity

A link MAY carry `integrity`. A schema reference MAY carry `hr:integrity`.
Both use the Subresource Integrity syntax: a hash algorithm (`sha256`,
`sha384` or `sha512`), a hyphen, and the Base64 digest of the target's bytes.

```json
"hr:forms": {
  "href": "/affordance-sets/e16765e81df9b76f",
  "integrity": "sha256-4Wdl6B35t29fbzeifgr3sQnIDRyXH5BGwWGvIb7x5yQ="
}
```

When it fetches a target that carries an integrity value, a client:

1. Hashes the response body exactly as received, before any parsing.
2. Compares the result with the integrity value, byte for byte.
3. On a mismatch, refuses the response: it does not parse it, cache it, or
   pass any of it to a reasoner.

Inside a schema object the keyword is `hr:integrity`, not `integrity`, so that
it cannot clash with a future JSON Schema keyword. JSON Schema ignores
keywords it does not know, so a validator is unaffected.

A server using this profile SHALL send `integrity` on the Layer 1 link to an
affordance set, and `hr:integrity` on every Layer 2 schema reference. The
digest is always the full digest, never truncated.

A client SHALL refuse, before any request, a link to an affordance set or a
schema reference that carries no integrity value. A client MAY offer a
setting that follows such links unchecked, for servers written before this
rule; it is off by default, and a link that does carry a value is always
checked.

**The client reads nothing from a URI.** A server may name content-addressed
resources by their hash (for example `/schemas/db7bab9848b1b240`), or
any other way. That is the server's own naming, and this profile defines no
paths (Fielding's Rule 4: no fixed resource names). A client SHALL NOT read a
hash, or any other meaning, from a URI. The expected digest comes only from
the integrity value in the linking document.

**Why the linking document.** Layer 1 is revalidated on every visit, so the
client has just confirmed it is current. Each layer then vouches for the next:
Layer 1 for the affordance set, the affordance set for each schema. This is
how npm lockfiles, Bazel and Nix pin downloads.

## Rule 3: schema by reference

A form's `schema` MAY be a JSON Schema `$ref` to a separately fetched schema
(SPEC §6.10.3, the L2 rung), with `hr:integrity` beside it:

```json
"schema": { "$ref": "/schemas/db7bab9848b1b240", "hr:integrity": "sha256-…" }
```

The client fetches the referenced schema, checks it under Rule 2, and uses it
as the form's schema. Earlier H.E.A.R.T. servers sent `{"$id": …}` in this
position. That misused `$id`, which names a schema rather than pointing to
one. A client MAY still accept it from older servers.

## Rule 4: Repr-Digest (optional)

A server SHOULD send `Repr-Digest` (RFC 9530) on Layer 2 and Layer 3
responses, in RFC 8941 syntax, for example
`Repr-Digest: sha-256=:4Wdl6B35t29fbzeifgr3sQnIDRyXH5BGwWGvIb7x5yQ=:`.

A client that receives it SHOULD check it and refuse a mismatch. A client
SHALL NOT require it. The header shows that the bytes arrived as the server
sent them. It cannot show that the server sent the right bytes, because the
response vouches for itself. Rule 2 covers that.

## Rule 5: form quality

Every form and every schema property SHOULD have a `title` and a
`description`, with units where they apply. Every schema SHOULD declare
`$schema`.

## Rule 6: form names are link relations

A form's key in `_forms` is a link relation type (RFC 8288), written either as
a full URI or as a CURIE declared in `_links.curies`, exactly as a link's key
is. A client expands a CURIE before comparing names, so `garden:water` and the
URI it expands to are the same form.

```json
"_links": {
  "curies": [{ "name": "garden", "href": "/profiles/garden#{rel}", "templated": true }]
},
"_forms": {
  "garden:water": {
    "title": "Water the bed",
    "description": "Adds water to every cell. A planted bed becomes watered.",
    "_links": { "target": { "href": "/beds/{id}/actions/water-cell", "templated": true } },
    "hr:templatePointers": { "id": "/id" },
    "method": "POST",
    "contentType": "application/json",
    "schema": { "$ref": "/schemas/…", "hr:integrity": "sha256-…" }
  }
}
```

A relative CURIE `href` is resolved against the representation's own URL, so
the expanded relation is always an absolute URI. The relation URI SHOULD
identify a descriptor in an ALPS profile document (SPEC §8.5). That document
says in prose what the act does, the state it leads to, and when it is
offered, and its descriptor `type` (`safe`, `unsafe`, `idempotent`) agrees
with the form's method. A client fetches it only when it wants more than the
inline text. It describes; it never makes an act legal.

Upstream names forms with plain strings. An upstream client that compares the
key as a plain string still works with the CURIE as written.

## Rule 7: forms describe themselves

For forms in an affordance set this strengthens Rule 5. Each form SHALL carry
a `title` and SHOULD carry a `description`. Each schema property SHOULD carry
the JSON Schema annotations `title` and `description`, and `examples` where a
typical value helps, with units in the description where they apply.
Annotations change no validation result.

Text is part of the bytes, so it is covered by the set's or the schema's
`integrity` value like everything else, and it is cached with them. It SHALL
NOT include instance identifiers (SPEC §6.9.4).

## Rule 8: choices from state

A property of a form's schema MAY carry `hr:options`: the choices for that
property, taken from the owning resource or from another resource. JSON Schema
2020-12 lets a schema carry keywords it does not define, so a validator that
does not know this profile ignores it. Fixed choices stay JSON Schema `enum`;
`hr:options` is for choices that depend on state.

`hr:options` is an instruction, not the values. It says where the choices are,
so the form stays the same bytes for every resource in the same state and the
set that holds it stays shared (SPEC §6.9). The values come from the owning
resource, or from a resource the client reads when it fills the form.

```json
"playerNumber": {
  "type": "integer",
  "hr:options": {
    "pointer": "/{team}/players",
    "valueField": "number",
    "promptField": "name",
    "where": { "fouledOut": false },
    "minItems": 1,
    "maxItems": 1
  }
}
```

```json
"agent": {
  "type": "string",
  "hr:options": {
    "link": { "href": "/agents{?skill}", "templated": true },
    "templatePointers": { "skill": "/category" },
    "items": "/agents",
    "valueField": "name",
    "promptField": "name",
    "where": { "onDuty": true }
  }
}
```

Its members:

- **`pointer`** or **`link`**, exactly one. A `pointer` is a JSON Pointer
  (RFC 6901) into the owning resource's JSON body, resolved as Rule 1
  resolves template pointers. A `link` is an object like a HAL link, with
  `href` and `templated`; the client reads it with GET, and it follows the
  rules of any Layer 1 resource (SPEC §6.1): no-cache, revalidated by ETag.
- **Templates.** A `pointer` MAY be an RFC 6570 Level 1 template whose
  variables are other properties of the same form. That is how one choice
  depends on another: the team first, then that team's players. A `link`'s
  template variables are filled from `templatePointers` first, which point
  into the owning resource as in Rule 1, then from properties of the same
  form.
- **`items`**: for a `link`, a JSON Pointer to the list in the answer; by
  default the answer is the list.
- **`valueField`** and **`promptField`**: for items that are objects, the
  member that is sent and the member that is shown. They default to `value`
  and `prompt`, as in HAL-FORMS. Items that are strings or numbers are both
  value and prompt.
- **`where`**: an object of member names and values; only items whose members
  equal every one of them are choices. It is equality only, so every client
  applies it the same way.
- **`minItems`** and **`maxItems`**: as in HAL-FORMS. With `maxItems` 1 the
  property is one value; otherwise it is an array of values.

What resolves to nothing, or to something other than a list, gives no
choices. A client SHALL offer only the choices it resolved, and SHALL NOT send
a form whose required property has none: the act cannot be filled now.

A server SHALL refuse a value that is not among the choices it computes when
the act arrives, with 422 and the `invalid-input` problem type, at that
property (SPEC §6.2). For a `pointer`, it computes them from the state the
act's `If-Match` matched, so a client that read an older state gets 412
first. For a `link`, the other resource may have changed since the client
read it; the refusal says so.

A HAL-FORMS rendering of the same form (SPEC §6.10.6) carries the choices as
HAL-FORMS `options`: those from a `pointer` inline, resolved from the owning
resource, and those from a `link` as `options.link`.

## Rule 9: embedded resources carry no forms

HAL Schema Forms says `_forms` "MAY appear on any HAL document (including
embedded HAL documents)". This profile narrows that: a resource object inside
`_embedded` carries no `_forms`, and no `hr:forms` link. It carries
`_links.self`, its state, and any other links.

The reason is the precondition. Every act sends `If-Match` with the `ETag` of
the resource it acts on (SPEC §6.3), and an embedded copy arrives with no
`ETag`. A client that wants to act on an embedded resource follows its `self`,
and acts from what that returns.

Pointers don't reach into `_embedded`. Rule 1's template pointers and Rule 8's
choices resolve against the owning resource's state, which is its body without
`_links`, `_embedded`, `_forms` and `_templates`.

(Decided 2026-10-18; SPEC §6.1. A possible remedy, embedded validators, is
filed as `spec/ideas/embedded-forms-with-validators.md`.)

## Rule 10: each form's own integrity

Every form a server writes carries `hr:integrity`: the integrity (Rule 2's
`sha256-` form) of a JSON object holding these members of the form, in
canonical form (RFC 8785):

| Member | Value |
|---|---|
| `target` | the target's URI template, as served at L1 and L2 (never the expanded address) |
| `hr:templatePointers` | the form's template pointers (Rule 1) |
| `method` | the form's method |
| `contentType` | the form's content type |
| `schema` | the integrity of the form's schema body |

Not the form's `title` or `description`: a retitled form does the same. The
value is the same at every rung, whether the schema is embedded or referred to
(Rule 3), and for every resource whose set offers the form.

An affordance set's integrity changes when any form in it changes; a form's
changes only when what that form does changes. A client replaying a recorded
act uses the two: lenient, it acts when the form it used still has its
integrity, in whatever set holds it now; strict, it acts only when the set
itself is unchanged (`spec/ideas/replay-guards-lenient-strict.md`). Both
still check every fetched body against its link's integrity (Rule 2).

(Decided 2026-10-18; SPEC §6.9.)

## Not covered here

- Refusal bodies are RFC 9457 problem details; the five `type` URIs and the
  `violations` member are in SPEC §6.2.
- Explaining absent acts (`hr:unavailable`) is in SPEC §6.12.
- `If-Match` scope for acts. See SPEC §6.3.
