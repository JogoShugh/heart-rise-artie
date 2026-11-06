# Idea: self-repair, kept: a repair that worked is written into the kept script

**Status:** future idea only, 2026-11-03. No code. From the author, on learning that ARTIE's repairs in the code mode
run were made in memory and lost (report: docs/reports/wayfinding-through-change/, "Self-repair, done but not
kept"). It is step 3 of spec/PLAN-RELEASE-TRAIN.md, and H1 of spec/ideas/fewer-tokens-for-kept-scripts.md.

## What happens today

A kept script held as data, with `on-change: intent` in its config, meets a form that changed since it was pinned:

1. The guard refuses the act before any request: the form's integrity isn't the one pinned.
2. The step falls back to its words, and the model chooses from the forms offered now.
3. The act is done, and the run goes on.
4. The run ends. The kept script, its pins and its config are unchanged on disk.

So every later run repairs the same step again. In the code mode run, runs 7 to 10 each paid for it: 3,093 tokens in
t1 and 1,288 in t3, every time, 17,524 in all.

## The idea

After a repaired act is done, write the repair down:

- the chosen relation and fields into the step of the kept `.heart.yaml`, its words kept for the next repair;
- the form's new integrity, method and set into the pins.

Only the first run after a change pays. In that run, 17,524 tokens would have been 4,381. The repair is the model at
the edge; keeping it puts determinism back in the middle.

## What a run can tell is safe

- The repair was chosen among forms the server offered, in the state it offered them.
- The server took it, with `If-Match`: the act was done on the state it was chosen from.
- What is written is data in the closed vocabulary (spec/schemas/heart-script.draft.schema.json), not code, and the
  kept script is checked against the schema before it is written.

## What it must take care with

- **A value made from state is written as a reference, never as the literal.** t3's repair sent "ring bearer,
  retired"; written as that literal, the next run would set Frodo's role to it whatever his role was then. It must be
  `${state.role}, retired`. ARTIE's trace records where each value came from (`from: parts: ring bearer: step 3,
  pointer /role`), so the writer can make the reference, as `artie generate` does from a goal run.
- **A value the model chose itself,** with no source in the state or the inputs, is written marked `review:`, as
  `artie generate` marks one today.
- **A run repaired midway** must not, on a rerun, do its earlier acts again
  (spec/ideas/self-repair-without-repeating-acts.md).

## What a run can't tell

That the meaning stayed when the name changed. A field renamed from `role` to `position` might also have come to
mean something else. Two guards:

- **Checks in the kept script:** `artie generate` writes the first run's end state as `check` steps (Samwise's role
  is "gardener, mayor"), so a repair that did the wrong thing fails loudly and isn't kept.
- **A person, where compliance asks for one:** a config setting like `repin`'s, `keep: auto`, `propose` (write the
  repair beside the script as a diff, and keep nothing) or `never` (the default). With `propose`, the reviewable
  change for a release is one diff of the kept script and its pins.

## What exists

- `repin: auto | propose | never` writes new pins, but only for a form that changed while the script's own values
  still fit it (`on-change: values`). A repair by the model needs the step itself written again, which nothing does
  yet.
- The trace already holds each value's source, and the generator already turns sources into references.
