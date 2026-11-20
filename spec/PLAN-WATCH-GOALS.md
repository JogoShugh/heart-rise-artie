# Plan: running a goal that keeps watching, decided once and replayed

Status: chosen 2026-10-23 ("Decide once, replay"). Slice 4a is in progress.

## The problem

A goal file can now say Whenever, Once, Or give up, Otherwise and Until (`goal-watch.feature`), and heart-script
can watch (`Watching.kt`). An `artie run` of such a goal still refuses it, because today's goal run asks the planner
and the reasoner for every move. A watch meets the same steps again for every pull request, every look, for hours.
The rule from the review is "no model call per event". The goal is reasoned once, and what was reasoned handles
every later event.

## The design: decide each step once, the first time it is needed, then replay it

A watch goal runs as today until it reaches its Whenever. From there, each step of the watch is decided by the
planner (and the reasoner, for an act) the first time the run needs it, live, on a real resource. The decision is
kept. Every later time, for every other resource and every later look, the decision is replayed with no model.

| Step | Decided once, live | Replayed, with no model |
|---|---|---|
| Whenever (the list) | The planner's act that reaches the list; the reasoner's form and values | The same form and values, at the same scope, each look |
| Whenever (the match) | At the first member: a check, the facts a member holds when it matches | The facts, tested on each new or changed member |
| Once (the wait) | The planner's wait: the act whose offer ends it | `offers(relation)` on the member, each look; `once` in heart-script |
| Once, and plain steps (the act) | The planner's act; the reasoner's form and values | The same form; each value bound to where it came from, read again from this member |
| Otherwise | As for an act, the first time a wait gives up | As for an act; the server's reason is a value it can bind |
| Until | At the named resource: a check, the facts that end the watch | The facts, tested on that resource after each look |

**Binding values.** A value the reasoner chose is matched against where it could have come from: an input, a
scalar of the member the step began on, a scalar of a resource a step named, or the server's reason for a give-up.
A whole match becomes that source. A value containing sources becomes a template with them in it, for example
`Bump left-pad … (#{/number})`. Anything else stays a literal. On replay, each source is read from what is there
now.

**Match facts on embedded resources.** "Labelled dependabot" is about resources a pull request embeds, not its
state. In a match, a fact may name an embedded relation (`github:labels`). Its value is then the names of what is
embedded there (each resource's `name`, else its title), and a list as the fact's value holds when every item is
among them.

**Then generate.** The kept decisions are what `artie generate` needs to write the run as a `watch` /
`whenever` / `once` script (spec/PLAN-SCRIPT-GENERATOR.md). That is slice 4c.

## Slices

1. **4a, the run.** `artie run X.goal.md` runs a watch goal: Whenever at any depth, its sub-steps plain or Once
   steps, with Or give up and Otherwise, and the Until. Decide once, replay, as above, all in one process. Tested
   with a scripted planner and reasoner against the in-process gateway and the small GitHub. The test counts the
   model calls: one per step, however many pull requests and looks.
2. **4b, the rest of the goal.** Done when becomes the run's final checks. The manners in words go to the
   planner. Steps after a Whenever in its block.
3. **4c, generate.** The run's decisions written as a watch script with no model in it.
4. **4d, live.** `dependabot-sim.goal.md` against the scratch repository, beside `watch-dsl.kts`.

## Left open

- **If.** "If it's labelled hold, leave it" is a plain step today. Whether it becomes a word, or part of the match,
  is still to decide (exhibits/goal-syntax/syntax.yaml).
- **A decision made on an odd first resource.** A decision is only as general as the resource it was made on. A
  replay that is refused (the form isn't offered, a value doesn't fit) is noted, and the watch goes on. Asking the
  planner again on a refusal is a later choice.
