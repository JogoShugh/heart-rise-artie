# Trimming representations: query parameters and defaults

An idea, filed 2026-10-18. Not started.

## Why

Lifting GitHub's link members into typed links and embedded resources made pull request #20 grow
from 19.2 KB to 27.5 KB (docs/reports/gateway-links). Every link now carries its type, and each
embedded repository carries about forty links of its own. Nothing was dropped, which is right as
a default. But a client often wants less: a model's context, a phone, a summary.

## The idea

Let a client ask for less, and let a server, or a user, choose what "less" is by default.

- **Query parameters.** A client asks for a trimmed representation of the same resource. For
  example, only some relations, no embedded resources, embedded resources as self links only, or
  no links of a type (`image/*`).
- **Server-side defaults.** A connector or a HEART server says what is sent when nothing is asked:
  for instance, embedded repositories with their self and alternate only.
- **User-specific defaults.** A caller's own standing preference, kept by the server and applied
  to every answer for that caller.

## Questions to settle when it is taken up

- Query parameters or `Prefer` tokens? A trimmed answer is a different representation, so it
  needs its own ETag and URL, and query parameters make it a different resource. `Prefer` keeps
  the resource's address and names the trim in `Preference-Applied`, like HEART's rungs.
- How trimming meets caching and content addressing: affordance sets are unaffected, since they
  carry no per-resource data. Layer 1's ETag already follows its bytes.
- What may never be trimmed: `self`, the affordance-set link, `hr:unavailable`, and any link a
  form's template pointers need.
- Whether a trimmed-away link is still discoverable, for example by a link to the untrimmed
  representation.
