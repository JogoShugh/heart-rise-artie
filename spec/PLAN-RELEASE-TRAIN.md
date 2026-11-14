# Plan: the release train, one business process across a service's releases

Status: proposed, 2026-11-03. Plan only; nothing built. Follows the code mode run (spec/PLAN-CODE-MODE-VS-HEART.md;
report: docs/reports/wayfinding-through-change/), which had one release. Shares its theme with
spec/PLAN-WAYFINDING-TO-RAILS.md.

## The question

As a service evolves through release after release, which architecture keeps a repeated business process right,
safe and auditable, and where must the model sit for that?

- **A bag of endpoints or tools:** the client is told every operation up front and builds its own way through them.
- **Hypermedia:** the client follows the links and forms the server offers now, and the server holds the controls.
- **The model:** in the middle of every run, or at the edges only: to find the way at the start, and to repair
  after a change.

This is Fielding's evolvability claim tested on a small scale: "REST is software design on the scale of decades"
(comment of 20 October 2008; quote it verbatim from the source before publishing). Tokens, seconds and requests are
evidence for the question, not the headline.

## The clients

| Arm | Architecture | Each run | Built? |
|---|---|---|---|
| `code-fresh` | endpoints | the model writes programs from the current description, in a sandbox | yes |
| `code-saved` | endpoints | the programs from the first run, replayed; written again on a nonzero exit | yes |
| `code-saved-checked` | endpoints | the same, with programs that exit nonzero on any refusal: the steelman | small: how the check gets there is decided in step 2 |
| `mcp-static` | endpoints as tools | a model on tools built from the description at each run's start | yes (`fastmcp`) |
| `artie-data` | hypermedia | the kept `.heart.yaml`; a changed form falls back to its words, repaired in memory | yes |
| `artie-data-kept` | hypermedia | the same, and a repair that worked is written into the kept script and its pins | no: step 3 |

Later, not in the first train: `code-artie`, code mode running ARTIE in a container beside its sandbox
(spec/ideas/code-mode-runs-artie.md).

The same model, task words and service for all. The tasks are t1 (hire, promote, dismiss) and t3 (retire Frodo).
The teammate who changes Frodo right after the client's first read stays on for t3 throughout, because concurrent
change doesn't stop for releases.

## The train

Releases are cumulative, as real ones are: each keeps the ones before it. Each stays live for 3 runs per arm per
task before the next one ships.

| Release | What ships | The kind of evolution | In the sim today |
|---|---|---|---|
| R0 | nothing; the service as it is | baseline | yes |
| R1 | every act at a new address: `take-on`, `revise`, `let-go` (`moved`) | addresses | yes |
| R2 | the input `role` is called `position`; the state still says `role` (`renamed`) | names | yes |
| R3 | updating needs a `reason` (`required`) | a new required input | yes |
| R4 | updating is a PATCH, a JSON Merge Patch (`method`) | the way a write is made | yes |
| R5 | dismissing is offered only after notice is given, by a new act (`notice`) | an act moved behind a state | yes |
| R6 | employees are reached a new way: search moves under departments | the path to a resource | no |
| R7 | the same forms, a different outcome: a new hire starts on probation | meaning, with no change in any form | no |
| R8 | the owner adds a control: dismissing needs a second person's approval | compliance | no |

- **Phase 1 is R0 to R5:** all five changes exist in heart-sims' employees service (`Change` in
  HeartEmployees.kt), and the bench already sends several at once (`PUT /_sim/employees/changes`). 6 releases × 3 runs
  × 2 tasks × 6 arms = 216 runs.
- **Phase 2 is R6 to R8,** each needing a new change in the sim. R7 is the case no client's pins can see: only
  checks on the outcome catch it. R8 is the owner's control arriving in a release, as in exhibits/own-controls.
- **A well-behaved variant of R1:** the old addresses answer 308 to the new ones for one release, then 404, as real
  services deprecate. It shows whether endpoint clients survive on redirects alone.

## What is measured, by question

| Question | Measures |
|---|---|
| Is it right? | the bench's check per task; partial work left behind (an act done, the next refused) |
| Who notices a release? | before any write (pins, the forms offered), by the server's refusal, or not at all (silent) |
| Who repairs it, and how often? | the model, deterministic code, or a person; runs until right again after each release; repairs repeated |
| Wayfinding | addresses the client built against addresses the server handed over; values frozen into saved artifacts |
| Controls | writes sent with a validator; refusals and their reasons; acts tried that weren't offered |
| Security | runs that executed model-written code; runs that needed a sandbox |
| Compliance | what a reviewer approves per release: new programs, or the diff of a kept script; silent failures; whether each run's trace says why |
| Where the model sits | runs that needed the model, split into wayfinding, repair, and every run |
| Cost, as evidence | tokens, seconds, HTTP requests |

The compliance record is new here: for `artie-data-kept`, each release leaves a diff of the kept `.heart.yaml` and its
pins, the change a reviewer would approve. For the code arms, it is the programs written that release.

## Steps

0. **The author decides** the arms, N, phase 2's timing, and the open questions below. Nothing is built before.
1. **The bench runs a train** (exhibits/mcp-over-openapi/bench.py): `--train R0,R1,…` with N runs per release,
   cumulative changes, the teammate on t3, and one line per run with its release. The existing sequence mode is the
   model for this.
2. **`code-saved-checked`.** Either the system prompt tells the model to make programs exit nonzero on any status of
   400 or more, or one review adds the check before the train. The fairer choice is the author's.
3. **`artie-data-kept`: self-repair and keep** (artie-cli and heart-script, feature first, as always):
   - after a fallback's act is done, write the chosen step into the kept script, and its form into the pins;
   - a value made from state is written as a reference (`${state.role}, retired`), from the trace's record of where
     each value came from, never as the literal;
   - `artie generate` writes checks from the first run's end state, so a repair that went wrong fails loudly;
   - a config setting like `repin`'s: `auto`, `propose` (say it, keep nothing), or `never`.
   It meets spec/ideas/self-repair-without-repeating-acts.md: a run repaired midway must not do its earlier acts again.
4. **Phase 2 releases in heart-sims** (R6 to R8), feature first, rendered by heart-core in both dialects.
5. **The report:** "Wayfinding Through Change" grows into the train. Its centre is a table of releases against arms,
   each cell saying noticed or silent, repaired by whom, and right; and per release, the kept script's diff beside
   the programs code mode wrote.

## Fairness

- Code mode's library and documentation are made from the current description on every run, as in the first run.
- `mcp-static` builds its tools at each run's start: fresh, not stale. The stale case is already reported ("Static
  Tools, Moving Service").
- Before the train, every saved artifact may have one review: the saved programs and the kept scripts alike. None
  during the train, except what each arm's own mechanism does.
- Every change is one heart-core renders everywhere: the forms, the profile, the OpenAPI twin. Neither side is told
  about a release that the other side isn't.

## Open questions for the author

- Six arms and 3 runs per release, or fewer?
- Phase 2 now, or after phase 1's report?
- `artie-data-kept` during the train: `auto`, or `propose`, with a person approving each diff (closer to compliance,
  slower)?
- A release where the server says what it renamed (spec/ideas/fewer-tokens-for-kept-scripts.md, H2) needs a change
  to spec/SPEC.md. Include it as an R2 variant?
- Does `code-artie` join phase 2?
