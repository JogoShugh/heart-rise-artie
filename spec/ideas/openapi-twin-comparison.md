# Idea: an OpenAPI twin of the garden, to compare agents

**Status:** idea only, 2026-10-11. Not started. New work, so it waits for the
review pause to end.

## The idea

Build an "alternate universe" version of at least the garden API, described
the usual way: an OpenAPI document with fixed endpoints, and no state-based
affordances. The same domain, the same beds, the same rules; only the
interface differs.

Then compare how agents do against each interface:

1. **Now:** a standard LLM-only agent reading the OpenAPI document, against an
   LLM-only HEART client reading the garden's forms.
2. **Later:** the ARTIE client against an existing MCP client from the wild
   that turns an OpenAPI document into tools.

The point is to measure what state-based affordances give an agent over a
static description of every operation, rather than to argue it.

## What the OpenAPI twin would look like

- One document lists every operation at once: get a bed, sow, water,
  harvest, with request schemas. Nothing says which operation is legal for
  the bed's current state.
- The server still enforces the same rules, so an illegal call is refused
  after it is sent (for example harvesting an empty bed), where HEART never
  offers it.
- Plain JSON bodies, no `_forms`, no `hr:unavailable`, no ETag-scoped acts
  unless OpenAPI users would normally add them.
- It runs over the same garden domain code, as a separate server or profile,
  so the two worlds differ only in the interface. The HEART garden is left
  as it is.

## What to measure

The metrics already planned for the eval
(`spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md` Part C, SPEC §10) apply to both
worlds, with the same cases:

- **Precondition sensitivity:** when the request is not legal now, does the
  agent ask or explain, or does it call anyway?
- **Selection accuracy:** the right act with the right values.
- **Calls refused by the server:** the OpenAPI agent can send illegal calls;
  count them. A HEART client cannot pick an act that is not offered.
- **Turns and tokens:** how many calls and how much context each agent needs
  to reach the goal, including reads made only to learn the state.
- **Cost:** latency and tokens, cache reads and writes shown apart.

## Open questions

- Is the twin a separate Spring app, or a second set of routes in the
  garden server behind a profile?
- Which MCP client from the wild reads OpenAPI and is fair to compare against?
  Pick one widely used, and pin its version.
- How is the OpenAPI document written: by hand as a typical team would, or
  generated (springdoc) from controllers? Hand-written may be fairer to
  OpenAPI's best case; generated is what most teams ship.
- Same model, same prompts, same cases for every agent; how is "same prompt"
  defined when the tool lists differ? The API description is the one thing
  that must differ: OpenAPI gives every operation as a tool in every state,
  HEART gives only the forms offered now. Everything else must match: the
  model and its settings, the user's request, the starting state, the
  system instructions outside the API part, and the turn limit. Suggested
  approach: one shared system prompt with no domain tips, only the tool or
  form section swapped. Then a second round where the OpenAPI agent also
  gets the tips a good team would write ("check the bed's status before
  acting"), to see whether HEART still does better against OpenAPI at its
  best.
- Do the other domains (tickets, basketball) get twins too, to check the
  result holds beyond the garden?

## When it is built

After the review and after the eval runner (Part C) exists, so both worlds
are scored by the same runner. Feature first, as for any new server work.
