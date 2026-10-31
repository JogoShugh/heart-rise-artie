# Forms on embedded resources, with their own validators

An idea, filed 2026-10-18. Not started. A possible future remedy for decision D1 of
`spec/PLAN-HAL-EMBEDDED.md`.

## Where it comes from

HAL Schema Forms allows forms on embedded resources: "This property MAY appear on any HAL document
(including embedded HAL documents)" (github.com/jbadeau/hal-schema-forms, README, line 68). HEART's
profile narrows that for now: no `_forms` or `_templates` inside `_embedded`, because every HEART
act sends `If-Match`, and an embedded copy arrives without an ETag. To act on an embedded
resource, a client follows it first.

That costs a request per act on an embedded resource. On a page of twenty beds, watering each one
means twenty reads before twenty acts.

## The remedy

Let an embedded resource carry forms when it also carries its validator:

- The embedded resource's `_links.self` gets the ETag its own GET would answer with, as a target
  attribute (a HEART extension, say `hr:etag`, since RFC 8288 defines none for this).
- A client acting from the embedded copy sends that value in `If-Match`. If the resource moved on,
  the answer is 412, exactly as for a stale held resource.
- The forms follow the same layering as the top level: inline at L0, or an `hr:forms` link to the
  shared affordance set at L1 and L2, so sets stay shared.

## What stands in the way

- **The validator must be exact.** It is the ETag of the embedded resource's own representation
  (dialect, rung, digest of its canonical bytes). heart-core can compute that, because it renders
  the resource; it means rendering each embedded resource in full to hash it, even when only a
  summary is embedded.
- **The gateway often can't.** An upstream API embeds partial objects (GitHub's "simple user"), so
  there is no full representation to hash without a read per embedded resource, which is the cost
  this idea set out to save. The gateway would embed forms only where it has the full upstream
  object, or never.
- **The parent's validator.** The embedded ETags are part of the parent's bytes, so the parent's
  ETag changes whenever any embedded resource changes. That is true already of embedded state.

## When to revisit

After `_embedded` is built (PLAN-HAL-EMBEDDED.md), when collections embed their items, and when a
real script shows the extra reads mattering.
