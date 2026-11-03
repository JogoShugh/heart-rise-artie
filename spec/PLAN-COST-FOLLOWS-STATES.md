# Plan: one goal over many resources in a few states, the cost following the states

Status: proposed, 2026-11-03. Plan only; nothing built. Grows spec/ideas/branching-from-intent-runs.md (options 2 and
"grows by use") on the keep built the same day (spec/ideas/self-repair-and-keep.md). Read in the frame of
docs/reports/wayfinding-through-change/.

## Progress (2026-11-03)

- **Built:** `when-offered` in the data runner and the draft schema (Kotlin already had `offers(rel)`), and its
  fallback to the step's words for a member offering an act no case names (heart-script, when-offered.feature).
- **Run:** the bench's t5, seeded through the service's own acts instead of a heart-sims route; 6, 30 and 90 Tooks,
  5 runs each, every run right for all four clients. ARTIE's kept script, written by hand as generate would write it:
  no model at any size, 3 form sets a run. With one case alone: one model call per record in the other state. Code
  mode: about 10k (endpoints) and 24k to 36k (HAL-FORMS alone) tokens a run at every size. In the report
  (docs/reports/wayfinding-through-change/, "Many records, a few states").
- **Built since (step 3):** `artie generate --format yaml` writes the members of a list a goal run visited one by one
  as a `whenever` with a `when-offered` case per act (generate-yaml.feature). From a real goal run in plain words
  (`artie/t5.goal.md`, 48,491 tokens once), the generated script was right in 15 runs of 15 at 6, 30 and 90
  employees, with no model.
- **Not built yet:** keep adding a case (step 4), and the same loop in the Kotlin generator.

## The question

Take a hypermedia API whose shape is stable for resources of the same type in the same state. One goal runs over
many of them, in several states. Does a client's cost grow with the resources, or with the states?

- **Hypothesis, for ARTIE:** it reasons once per state it hasn't met, keeps a script that branches on what the server
  offers, and runs it after that with no model. The form sets it fetches are one per state, since resources in the
  same state share one. Model calls and form sets follow the states, not the resources.
- **For code mode:** it writes a loop too, so its model cost needn't grow with the resources either. It pays for the
  wayfinding in every run, and for a sandbox.

The middle ground this speaks to: services that won't change shape often, where repeatability, reliability and cost
matter a great deal.

## The service and the goal

heart-sims' employees service with its `notice` release: dismissing is offered only after notice is given. What each
state offers:

| State | Offered |
|---|---|
| working | give notice, update |
| on notice | dismiss, update |
| dismissed | nothing |

One family, the Tooks, seeded N working, M on notice and K dismissed. The goal: "Move every Took one step through
leaving: give notice to anyone working, dismiss anyone on notice, and leave the dismissed as they are."

The condition for each branch is the offer. The server decides the state; the script only reads what it offers.

## What gets built (feature first, as always)

1. **Seeding in heart-sims:** a `/_sim/employees/seed` route that makes the family in the given states, outside the
   measured requests.
2. **A step that branches on what is offered now.** The vocabulary has `once: offered` (waits for an act) and `given`
   (facts), but not "take the first case whose act is offered here". Proposed, for the author to name:

   ```yaml
   - whenever:
       - when-offered:
           - act: staff:give-notice
             intent: Give notice to anyone working
           - act: staff:dismiss
             intent: Dismiss anyone on notice
         otherwise:
           - note: nothing to do for ${state.firstName}
   ```

   In the schema, in the runner (heart-script's DataSteps), and in the Kotlin DSL, so both forms behave alike.
3. **Generate writes it.** From a goal run whose `whenever` met members with different offers, `artie generate`
   writes one case per act chosen, in the order the reasoner chose them, and `otherwise` for members where nothing was
   chosen. A value the reasoner judged per member stays words, or is marked for review.
4. **Grows by use, kept.** A member offering an act no case names falls to its words, with `on-change: intent`: the
   model chooses once. `keep: auto` adds the new case where every value is the script's own; `propose` shows it. A
   member offering nothing the goal could use takes `otherwise` with no model.

## The runs

| Run | Population (working, on notice, dismissed) | What it shows |
|---|---|---|
| Wayfinding | 2, 0, 0 | one goal run; the kept script with one case, give notice |
| Grow | 2, 2, 2 | those on notice met for the first time: dismiss is offered, no case matches, one model call, the dismiss case kept. The dismissed offer nothing: `otherwise` notes it, with no model |
| Scale | 10, 10, 10, then 30, 30, 30 | model calls and form sets per run, against the number of resources |
| Release | 30, 30, 30, with `renamed` | the repair kept once, for every member after it |

Arms: ARTIE (goal run, then the kept script), ARTIE's intent script (words per step: the model per member, as a
contrast), code mode on endpoints, and code mode on HEART with HAL-FORMS alone. 5 runs per row per arm.

## Measures

| Measure | The prediction for ARTIE's kept script |
|---|---|
| Model calls per run | 0 once each state has a case, whatever N |
| Form sets fetched per run | one per state met |
| Requests per member | flat as N grows |
| Right, per member | every member moved one step, the dismissed left |
| Writes over a change | 0 |
| Tokens, seconds, requests | as evidence, against the number of resources |

## Decisions for the author

- The new step's name and shape (`when-offered`, `choose`, `offered:` cases), and whether it lives in the release
  train too.
- The sizes, and 5 runs per row.
- Whether code mode on HEART runs with the 16 turns L2 needed, or the 8 the others had.

## Steps

0. The decisions above.
1. Seeding in heart-sims (heart-sims features).
2. The step, in heart-script's DSL and the data runner (heart-script features), and in the draft schema.
3. Generate writes it (artie-cli features).
4. Keep adds a case (artie and artie-cli features).
5. The bench's runs (exhibits/mcp-over-openapi or an exhibit of its own), then the report: cost against resources,
   one line per arm.
