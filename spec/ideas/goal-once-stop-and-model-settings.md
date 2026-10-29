# Idea: a goal's Once that stops, and the model's settings as a measured choice

**Status:** idea only. Raised by the author on 2026-10-29, after the merge watch bench (140 runs). No code, no spec
text.

## What the bench showed

On the behind scene, the plain-text goal (`exhibits/merge-watch/merge.goal.md`) stopped early in 2 of 10 runs, at
the line "Once it can be merged, merge it by squashing." Nothing was merged in either run. In the draft scene it
stopped in none of 10. The kept scripts never stopped (80 of 80).

At a Once under a Whenever, the runner already asks the planner for a wait and nothing else: the moves it allows are
`wait` and `stop` (`client/heart-script/src/main/kotlin/heart/script/StepWatch.kt`, `WAIT`). A wrong kind of move is
refused and asked again. A `stop` is allowed, and ends the run for good. In both failures the planner chose `stop`,
misreading the step:

- goal-7: "The condition is whether the held pull request can be merged; its state shows mergeable, so I'm checking
  that."
- goal-9: "The labelled pull request 1122 is held, which is where the sub-steps start, so this step's work is
  finished."

So a `Wait:` prefix on the line would change nothing: the Once is already a wait. The exit is the problem.

## Idea 1: structure first, a stop at a Once is questioned once

At a Once, refuse the planner's first `stop`, telling it: "this step waits; name the form its act needs, even when it
isn't offered now." Ask once more. A second `stop` ends the run as now. No model change, no prompt tuning beyond the
refusal the runner already gives a wrong move.

Measure: 10 goal runs on the behind scene, before and after.

## Idea 2: the model's settings, chosen and measured

Every run so far used the same settings, by default rather than by choice:

- model `claude-sonnet-5-5` (`ClaudeReasoner.DEFAULT_MODEL`, an alias, not a dated snapshot); `artie run` has no
  option to change it;
- no extended thinking, no effort setting;
- no temperature, so the API's default;
- `max_tokens` 1024.

Add `--model`, `--effort` and `--temperature` to `artie run` (and to the run's record, beside the usage it keeps).
Then run the goal 10 times per setting on the behind scene:

| Setting | Question |
|---|---|
| temperature 0 | Does a less varied choice stop the misreading? |
| thinking, or higher effort | Does more care at each move fix it, and at what token cost? |
| a stronger model (Opus 5.5) | Same, at a higher price per call |
| a dated snapshot | Does pinning the model make runs repeatable across weeks? |

Compare each with Idea 1. The write-up's point either way: what tuning the model buys, against what structure buys.

## Why it matters for "Reason on Rails"

The plain-text form is the least reliable of the forms measured. Whether the fix is a stronger model or a stronger
structure is exactly the question the write-up asks; this measures it rather than arguing it.
