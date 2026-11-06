# Plan: "Wayfinding to Rails"

Status: outline, revised 2026-10-27 after the day's live runs. Not written yet. Published as an artifact, then copied
to `docs/reports/wayfinding-to-rails/` once approved.

## The one theme

Information and controls, built back up from the bottom. Today's common starting point is the divorce of the two:
an API hands over data, and the rules of what may be done live elsewhere, in docs, prompts and client code. The
piece starts there, with the barest client, and adds one piece of hypermedia per stop, until the client is a generic
one coupled only to media types, as a browser is, and the server's controls carry the rules. The model is used where
it earns its keep: to read the goal once.

One task throughout: the merge watch (`exhibits/merge-watch/merge.goal.md`), on the scratch repository. Concepts over
metrics; each stop gets one short proof from a live run, never a trace dump or a history of fixes.

## Outline

1. **Opening: four of Fielding's sentences, verbatim** (checked against the source before publishing).
   - "REST is software design on the scale of decades…" (comment, 20 October 2008, 11:03 am). The anchor.
   - "I am getting frustrated by the number of people calling any HTTP-based interface a REST API… That is RPC."
   - The banking one: "How many Web browsers are aware of the distinction between an online-banking resource and a
     Wiki resource?…", which ends on "automated agents".
   - "…the simultaneous presentation of information and controls such that the information becomes the affordance
     through which the user (or automaton) obtains choices and selects actions."
   - Then the diagram of the divorce: the rules drift away from the server and go stale (from Information and
     Controls). "We start where most systems are today, and build up to his architecture."
2. **What people complain about.** Five of the other R's, the ones agents fail on in the wild: rate (cost),
   reliability, right behaviour, recency (drift), repeatability. One line each, sourced. Each later stop wears the
   badge of the R it buys back. The rest of the R's link to "The Other R's".
3. **The goal**, in plain text, as a person writes it.
4. **Bare: naive and fast.** `merge.bare.kts`, the goal's words over GitHub's own addresses, generated from a run.
   - It looks like an Arazzo workflow: steps, operations, success criteria, retries.
   - The proof: a pull request one commit behind its base, which GitHub calls clean, is merged.
   - The point: bare is safe exactly where the service enforces its own rules, and only there. A rule the service
     lacks (here, "up to date before merge", which this repository doesn't require) is nobody's.
   - The table: when bare makes sense, when it is inherently risky.
5. **The gateway: from a bag of endpoints to controls in the answer.** The hinge of the piece, given room of its own,
   with one diagram: OpenAPI operations on the left, the connector's YAML in the middle, a pull request's HEART
   representation on the right, its offered forms changing with its state.
   - **Scope, said once, then kept.** GitHub's description has 1,242 operations; the connector reads 32 of them
     (pull requests, issues, comments, repositories, branches). The detail covers only what the merge watch
     touches: the repository, the watch issue, the pull request list and one pull request, and its two acts, merge
     and comment. Five forms in the lock file, one link. Everything below is shown on that slice.
   - **What it starts from.** GitHub's own OpenAPI description: a catalogue of operations, each a method, a path and
     a request schema, true in every state. It says what can be called, never what may be done now. Only the
     operations the connector names are read (`connector.yaml`, `include`).
   - **Resources, not operations** (`pull.resource.yaml`). Which operation reads one pull request, which lists them,
     which address it lives at. Each act is a projection of one operation, given a name: `merge` is `pulls/merge`
     with three of its fields; `comment` is `issues/create-comment`. Each form's schema is cut from the operation's
     request body, so it is GitHub's own, and content-addressed, so a client can pin it.
   - **Values bound from state.** A field the client shouldn't have to know is filled from what it holds: `merge`
     binds `sha` to `/head/sha`, so GitHub's own check that the head hasn't moved becomes part of the act; the
     client never sends it.
   - **What may be done now** (`pull.reader.yaml`). GitHub's answer says nothing about what is allowed, so the reader
     says it: by state first (open, closed), then by ordered rules over several members, each making an act absent
     with a reason: a draft, GitHub still checking, conflicts, blocked by protection, no push permission. A rule may
     read a fact from another of GitHub's answers: whether the caller can push is on the repository, not the pull
     request.
   - **Links, not URLs.** GitHub's `*_url` members and `_links` become HEART links and leave the state; nested users,
     labels and repositories are embedded resources of their own. Links into the API the gateway serves point back
     at the gateway.
   - **HTTP's own safety, added.** Every representation has an ETag; every act needs `If-Match`; a change since it
     was read is a 412, not a lost write. GitHub's refusals are mapped to HEART's: its 405 "can't merge now" is the
     act not offered (409, with the reason), its 409 "head moved" is stale (412). Members GitHub works out by itself
     (`mergeable`) don't make a client's view stale. Reads upstream are revalidated with `If-None-Match`.
   - **Configuration only.** No code per API: the same gateway, with another connector folder, fronts another
     service. A client written for HEART, ARTIE or another, works with every one.
   - **Then, the rule GitHub doesn't have.** The proof of the stop: the reader adds one fact, GitHub's compare of the
     base with the head, and one rule: while the branch is behind, merge is absent, "The branch is behind its base;
     update it first." One rule, in one place, for every client. ("Little control of GitHub? Automate it from your
     laptop with your own gateway.")
6. **Off the rails: controls arrive.** The same words, through the gateway, the client trusting. The behind pull
   request is held back, and its author is told why, in the gateway's words. The server guards even a trusting
   client. A wait is the task's, so even off the rails the watch sees someone close it.
7. **Look then leap: check the rooms, not only the door.** One look at the entry's manifest of every form the server
   declares. `--look used`: leap unless a form the script uses changed. `--look everything`: leap only if nothing
   changed anywhere. The proof: a changed form the watch never uses; used leaps, everything stays careful. Both say
   which way they went.
8. **Lenient, the default: each act checked.** Every act's form held to its pin as it is used: survives changes that
   don't touch it, refuses before harm when one does.
9. **Strict: nothing unrecorded.** A change anywhere in the set beside an act stops that act, every pass, and the
   watch keeps watching. For audited work. Honest note: a reasoned line's words are the model's, not pinned.
10. **The top: wayfinding.** The goal run itself: the model reads the plain text and chooses each step from what the
    server offers. Every stop below was made from one such run, and a fresh run made the same script again. Reason
    once, then ride the rails at the care you choose.
11. **The climb, in one table.** Rows bare to wayfinding. Columns: who enforces the rules; what each survives (a
    behind pull request, a changed form, someone else's change); relative cost per pull request (rough, single runs);
    reasoning: the same small amount on every rail, paid in full only at the top.
12. **Closing.** Back to "software design on the scale of decades": with AI in the loop, the long-term design is also
    the cheaper one in the short term. Architecture in the loop, so that people and models step out of it. A teaser:
    a new system deployed during a run, followed by its links (spec/ideas/new-system-mid-run.md).
13. **Appendix** (later): every rung on one fixed scene, several interleaved rounds, for firmer numbers.

## What changed since the first outline (2026-10-27)

- Bare comes first, not last: the climb goes up from it, adding hypermedia stop by stop.
- The safety scene is "behind its base": real, live, and the gap between GitHub's rules and the gateway's.
- A stop of its own for the gateway: how it turns an OpenAPI description into state-based controls (resources,
  acts as projections of operations, values bound from state, rules with reasons, facts, links, ETags and mapped
  refusals), ending on its own rule, the pitch for running one's own gateway.
- Look then leap now looks at every form the server declares, used or everything, not only the entry's.
- Model calls stay out of the stops: one sentence in the table and the closing, as the explainer memory asks.
- The opening gains the "scale of decades" quote and the five R's.

## Not in it

Fix histories, trace dumps, per-run model call counts, the 45 MB trace. The run records stay in
`exhibits/merge-watch/runs/` for anyone who wants them, linked once.
