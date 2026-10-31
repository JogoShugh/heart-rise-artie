# Idea: generated domains to test ARTIE against

**Status:** idea only. Raised by the author on 2026-10-25, after the basketball, tickets and garden domains. No
code, no spec text.

## The thought

Every HEART domain so far has the same shape: resources with fields, acts with a guard over the state, an input
schema, and an event that changes the state; `hr:options` for choices from state and `hr:unavailable` from the guards.
Tickets is about 650 lines of `src/main`, most of it wiring. A generator could make such domains at random, serve
them, and run ARTIE against them.

## Why it would be worth it

The generator knows the whole state graph. For any goal it can compute whether the goal is reachable and the
shortest path, so every run is graded against a known answer:

- did ARTIE reach the goal, and in how many steps beyond the shortest;
- did it stop with the server's reason when the goal is unreachable;
- did it ever ask for an address the server did not hand over.

## Two tracks, kept apart

1. **Nonsense names** (`zorp`, `act-17`). Tests ARTIE's deterministic parts with no model: replay, `at()` refusals,
   `actOnce`, `doneWhere`, kept values, listeners. Cheap; thousands of seeded domains could run as property tests.
2. **Plausible names**, written by a model for a seeded graph (a library, a clinic, a warehouse). Tests the reasoner:
   from a person's words to the right path. Paid per run, so a small fixed benchmark, not every build.

Mixing them would hide which part failed.

## Evolution tests

Record a script against version N of a domain; change the domain (rename a relation, add a guard, drop an act, change
an input); replay. The expected outcome is known: stop with the server's reason, never guess. This measures the claim
HEART makes against clients generated from an OpenAPI description.

## Hard parts

- Realism: random graphs come out too regular or too chaotic. Real domains serve as templates (short lifecycles,
  guards over two fields, lists with choices).
- Goals in words: turning a target state into a sentence a person would say needs a model or templates.
- Grading partial success: a goal reached by a strange path, or the right refusal.

## A first slice, if it is ever taken up

1. A domain described as data: resources, fields, acts, guards, events.
2. One generic host that serves such a description as HEART.
3. Tickets expressed in it, with `heart-tck` green, to prove it covers a real domain.
4. Only then, randomness.
