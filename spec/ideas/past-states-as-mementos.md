# Idea: past states as Mementos, with the acts offered then

**Status:** idea only, 2026-10-11. Nothing here is decided or built. Raised
during the bed-one review (issue 4: a past state offers acts). Waits for the
review pause to end.

## The problem

`GET /beds/1?asOf=<instant>` rebuilds the past state from events and renders
it exactly like the current state, `_forms` included (heart-spring
`past.feature`: the past "reads exactly like the current state").

Two things are wrong with that:

1. **The forms look live but can never work.** Bed 1 as of just before its
   harvest shows a `garden:harvest` form with ETag `"seq-3.hsf.L0"`. The bed
   is now at seq 4, so following that form always answers 412. A model reading
   the past representation may conclude "harvest is possible now".
2. **The forms are not the ones offered then.** They are projected again by
   today's code. When the code changes, the past changes. This already
   happened: 7f17677 (NotBlank) retired affordance sets `049531dec816f02f`,
   `7c9b4beac495ed3f` and schema `5b67c4473aad58b8`, so a past state from
   before that commit can no longer show what it really offered.

Yet what was offered at a moment is valuable for debugging: "what could the
client do at seq 3?", "did the client act on something not offered?".

## The git analogy

A git commit does not copy its files. It stores one hash, the tree hash, and
the tree lives in an object store under that hash forever. Old commits stay
exact after the code that wrote them changes, and each distinct tree is stored
once.

HEART already has the tree half: affordance sets and schemas are
content-addressed and immutable. Missing are the commit half (an event records
which set was offered) and the object store (sets are computed from code, not
kept).

## The idea

- **Record on append.** Each event's metadata stores the digest of the
  affordance set served for the state it produced, and the digest of its
  `hr:unavailable` document. 32 bytes each, per event.
- **Keep the bytes.** A content-addressed table, digest to exact bytes,
  written once, never deleted. Schemas the sets reference are kept the same
  way (nested, like trees). Few distinct sets exist (one per state key per
  code version), so it stays small.
- **Render the past as inert.** No `_forms` in a past representation. A link
  with a HEART relation (working name `hr:offered-then`, never `hr:forms`)
  points to the recorded set, with `integrity`. Fetching it is a GET of a
  document; nothing in it can be acted on, because an act needs the current
  ETag.
- **Events written before this** have no recorded digest. Their past
  representation carries no `hr:offered-then` link rather than a guess.

## Standards that apply (read 2026-10-11)

**RFC 7089, Memento** (Informational, December 2013, Independent Submission;
not IETF-endorsed, never obsoleted or updated, no successor draft found).

- Roles: Original Resource, Memento (a past state with its own URI), TimeGate
  (picks a Memento for a requested time), TimeMap (list of Mementos).
- `Memento-Datetime` response header, HTTP-date in GMT, whole seconds. Its
  presence "constitutes a promise that the resource state reflected in the
  response will no longer change". Today's re-projection breaks that promise;
  recording the digest keeps it.
- Link relations `original` (exactly one on a Memento), `memento` (with a
  `datetime` attribute), `timegate`, `timemap`.
- TimeMap serialization `application/link-format` (RFC 6690) MUST be
  supported. CoAP already speaks it.
- Pattern 4: Mementos without a TimeGate. Fits HEART best.
- Memento is only about GET and HEAD. It says nothing about forms or unsafe
  methods, and allows a Memento's bytes to differ from the original (URI
  rewriting, banners). So leaving forms out is allowed, but HEART must state
  the rule itself.

**Replaced references inside Memento:** RFC 5988 is now RFC 8288 (Web
Linking); the RFC 1123 date is now the RFC 9110 HTTP-date. Cite the new ones.

**RFC 5829, version navigation link relations** (Informational, 2010):
`version-history`, `latest-version`, `predecessor-version`,
`successor-version`. These navigate by version, not time. Bed 1's seq 1 to 3
fell within one second, so time alone cannot name them; a sequence can.

**RFC 8246, `Cache-Control: immutable`.** A Memento never changes.

**SRI and RFC 9530 `Repr-Digest`.** Already used; they cover the
`integrity` on the `hr:offered-then` link.

**CoAP:** no Memento binding or `Accept-Datetime` option found. Use Mementos
by URI and a link-format TimeMap; no new option needed.

## Possible shape

- A past state's URI pins a version: `/beds/1/versions/3` or `?asOf=seq-3`.
  An instant may still be accepted and resolved to a version.
- Its response carries `Memento-Datetime`, `Link: original`,
  `predecessor-version`, `successor-version`, `Cache-Control: immutable`,
  and `hr:offered-then` with `integrity`. No `_forms`.
- `/history` becomes the TimeMap: link-format, with HAL as an alternative.

## Open questions

- Version in the path, or `?asOf=seq-N`, or both?
- Is `hr:offered-then` the right relation name, and should
  `hr:unavailable-then` exist too?
- Must Layer 1 at L0 (inline forms) also record the inline form bytes, or is
  the affordance-set digest enough, since L0 is rendered from the same set?
- Keep the object store in heart-eventsourcing, or in heart-core so a server
  without an event store can still pin what it offered?
- A server whose `offers` never changed between versions: is re-projection
  then acceptable, or is recording always required?

## When it is built

Feature first: change `past.feature` and write the SPEC section on the past
(review issue 7, the past is not in SPEC yet), then heart-eventsourcing and
heart-spring.
