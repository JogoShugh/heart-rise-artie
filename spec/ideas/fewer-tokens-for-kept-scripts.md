# Idea: fewer tokens for a kept script as data, still right every run

**Status:** future idea only, 2026-11-03. No plan, no code. From the author, after the fresh run of
spec/PLAN-CODE-MODE-VS-HEART.md (report: docs/reports/code-mode-vs-artie/).

## Where ARTIE's tokens went

In that run, ARTIE as data was right 20 of 20; saved code mode was right 12 of 20.

| Where | Input tokens | When |
|---|---|---|
| The goal runs that wrote the kept scripts | 42,483 (t1 25,390, t3 17,093) | once, before the 20 runs |
| The fallback after the rename | 17,524 (t1 3,093 a run, t3 1,288 a run, four runs each) | runs 7 to 10 |

The fallback's prompt is already small: the state, the forms offered, and the step's words. Trimming it gains little.
The gains are in calling the model less often, and in authoring the script more cheaply.

## Hypotheses

| # | Hypothesis | Tokens (estimated) | Why it stays right | Cost or risk |
|---|---|---|---|---|
| H1 | **Keep the repair.** After a fallback succeeds, ARTIE writes a new kept script and new pins from the model's choice, so only the first run after a change pays | 17,524 to 4,381 | the new script is held to the new pins, as the old one was | a write-back in `artie run`, close to `heart_do` writing YAML (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, step 7); auto-kept, or reviewed first? |
| H2 | **The server says what it renamed.** The form's schema says `position` was `role`, and ARTIE carries the value over itself | renamed runs to 0 | the server that made the change states it | a change to spec/SPEC.md, the author's call; the strongest form of "controls built into APIs we own" |
| H3 | **Repair a plain rename without the model.** One field gone, one added, with the same type and limits: carry the value over; anything else goes to the model | renamed runs to 0 | only together with H4 | a rename that also changed meaning would be carried over wrongly; weaker than H2 |
| H4 | **Checks in the kept script.** `artie generate` writes facts from the goal run's end state as `check` steps (Samwise's role is "gardener, mayor"), so a wrong repair fails loudly and falls back | none by itself | it is the guard that makes H1 and H3 safe | small, in the generator |
| H5 | **Author from a cheaper run.** Generate the kept script from an intent run instead of a goal run | 42,483 to about 12k (unmeasured) | the same kept script and pins | the about-12k is a guess from earlier intent runs (5.6k to 7.3k per task) |
| H6 | **A smaller model for the fallback** | the price, not the count | the choice is narrow: one form, the step's words | weaker choices on harder changes |

## Said plainly

- Saved code mode with one status check (`assert r['status'] < 300`) would likely be right 20 of 20 too. Run 7 would
  exit nonzero, the model would write the programs again, and runs 8 to 10 would replay them: about 32k tokens in
  all, against ARTIE's 60k with its goal runs counted. So against a well-written saved program, ARTIE doesn't win on
  tokens until H5 cuts the authoring cost.
- What ARTIE has without anyone remembering that check: its pins stopped the changed form before any write, it
  kept the server's `If-Match`, and it ran no code a model wrote, with no sandbox. Safety is the first claim; tokens
  the second.

## To measure, when taken up

- H1, H4 and H5 together, ARTIE's own work with no SPEC change: about 12k to author and 4.4k at run 7, about 16k in
  all, against about 32k (saved code mode with a check) and 159k (fresh code mode). All estimates until run.
- A `code-saved-checked` arm in the bench, the saved programs with a status check, so the comparison stays fair.
- H2 only on the author's word.
