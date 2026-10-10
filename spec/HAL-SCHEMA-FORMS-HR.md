# HAL Schema Forms, H.E.A.R.T. profile

**Status:** draft, 2026-10-08. Differences only.

**Profile:** `https://github.com/jogoshugh/heart-rise/hal-schema-forms`

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
either, so this profile adds two keywords, and nothing else, for that purpose.

Each addition is taken from an existing standard. Nothing here is a new
mechanism.

| Addition | Taken from |
|---|---|
| `hr:templatePointers` | JSON Hyper-Schema `templatePointers` (draft-handrews-json-schema-hyperschema, 2019-09); JSON Pointer (RFC 6901) |
| `integrity`, `hr:integrity` | W3C Subresource Integrity (the `integrity` attribute) |
| Schema by `$ref` | JSON Schema `$ref` |
| `Repr-Digest` | RFC 9530 |

The `hr:` prefix is the HAL CURIE the server declares in `_links.curies`,
expanding to `https://github.com/jogoshugh/heart-rise/rels/{rel}`.

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
  "schema": { "$ref": "/schemas/sha256-db7bab9848b1b240", "hr:integrity": "sha256-…" }
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
  "href": "/affordance-sets/sha256-e16765e81df9b76f",
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

**The client reads nothing from a URI.** A server may name content-addressed
resources by their hash (for example `/schemas/sha256-db7bab9848b1b240`), or
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
"schema": { "$ref": "/schemas/sha256-db7bab9848b1b240", "hr:integrity": "sha256-…" }
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

## Not covered here

- Form names as link relations (CURIE names for forms). Proposed, not
  adopted.
- The error format (`application/vnd.error+json` or the current
  `violations` body). Open.
- `If-Match` scope for acts. See SPEC §6.3.
