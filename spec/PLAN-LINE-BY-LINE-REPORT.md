# Plan: a script run, reported line by line

Status: proposed, 2026-10-18. Mockup: https://claude.ai/artifact/TQyupYTxo7NU28K89zkTvu (real data from one
recorded reasoned GitHub run, assembled by hand for the layout; not yet the general tool).

## What the report is

For any heart-script run, one page, in this order, with light mode the default and a dark mode by choice:

1. **The script**, whole, coloured, numbered. Every line that ran is marked with the steps it made; a step
   number jumps to that step.
2. **What each line did**, one row per step, in the order the run took them. The default view shows only:
   - the statement, as the script wrote it, and how it ended (200 Read, 201 Created, 200 Done, held);
   - the affordance chosen: form, lookup form or link; its relation; and the values passed, `name = value`;
     for a check, its facts and what was held;
   - one terse line of what it took: `3 HEART calls · 8 GitHub calls (1 write) · 4.5 s`.
3. **One level deeper**, on a click of that line or a toggle on top (details · HEART exchanges · upstream
   API · upstream pages · block ends and re-reads): the words the model was asked, the method and address
   (and the template it came from), If-Match, the fields not sent, what else was offered there, every HEART
   exchange, every upstream exchange behind it, and the upstream's own page for the resource.

Block ends and re-reads (`return to`, `until`'s polls) are hidden unless asked for.

## Deterministic, for any script

Nothing is matched by text or by time. The run records what the report needs as it happens:

| What | Recorded by | How |
|---|---|---|
| The script line of each step | heart-script | the call site's frame in the `.kts` (file and line), taken when the step starts; a block end names the line of the block it closes |
| The affordance chosen | heart-script / ARTIE | kind (form, lookup, link), relation, title, method, target as offered and as expanded, fields offered, values sent, If-Match |
| What was offered | ARTIE | the forms and followable links of the representation held when the choice was made |
| The HEART exchanges | ARTIE | each HTTP exchange of the step, credentials as `<token>`, in the run's YAML capture (`artie run --exchanges`) |
| The upstream exchanges | the gateway | `GATEWAY_RECORD=file`: each upstream exchange as a YAML document, credentials as `<token>`, with the `traceparent` of the HEART request it served, so each is tied to its step exactly |
| The upstream page | the representation | its `alternate` link of type `text/html`; `--views` takes a headless screenshot of it after the step |

The page is built by the one copy of the report code in `docs/reports/assets` (a script layout beside the
run report's), from the trace, the script and the gateway's record. One command:
`build-run-report.sh TRACE OUT "Title" "Lede" SCRIPT [UPSTREAM.jsonl]`.

## The capture is YAML

What a run records for a person to read is YAML, never JSON lines of escaped JSON (decided 2026-10-18): one
YAML document per step, holding the step (its line, statement, words, the affordance chosen and the values
sent, what was offered, the outcome, what it took) and its exchanges, each HEART request with the upstream
requests that served it under it, tied by `traceparent`. Bodies are written as YAML, not as strings;
credentials as `<token>`. Mockup: step 3 of the recorded reasoned run, `step-3.capture.yaml` (sent
2026-10-18).

## One capture, two uses: the report and the script generator

The same capture feeds the line-by-line report and, later, script synthesis by slot filling
(spec/PLAN-ARTIE-SCRIPTS.md step 5, spec/ideas/captured-scripts.md). The report needs what happened; a
generator also needs why each value was what it was. So the capture holds now, per step (decided
2026-10-18; mockup version 2, https://claude.ai/artifact/RZSim7b6qnZWBZmJSq5GtR):

| Field | What | For |
|---|---|---|
| `values.*.from` | where each value sent came from: `words`, `{input: name}`, `{step: n, pointer: /…}`, `constant`, `default`; a value built of several, its `parts`; `ambiguous` when more than one source fits, never a guess | slots become inputs; state is read again at run time, never hard-coded |
| `reason.template`, `reason.slots` | the reason line before Kotlin filled it in (from the source line), and each `$variable` with its value and source | the words a generated script asks with, and its parameters |
| `guard` | the rung; the affordance set's href and integrity; the form's relation, its integrity (published by the server) and its schema's href and integrity; the If-Match sent | lenient and strict replay (spec/ideas/replay-guards-lenient-strict.md) |
| `block` | whether the step opens a block, and the step whose block it is in | the report's block ends; the generator's nesting |
| `waited` | an `until`: what it waited for (an offered relation, a state), how many reads | a generated script waits as the run did |
| `picked` | how a link or item was picked: by relation, by title, by a test on state | the generator's follow or pick |
| `judgement` | true when a choice was not in the words (the hybrid's "close it the right way") | the generator keeps a reason there, or freezes it only once a person confirms |
| `model` | what the model was asked and answered, at least on judgement steps | why a judgement step chose what it did |
| `expect`, `outcome.keeps` | the status the step had, and which members later steps read from its answer, by pointer | the generated script's expect, and its bindings |
| `run.inputs` | the script's inputs, their values and where each was given (command line, file, default) | the generated script's own inputs |

Values that change from run to run (time stamps, generated names) are inputs or defaults, never constants.
A replay writes the same capture, so comparing two captures checks that it made the same requests (the
plan's done condition for trace capture), and the report can show that comparison.

## Work, test first

0. heart-core: each form in an affordance set carries its own `integrity`, published by the server
   (decided 2026-10-18; spec/ideas/replay-guards-lenient-strict.md). The gateway gets it from heart-core.
   Live captures are recorded at `--rung L2`, so guards hold the server's addresses, not client digests.

1. heart-script: `line` on every trace step, and the block a block end closes (feature: a script's steps
   name their lines, nested blocks included).
2. heart-script / ARTIE: `affordance` and `offered` on every act, follow and reason step, with
   `values.*.from`, `reason.template` and `slots`, `guard`, `block`, `waited`, `picked`, `judgement`,
   `model`, `expect` and `outcome.keeps` (features in heart-script with the scripted service, and in artie
   against the HEART test server).
3. ARTIE: `--exchanges` keeps each step's HTTP exchanges, credentials redacted (artie-cli feature).
4. ARTIE sends `traceparent` on every request; the gateway records upstream exchanges with it when
   `GATEWAY_RECORD` is set (heart-gateway feature with the fake GitHub).
5. Report: the script layout in `run-report.js` (or a sibling in assets), light by default, its toggles;
   `--views` screenshots. Checked in headless Chrome, wide and at 500 px.
6. Live: the reasoned, scripted and hybrid GitHub tasks and one garden task, each reported this way;
   the skill updated.

## Decided 2026-10-18

- Upstream pages: the report always shows the resource's `text/html` alternate link; screenshots only when
  asked, `artie run --views` (headless Chrome).
- The model's exchange: recorded for every reason step, what it was asked and what it answered, keys never
  written; the report shows it one level deep.
- A form's integrity leaves out its title and description: a retitled form behaves the same, so lenient
  replay survives retitles (spec/ideas/replay-guards-lenient-strict.md).
- Garden over CoAP has no upstream: the same page, without the upstream column.
